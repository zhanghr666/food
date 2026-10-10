package com.example.foodmanager.voice

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** 语音识别状态 */
sealed interface VoiceState {
    data object Idle : VoiceState

    /** 正在准备离线模型（首次使用需要几秒） */
    data object Preparing : VoiceState

    /** 麦克风已打开，正在采集 */
    data object Listening : VoiceState

    /** 已停止采集，正在出最终结果 */
    data object Processing : VoiceState

    /** 边说边出的实时文字 */
    data class Partial(val text: String) : VoiceState

    data class Result(val text: String, val alternatives: List<String> = emptyList()) : VoiceState

    data class Error(val message: String) : VoiceState
}

/**
 * 离线语音识别（Vosk + 内置中文模型）。
 *
 * ## 为什么不用系统 SpeechRecognizer
 * 实测本机（MIUI）：系统语音服务被 ROM 锁掉麦克风权限（错误码 11），设备端离线识别不存在，
 * 唯一可行的"灌音频给云引擎"路径极不稳定（引擎按自己的静音判定提前 Abort，大量返回空结果）。
 * 因此改用完全本机的 Vosk：模型随 APK 打包，音频不出设备，识别不受 ROM 与网络影响。
 *
 * ## 交互语义：按住说话
 * - [start]：加载模型（仅首次）并打开麦克风，边听边出实时文字
 * - [finish]（松手）：停止采集，回调最终结果，**只在此时提交文字**
 */
class VoiceRecognizer(private val context: Context) {

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private var startJob: Job? = null
    private val lifecycle = Mutex()
    @Volatile private var processing = false
    private var transcript = VoiceTranscript()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val busy = AtomicBoolean(false)

    /** 用户是否还按着手指 */
    @Volatile
    var holdActive: Boolean = false
        private set

    /** 每次「按住」递增；界面据此判断是不是新的一轮（用于复位已收尾状态） */
    @Volatile
    var sessionId: Int = 0
        private set

    /** 最后一次识别到的文字（实时或最终） */
    @Volatile
    private var lastText: String = ""

    /** 上一次已经发给界面的实时文字，避免重复刷新 */
    @Volatile
    private var lastPartialEmitted: String = ""

    private val _state = MutableSharedFlow<VoiceState>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val state: SharedFlow<VoiceState> = _state.asSharedFlow()

    /** 本次按住期间识别到的全部文字 */
    fun fullText(): String = lastText.trim()

    /** 模型是否已经就绪（就绪后按住即可立即识别） */
    val isModelReady: Boolean get() = model != null

    /**
     * 预热模型：进入录入页时调用，避免第一次按住时等待。
     */
    fun warmUp() {
        scope.launch { lifecycle.withLock { ensureModel() } }
    }

    @Synchronized
    fun start() {
        if (holdActive || processing) return
        holdActive = true
        val generation = ++sessionId
        lastText = ""
        lastPartialEmitted = ""
        startJob = scope.launch {
            lifecycle.withLock {
                if (generation != sessionId || !holdActive) return@withLock
                try {
                    if (model == null) _state.tryEmit(VoiceState.Preparing)
                    val loaded = ensureModel()
                    if (generation != sessionId || !holdActive) return@withLock
                    if (loaded == null) {
                        holdActive = false
                        _state.tryEmit(VoiceState.Error("离线语音模型加载失败，请重装应用或改用文字录入"))
                        return@withLock
                    }
                    if (generation != sessionId || !holdActive) return@withLock
                    transcript = VoiceTranscript()
                    startListening(loaded)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Exception) {
                    holdActive = false
                    closeCapture()
                    _state.tryEmit(VoiceState.Error("启动语音识别失败，请重试"))
                    Log.e(TAG, "启动识别失败", t)
                }
            }
        }
    }

    private fun startListening(loaded: Model) {
        if (audioRecord != null) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            holdActive = false
            _state.tryEmit(VoiceState.Error("未获得录音权限，请在语音录入时允许使用麦克风"))
            return
        }
        val rec = Recognizer(loaded, SAMPLE_RATE)
        rec.setMaxAlternatives(3)
        // 使用内置模型的默认解码图，不把听写限制在食品、位置或用户常用词中。
        // 食品词典仅用于听写后的字段解析；多条转写仍交给统一候选核对。
        Log.i(TAG, "使用通用离线听写（无应用限制词表）")
        recognizer = rec

