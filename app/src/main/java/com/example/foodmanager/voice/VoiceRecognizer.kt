package com.example.foodmanager.voice

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.example.foodmanager.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    /** 领域语法词表（JSON 数组，空表示用自由解码） */
    @Volatile
    private var grammarJson: String? = null

    /**
     * 生成 [grammarJson] 时所用的词表指纹。
     *
     * 用户在设置里增删位置/食品、或改了别名后，指纹变化 → 下次识别前**重建词表**，
     * 否则新加的常用食品要等重启 App 才生效。
     */
    @Volatile
    private var grammarSignature: String? = null

    @Volatile
    private var grammarWordCount: Int = 0

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
        if (model != null || busy.get()) return
        scope.launch { runCatching { ensureModel() } }
    }

    fun start() {
        if (holdActive) return
        holdActive = true
        sessionId++
        lastText = ""
        lastPartialEmitted = ""
        startJob = scope.launch {
            try {
                if (model == null) {
                    _state.tryEmit(VoiceState.Preparing)
                }
                val loaded = ensureModel()
                if (!holdActive) return@launch
                if (loaded == null) {
                    _state.tryEmit(VoiceState.Error("离线语音模型加载失败，请重装应用或改用文字录入"))
                    holdActive = false
                    return@launch
                }
                startListening(loaded)
            } catch (t: Throwable) {
                Log.e(TAG, "启动识别失败", t)
                holdActive = false
                _state.tryEmit(VoiceState.Error("启动语音识别失败：${t.message ?: t.javaClass.simpleName}"))
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
        // 注入领域语法：解码时只能从食物/单位/位置/时间词里选，
        // 从根上避免通用模型把「西瓜」猜成「吸管」这类同音字错误。
        if (grammarJson != null) {
            runCatching { rec.setGrammar(grammarJson) }
                .onSuccess { Log.i(TAG, "已启用领域语法约束（${grammarWordCount} 个词条）") }
                .onFailure { Log.w(TAG, "语法设置失败，改用自由解码：${it.message}") }
        }
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

        captureJob = scope.launch {
            val buffer = ShortArray(bufferSize / 2)
            var peak = 0
            var lastReport = 0L
            var frames = 0L
            try {
                while (isActive && holdActive) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read <= 0) break
                    frames += read
                    val target = recognizer ?: break
                    target.acceptWaveForm(buffer, read)

                    for (i in 0 until read) {
                        val abs = kotlin.math.abs(buffer[i].toInt())
                        if (abs > peak) peak = abs
                    }
                    // 边识别边出实时文字（先做同音字校正，界面直接显示干净文本）
                    val partial = VoicePhoneticCorrector.correct(extractText(target.partialResult, "partial"))
                    if (partial.isNotBlank() && partial != lastPartialEmitted) {
                        lastPartialEmitted = partial
                        lastText = partial
                        Log.i(TAG, "实时：$partial")
                        _state.tryEmit(VoiceState.Partial(partial))
                    }
                    val now = System.currentTimeMillis()
                    if (now - lastReport >= 2000L) {
                        lastReport = now
                        Log.i(TAG, "采集中：${frames * 1000 / 16000}ms 音频，峰值=$peak/32767")
                        peak = 0
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "采集线程异常", t)
            }
        }
    }

    /** 松手：停止采集，取最终结果（只提交一次） */
    fun finish() {
        holdActive = false
        val record = audioRecord
        audioRecord = null
        val rec = recognizer
        recognizer = null

        if (rec == null) {
            _state.tryEmit(VoiceState.Idle)
            return
        }
        _state.tryEmit(VoiceState.Processing)
        scope.launch {
            captureJob?.cancel()
            captureJob = null
            runCatching { record?.stop() }
            runCatching { record?.release() }
            // 等采集线程退出，确保最后的音频已经送进 recognizer
            delay(150)
            val text = VoicePhoneticCorrector.correct(
                runCatching { extractText(rec.finalResult, "text") }.getOrDefault(""),
            ).ifBlank { lastText.trim() }
            runCatching { rec.close() }
            if (text.isNotBlank()) {
                lastText = text
                Log.i(TAG, "松手提交：「$text」")
                _state.tryEmit(VoiceState.Result(text, listOf(text)))
            } else {
                _state.tryEmit(VoiceState.Error("没有听清，请靠近手机再说一遍"))
            }
        }
    }

    /** 彻底释放（离开页面） */
    fun stop() {
        holdActive = false
        startJob?.cancel()
        startJob = null
        captureJob?.cancel()
        captureJob = null
        val record = audioRecord
        audioRecord = null
        val rec = recognizer
        recognizer = null
        scope.launch {
            runCatching { record?.stop() }
            runCatching { record?.release() }
            runCatching { rec?.close() }
        }
    }

    /** 释放模型（App 退出时可调用） */
    fun releaseModel() {
        stop()
        runCatching { model?.close() }
        model = null
    }

    // ------------------------------------------------------------------

    /** 读取用户设置（自定义位置 / 常用食品），用于生成语法词表 */
    private fun readSettings(): com.example.foodmanager.data.AppSettings = runCatching {
        val database = AppDatabase.get(context)
        kotlinx.coroutines.runBlocking { database.settingsDao().get() }
    }.getOrNull() ?: com.example.foodmanager.data.AppSettings.DEFAULT

    /**
     * 读取参与语音词表的位置与食品（**含别名**）。
     *
     * 别名也要进词表，否则用户说「冷藏」时 Vosk 根本不会把它识别出来，
     * 后面的"别名→正式名"映射也就无从生效。
     */
    private fun readGrammarWords(): GrammarWords = runCatching {
        val database = AppDatabase.get(context)
        kotlinx.coroutines.runBlocking {
            val locations = database.storageLocationDao().getAll()
            val foods = database.commonFoodDao().getAll()
            GrammarWords(
                locations = locations.flatMap { listOf(it.name) + it.aliasList },
                foods = foods.flatMap { listOf(it.name) + it.aliasList },
            )
        }
    }.getOrElse { GrammarWords(emptyList(), emptyList()) }

    private data class GrammarWords(val locations: List<String>, val foods: List<String>)

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
            // 生成语法词表：把用户维护的常用位置/食品（含别名）纳入
            // 指纹变化时重建 —— 这样在设置里改了别名，下一次说话就生效，不必重启
            val wordSource = readGrammarWords()
            val signature = wordSource.locations.joinToString("|") +
                "#" + wordSource.foods.joinToString("|")
            if (grammarJson == null || grammarSignature != signature) {
                runCatching {
                    VoiceGrammar.build(wordSource.locations, wordSource.foods)
                }
                    .onSuccess {
                        grammarJson = it
                        grammarSignature = signature
                        grammarWordCount = it.count { c -> c == ',' } + 1
                        Log.i(TAG, "领域语法词表生成完成，共 $grammarWordCount 个词条")
                    }
                    .onFailure { Log.w(TAG, "语法词表生成失败，将使用自由解码", it) }
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
