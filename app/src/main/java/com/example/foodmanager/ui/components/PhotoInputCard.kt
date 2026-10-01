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
import androidx.compose.material.icons.filled.DocumentScanner
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

/**
 * 拍照 / 相册 OCR 识别卡片。编辑页与智能录入页共用。
 *
 * [onRecognized] 会带回识别原文与解析出的草稿，由调用方决定怎么用
 * （智能录入页放进待入库列表，编辑页直接填表）。
 */
@Composable
fun PhotoInputCard(
    title: String = "拍照批量识别",
    onRecognized: (rawText: String, drafts: List<FoodDraft>) -> Unit,
    modifier: Modifier = Modifier,
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

    fun handleResult(rawText: String) {
        val drafts = OcrTextParser.parse(rawText, "ocr")
        status = if (drafts.isEmpty()) {
            "没有从图片里识别到食物信息，可换一张或手动填写"
        } else {
            "识别到 ${drafts.size} 条"
        }
        onRecognized(rawText, drafts)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val path = pendingCameraPath
        if (success && path != null) {
            processing = true
            status = "正在识别图片文字…"
            scope.launch {
                runCatching {
                    withContext(Dispatchers.Default) {
                        val bitmap = OcrEngine.loadBitmapFromFile(path)
                        ocrEngine.recognizeBitmap(bitmap)
                    }
                }.onSuccess { text ->
                    processing = false
                    handleResult(text)
                }.onFailure {
                    processing = false
                    status = "识别失败：${it.message}"
                }
            }
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
                text = "对准包装上的生产日期、保质期、净含量拍照，自动识别（完全离线，不上传图片）",
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
