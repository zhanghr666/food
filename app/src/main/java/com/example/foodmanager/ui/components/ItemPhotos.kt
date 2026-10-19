package com.example.foodmanager.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.foodmanager.core.CameraFiles
import com.example.foodmanager.core.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun LocalPhoto(store: PhotoStore, key: String, modifier: Modifier, thumb: Boolean = false) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, key, thumb) { value = withContext(Dispatchers.IO) { store.decode(key, if (thumb) 360 else 2048, thumb) } }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), "现场照片", modifier, contentScale = if (thumb) ContentScale.Crop else ContentScale.Fit)
    else Box(modifier.background(if (thumb) MaterialTheme.colorScheme.surfaceVariant else Color.DarkGray), contentAlignment = Alignment.Center) {
        Text("照片暂不可用", color = if (thumb) MaterialTheme.colorScheme.onSurfaceVariant else Color.White, style = MaterialTheme.typography.bodySmall)
    }
}

/** 固定高度横排，首张为封面。附件入口不触发照片识别。 */
@Composable
fun ItemPhotosEditor(keys: List<String>, store: PhotoStore, enabled: Boolean,
    onAdd: (List<android.net.Uri>) -> Unit, onRemove: (String) -> Unit,
    onCover: (String) -> Unit, onMove: (String, Int) -> Unit, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var viewer by remember { mutableStateOf<Int?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { if (it.isNotEmpty()) onAdd(it) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        cameraPath?.let { path -> if (success) onAdd(listOf(CameraFiles.uriFor(context, File(path)))) else File(path).delete() }
        cameraPath = null
    }
    fun takePhoto() { runCatching {
        val file = CameraFiles.createImageFile(context); cameraPath = file.path
        camera.launch(CameraFiles.uriFor(context, file))
    }.onFailure { onMessage("相机无法打开，可以从相册添加") } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) takePhoto() else onMessage("未开启相机权限，可以从相册添加") }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("现场照片${if (keys.isNotEmpty()) " · ${keys.size} 张" else "（可选）"}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) takePhoto()
                else permission.launch(Manifest.permission.CAMERA)
            }, enabled = enabled) { Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(18.dp)); Text(" 拍照") }
            TextButton(onClick = { gallery.launch("image/*") }, enabled = enabled) { Text("相册") }
        }
        if (keys.isEmpty()) Text("记录物品或摆放现场，照片不参与智能识别", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(122.dp)) {
            itemsIndexed(keys, key = { _, key -> key }) { index, key ->
                var menu by remember { mutableStateOf(false) }
                Column(Modifier.width(96.dp)) {
                    Surface(shape = RoundedCornerShape(8.dp), modifier = Modifier.size(96.dp).clickable { viewer = index }) { LocalPhoto(store, key, Modifier.fillMaxSize(), true) }
                    Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (index == 0) "封面" else "${index + 1}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        Box {
                            IconButton({ menu = true }, enabled = enabled, modifier = Modifier.size(26.dp)) { Icon(Icons.Outlined.MoreHoriz, "照片选项", Modifier.size(20.dp)) }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem({ Text("设为封面") }, { onCover(key); menu = false }, enabled = index != 0)
                                DropdownMenuItem({ Text("向前移动") }, { onMove(key, -1); menu = false }, enabled = index > 0)
                                DropdownMenuItem({ Text("向后移动") }, { onMove(key, 1); menu = false }, enabled = index < keys.lastIndex)
                                DropdownMenuItem({ Text("移除照片") }, { onRemove(key); menu = false })
                            }
                        }
                    }
                }
            }
        }
    }
    viewer?.let { start -> if (keys.isNotEmpty()) ItemPhotoViewer(keys, start, store) { viewer = null } }
}

/** 独立全屏查看页；常态横滑切图，放大后单指拖动，双指缩放。 */
@Composable
fun ItemPhotoViewer(keys: List<String>, start: Int, store: PhotoStore, onClose: () -> Unit) {
    val pager = rememberPagerState(initialPage = start.coerceIn(0, keys.lastIndex)) { keys.size }
    var zoomedPage by remember { mutableStateOf(-1) }
    LaunchedEffect(pager.currentPage) { zoomedPage = -1 }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = Color.White) }
                Text("${pager.currentPage + 1} / ${keys.size}", color = Color.White, modifier = Modifier.weight(1f))
                Text("双击或双指放大", color = Color.LightGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 16.dp))
            }
            HorizontalPager(pager, userScrollEnabled = zoomedPage != pager.currentPage, modifier = Modifier.weight(1f)) { index ->
                var scale by remember(keys[index]) { mutableFloatStateOf(1f) }
                var pan by remember(keys[index]) { mutableStateOf(Offset.Zero) }
                var size by remember { mutableStateOf(IntSize.Zero) }
                LaunchedEffect(pager.currentPage) { if (index != pager.currentPage) { scale = 1f; pan = Offset.Zero } }
                fun clamp(value: Offset) = Offset(value.x.coerceIn(-size.width * (scale - 1f) / 2, size.width * (scale - 1f) / 2), value.y.coerceIn(-size.height * (scale - 1f) / 2, size.height * (scale - 1f) / 2))
                Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged { size = it }
                    .pointerInput(keys[index]) { detectTapGestures(onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2.5f; pan = Offset.Zero; zoomedPage = if (scale > 1f) index else -1
                    }) }
                    .pointerInput(keys[index]) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } > 1 || scale > 1f) {
                                    scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                                    pan = clamp(pan + event.calculatePan())
                                    zoomedPage = if (scale > 1f) index else -1
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }, contentAlignment = Alignment.Center) {
                    LocalPhoto(store, keys[index], Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = pan.x; translationY = pan.y })
                }
            }
        }
    }
}
