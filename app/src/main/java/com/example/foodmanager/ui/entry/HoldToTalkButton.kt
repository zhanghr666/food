package com.example.foodmanager.ui.entry

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.unit.dp

/** 手势在重组期间保持有效；手指离开按钮或页面退出时取消，不提交部分录音。 */
@Composable
fun HoldToTalkButton(recording: Boolean, preparing: Boolean, enabled: Boolean,
    onStart: () -> Boolean, onFinish: () -> Unit, onCancel: () -> Unit) {
    val latestRecording by rememberUpdatedState(recording)
    val latestEnabled by rememberUpdatedState(enabled)
    val start by rememberUpdatedState(onStart)
    val finish by rememberUpdatedState(onFinish)
    val cancel by rememberUpdatedState(onCancel)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium), label = "voice-press-scale")
    val primary = MaterialTheme.colorScheme.primary
    val buttonColor by animateColorAsState(
        if (!enabled) MaterialTheme.colorScheme.surfaceVariant else if (pressed) lerp(primary, Color.Black, 0.12f) else primary,
        animationSpec = tween(140), label = "voice-press-color")
    val status = if (preparing) "正在准备，请保持按住" else if (recording) "松开结束，移出取消" else "按住说话"
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
    // 手势区域固定大小；缩放只作用于圆形视觉，避免动画改变取消边界。
    Box(Modifier.size(112.dp).testTag("entry-hold-to-talk")
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "语音录入"
                stateDescription = status
                if (!enabled) disabled()
                // 无障碍服务无需模拟持续按压，可分别开始和结束。
                onClick(label = if (recording) "结束说话" else "开始说话") {
                    if (!latestEnabled) false else if (latestRecording) { finish(); true } else start()
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = press@{
                    if (!latestEnabled || latestRecording) return@press
                    pressed = true
                    var accepted = false
                    var released = false
                    try {
                        accepted = start()
                        if (accepted) released = tryAwaitRelease()
                    } finally {
                        pressed = false
                        if (accepted) { if (released) finish() else cancel() }
                    }
                })
            }, contentAlignment = Alignment.Center) {
        if (recording && !preparing) {
            val pulse = rememberInfiniteTransition(label = "voice-listening")
            val haloScale by pulse.animateFloat(0.94f, 1.08f,
                infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "voice-halo-scale")
            val haloAlpha by pulse.animateFloat(0.10f, 0.22f,
                infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "voice-halo-alpha")
            Box(Modifier.size(100.dp).graphicsLayer { scaleX = haloScale; scaleY = haloScale; alpha = haloAlpha }
                .background(primary, CircleShape))
        }
        Surface(color = buttonColor,
            contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = CircleShape, modifier = Modifier.size(88.dp).graphicsLayer { scaleX = scale; scaleY = scale }) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (preparing) CircularProgressIndicator(Modifier.size(34.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
                else Icon(Icons.Outlined.Mic, contentDescription = null, modifier = Modifier.size(34.dp))
            }
        }
    }
    Text(status, style = MaterialTheme.typography.labelLarge,
        color = if (recording) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
