package com.example.foodmanager.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.foodmanager.core.CameraFiles
import com.example.foodmanager.core.FoodDraft
import com.example.foodmanager.ocr.OcrEngine
import com.example.foodmanager.ocr.OcrTextParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 拍照 / 相册 OCR 识别卡片。编辑页与智能录入页共用。
 *
 * [onRecognized] 会带回识别原文与解析出的草稿，由调用方决定怎么用
 * （智能录入页放进待入库列表，编辑页直接填表）。
 *
 * @param singleItemMode true = 整张照片只当成一件商品（默认）。
 *   单件包装上的「脉动 / 减糖 / 蓝莓汁 1L」按行拆会被切碎成 3 条，所以默认不拆。
 */
@Composable
fun PhotoInputCard(
    title: String = "拍照识别",
    onRecognized: (rawText: String, drafts: List<FoodDraft>) -> Unit,
    modifier: Modifier = Modifier,
    singleItemMode: Boolean = true,
    /** 提供时显示「手动选保质期」按钮（编辑页用它直接填日期，不必再拍一张） */
    onPickDate: (() -> Unit)? = null,
    onCollapse: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ocrEngine = remember { OcrEngine() }

    // ML Kit 的 TextRecognizer 持有原生资源，离开界面时要显式关闭
    DisposableEffect(ocrEngine) {
        onDispose { runCatching { ocrEngine.close() } }
    }

    var processing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var pendingCameraPath by remember { mutableStateOf<String?>(null) }
    /** 下一次拍照是否为「日期区域」模式 */
    var pendingDateMode by remember { mutableStateOf(false) }

    fun handleResult(rawText: String) {
        val drafts = OcrTextParser.parse(rawText, "ocr", singleItemMode = singleItemMode)
            // 兜底：过滤掉"只有数量、没有名称"的空壳记录，
            // 否则一张照片可能凭空多出一条无意义的待入库项
            .filter { it.name.isNotBlank() }
        status = when {
            drafts.isEmpty() -> "没有从图片里识别到食物信息，可换一张或手动填写"
            else -> {
                val mode = if (singleItemMode) "单件模式" else "拆分模式"
                "识别到 ${drafts.size} 条（$mode）：" + drafts.joinToString("、") { it.name }
            }
        }
        onRecognized(rawText, drafts)
    }

    /**
     * 日期喷码专用识别：只对**取景框内的日期区域**做裁剪 → 放大 3 倍 → 对比度拉伸 → 识别。
     *
     * 为什么单独做一次：包装上的生产日期/保质期常是**点阵喷码**（字小、浅色、低对比度），
     * 整图识别时容易被漏掉；把该区域单独放大后，点阵字会连成可辨笔画。
     */
    suspend fun recognizeDateZone(path: String): String = withContext(Dispatchers.Default) {
        val full = OcrEngine.loadBitmapFromFile(path)
        try {
            // 对应取景框提示「把日期对在虚线框内」：画面中上部
            val region = OcrEngine.cropRegion(full, 0f, 0.10f, 1f, 0.45f)
            try {
                val enhanced = OcrEngine.upscaleAndStretch(region, factor = 3)
                try {
                    ocrEngine.recognizeBitmap(enhanced)
                } finally {
                    if (!enhanced.isRecycled) enhanced.recycle()
                }
            } finally {
                if (!region.isRecycled) region.recycle()
            }
        } finally {
            if (!full.isRecycled) full.recycle()
        }
    }

    /**
     * 识别一张图片并返回文字。
     *
     * 位图用完**必须回收**：一张 2600×1500 的 ARGB 位图约 15MB，
     * 连续拍几张不回收就会造成明显的内存压力。
     */
    suspend fun recognizeFile(path: String, dateMode: Boolean): String =
        withContext(Dispatchers.Default) {
            if (!dateMode) {
                val bitmap = OcrEngine.loadBitmapFromFile(path)
                try {
                    ocrEngine.recognizeBitmap(bitmap)
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            } else {
                // 日期模式：主识别 + 日期区域增强识别，两者取并集
                val main = run {
                    val bitmap = OcrEngine.loadBitmapFromFile(path)
                    try {
                        ocrEngine.recognizeBitmap(bitmap)
                    } finally {
                        if (!bitmap.isRecycled) bitmap.recycle()
                    }
                }
                val zone = recognizeDateZone(path)
                if (zone.isBlank()) main else OcrEngine.mergeText(main, zone)
            }
        }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val path = pendingCameraPath
        if (success && path != null) {
            val dateMode = pendingDateMode
            pendingDateMode = false
            processing = true
            status = if (dateMode) "正在识别日期区域…" else "正在识别图片文字…"
            scope.launch {
                runCatching { recognizeFile(path, dateMode) }
                    .onSuccess { text ->
                        processing = false
                        handleResult(text)
                    }
                    .onFailure {
                        processing = false
                        status = "识别失败：${it.message}"
                    }
                // 识别结束（无论成败）立即删掉拍照产生的临时大图，
                // 否则每次拍照都会在 cache 里留下一个约 4MB 的文件
                runCatching { File(path).delete() }
            }
        } else if (path != null) {
            // 用户取消拍照：同样清掉刚创建的临时文件
            runCatching { File(path).delete() }
            pendingDateMode = false
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = CameraFiles.createImageFile(context)
            pendingCameraPath = file.absolutePath
            runCatching { cameraLauncher.launch(CameraFiles.uriFor(context, file)) }
                .onFailure { status = "无法启动相机：${it.message}" }
        } else {
            status = "需要相机权限才能拍照识别"
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            processing = true
            status = "正在识别图片文字…"
            scope.launch {
                runCatching { ocrEngine.recognizeAndParse(context, uri) }
                    .onSuccess { result ->
                        processing = false
                        status = if (result.drafts.isEmpty()) {
                            "没有从图片里识别到食物信息，可换一张或手动填写"
                        } else {
                            "识别到 ${result.drafts.size} 条"
                        }
                        onRecognized(result.rawText, result.drafts)
                    }
                    .onFailure {
                        processing = false
                        status = "识别失败：${it.message}"
                    }
            }
        }
    }

    val startCamera = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            val file = CameraFiles.createImageFile(context)
            pendingCameraPath = file.absolutePath
            runCatching { cameraLauncher.launch(CameraFiles.uriFor(context, file)) }
                .onFailure { status = "无法启动相机：${it.message}" }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DocumentScanner, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (singleItemMode) {
                    "对准包装上的生产日期、保质期、净含量拍照，自动识别（完全离线，不上传图片）"
                } else {
                    "按「一行一件商品」拆分，适合拍购物小票批量录入（完全离线，不上传图片）"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { startCamera() }, enabled = !processing) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("拍照识别")
                }
                FilledTonalButton(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !processing,
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("从相册选")
                }
            }
            Spacer(Modifier.height(6.dp))
            // 日期喷码（点阵小字）整图识别常常读不到，单独拍一张、只放大识别日期区域
            FilledTonalButton(
                onClick = {
                    pendingDateMode = true
                    startCamera()
                },
                enabled = !processing,
            ) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("单独拍日期（点阵喷码用）")
            }
            if (onPickDate != null) {
                Spacer(Modifier.height(4.dp))
                FilledTonalButton(onClick = onPickDate, enabled = !processing) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("手动选保质期")
                }
            }
            Text(
                text = "点「单独拍日期」后，把包装上的日期喷码对准取景框**中上部虚线区域**再拍。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            if (processing) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(status, style = MaterialTheme.typography.bodyMedium)
                }
            } else if (status.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(status, style = MaterialTheme.typography.bodyMedium)
            }

            if (onCollapse != null) {
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.TextButton(
                    onClick = onCollapse,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("收起拍照录入")
                }
            }
        }
    }
}