        // 自己开麦克风：大缓冲区避免丢样本
        // （Vosk 自带的 SpeechService 只用 0.2*sampleRate/2 的极小缓冲，实测会丢样本导致识别成乱码）
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE.toInt(),
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            _state.tryEmit(VoiceState.Error("设备不支持 16kHz 录音"))
            holdActive = false
            runCatching { rec.close() }
            recognizer = null
            return
        }
        val bufferSize = maxOf(minBuffer * 4, SAMPLE_RATE.toInt() * 2) // 至少 1 秒
        val record = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE.toInt(),
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
        }.getOrNull()
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            _state.tryEmit(VoiceState.Error("麦克风被占用或不可用，请稍后重试"))
            holdActive = false
            runCatching { record?.release() }
            runCatching { rec.close() }
            recognizer = null
            return
        }
        audioRecord = record
        Log.i(TAG, "打开麦克风（缓冲 ${bufferSize / 2} 采样≈${bufferSize / 2 / 16}ms），开始识别")
        try {
            record.startRecording()
        } catch (_: SecurityException) {
            holdActive = false
            record.release()
            audioRecord = null
            rec.close()
            recognizer = null
            _state.tryEmit(VoiceState.Error("录音权限已撤销，请重新授权后再试"))
            return
        }
        _state.tryEmit(VoiceState.Listening)

        val generation = sessionId
        val currentTranscript = transcript
        captureJob = scope.launch {
            val buffer = ShortArray(1600) // 每批最多 100ms，避免一秒阻塞读吞掉尾音。
            var tailBatches = 0
            try {
                while (isActive && generation == sessionId) {
                    val read = record.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
                    if (read < 0) break
                    if (read == 0) {
                        if (!holdActive) break
                        delay(10)
                        continue
                    }
                    val text = if (rec.acceptWaveForm(buffer, read)) {
                        val results = extractResults(rec.result)
                        currentTranscript.acceptCompleted(results.firstOrNull().orEmpty(), results.drop(1))
                    } else currentTranscript.acceptPartial(extractText(rec.partialResult, "partial"))
                    lastText = text
                    if (text.isNotBlank() && text != lastPartialEmitted && generation == sessionId) {
                        lastPartialEmitted = text
                        _state.tryEmit(VoiceState.Partial(text))
                    }
                    // 松手后送完已经缓存的音频；有界排空，避免一直录入后续环境声音。
                    if (!holdActive && ++tailBatches >= 10) break
                }
            } catch (t: Exception) {
                Log.e(TAG, "采集线程异常", t)
            }
        }
    }

    /** 收尾与启动/释放串行；先等待采集结束，再读取 final，最后关闭原生对象。 */
    @Synchronized
    fun finish() {
        if (!holdActive || processing) return
        holdActive = false
        processing = true
        val generation = sessionId
        _state.tryEmit(VoiceState.Processing)
        scope.launch {
            lifecycle.withLock {
                var outcome: VoiceState? = null
                try {
                    if (generation != sessionId) return@withLock
                    val rec = recognizer
                    captureJob?.join()
                    runCatching { audioRecord?.stop() }
                    val results = rec?.let { extractResults(it.finalResult) }.orEmpty()
                    val text = if (rec == null) "" else transcript.finish(results.firstOrNull().orEmpty(), results.drop(1))
                    if (generation != sessionId) return@withLock
                    lastText = text
                    outcome = if (text.isNotBlank()) VoiceState.Result(text, transcript.alternatives())
                    else VoiceState.Error("没有听清，请等到正在聆听后再说话")
                } catch (t: Exception) {
                    outcome = VoiceState.Error("语音处理失败，请重试")
                    Log.e(TAG, "识别收尾失败", t)
                } finally {
                    closeCapture()
                    if (generation == sessionId) processing = false
                }
                if (generation == sessionId) outcome?.let { _state.tryEmit(it) }
            }
        }
    }

    private suspend fun closeCapture() {
        captureJob?.join()
        captureJob = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
        runCatching { recognizer?.close() }
        recognizer = null
    }

    @Synchronized
    fun stop() = stopInternal(false)

    @Synchronized
    fun releaseModel() = stopInternal(true)

    private fun stopInternal(release: Boolean) {
        holdActive = false
        processing = true
        val generation = ++sessionId
        scope.launch {
            lifecycle.withLock {
                closeCapture()
                if (release) {
                    runCatching { model?.close() }
                    model = null
                }
                if (generation == sessionId) {
                    processing = false
                    _state.tryEmit(VoiceState.Idle)
                }
            }
        }
    }

    // ------------------------------------------------------------------

    internal fun extractResults(json: String?): List<String> = runCatching {
        val obj = JSONObject(json.orEmpty())
        val alternatives = obj.optJSONArray("alternatives")
        if (alternatives == null) listOf(obj.optString("text")) else
            (0 until alternatives.length()).map { alternatives.optJSONObject(it)?.optString("text").orEmpty() }
    }.getOrDefault(emptyList()).map(String::trim).filter(String::isNotBlank).distinct()

    private fun extractText(json: String?, key: String): String {
        if (json.isNullOrBlank()) return ""
        return runCatching { JSONObject(json).optString(key, "").trim() }.getOrDefault("")
    }

    /**
     * 首次使用时把 assets 里的模型解包到内部存储。
     *
     * 注意：不用 Vosk 的 StorageService.unpack()，因为它会去读模型根目录的 `uuid` 文件
     * 做版本校验，而官方中文模型并没有这个文件，会直接抛 FileNotFoundException。
     * 这里自己递归拷贝，并用一个标记文件判断是否已完成。
     *
     * **两处刻意设计**：
     * ① 解包到 `noBackupFilesDir` 而不是 `filesDir` —— 模型有 65MB，
     *    而 Android 自动备份的每应用配额只有 25MB，放在 `filesDir` 会让**整包备份失败**
     *    （用户换机后数据全丢，且不会有任何提示）。`noBackupFilesDir` 天然排除在备份之外，
     *    且它是可随时从 assets 重新生成的，正该如此。
     * ② 先解包到临时目录、成功后再整体改名 —— 避免中途被杀（切后台/低内存）
     *    留下**残缺模型**，下次启动照样加载失败。
     */
    private suspend fun ensureModel(): Model? {
        model?.let { return it }
        if (busy.getAndSet(true)) {
            repeat(120) {
                kotlinx.coroutines.delay(250)
                model?.let { return it }
            }
            return model
        }
        return try {
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            val dir = File(context.noBackupFilesDir, MODEL_DIR)
            val marker = File(dir, UNPACK_MARKER)
            if (!marker.exists()) {
                Log.i(TAG, "首次使用，从 assets 解包离线模型（约 65MB）…")
                withContext(Dispatchers.IO) {
                    val staging = File(context.noBackupFilesDir, "$MODEL_DIR.tmp")
                    staging.deleteRecursively()
                    staging.mkdirs()
                    copyAssetDir(MODEL_ASSET, staging)
                    File(staging, UNPACK_MARKER).writeText("ok")
                    // 原子替换：先移走旧目录，再把完整的新目录改名到位
                    dir.deleteRecursively()
                    if (!staging.renameTo(dir)) {
                        // 极少数情况下 rename 失败（跨挂载点），退化为拷贝
                        copyAssetDir(MODEL_ASSET, dir.apply { mkdirs() })
                        File(dir, UNPACK_MARKER).writeText("ok")
                        staging.deleteRecursively()
                    }
                }
                Log.i(TAG, "模型解包完成")
            }
            model = withContext(Dispatchers.IO) { Model(dir.absolutePath) }
            // 迁移清理：老版本把模型解包在 filesDir（会被自动备份统计，撑爆 25MB 配额）。
            // 新模型**已就绪**后才删旧目录，避免出现"两个都没有"的空窗。
            if (model != null) {
                withContext(Dispatchers.IO) {
                    val legacy = File(context.filesDir, MODEL_DIR)
                    if (legacy.exists() && legacy.deleteRecursively()) {
                        Log.i(TAG, "已清理旧位置的模型目录（移出备份范围）")
                    }
                }
            }
            Log.i(TAG, "离线模型就绪=${model != null}")
            model
        } catch (t: Throwable) {
            Log.e(TAG, "准备模型失败", t)
            null
        } finally {
            busy.set(false)
        }
    }

    /** 递归把 assets 目录复制到本地目录 */
    private fun copyAssetDir(assetPath: String, targetDir: File) {
        val assets = context.assets
        val children = assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            // 叶子：是文件
            targetDir.parentFile?.mkdirs()
            assets.open(assetPath).use { input ->
                targetDir.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            }
            return
        }
        targetDir.mkdirs()
        for (child in children) {
            copyAssetDir("$assetPath/$child", File(targetDir, child))
        }
    }

    private companion object {
        const val TAG = "VoiceRecognizer"
        const val MODEL_ASSET = "model-cn"
        const val MODEL_DIR = "vosk-model-cn"

        /** 解包完成标记：存在才认为目录里的模型是完整的 */
        private const val UNPACK_MARKER = ".unpack_complete"
        const val SAMPLE_RATE = 16000f
    }
}
