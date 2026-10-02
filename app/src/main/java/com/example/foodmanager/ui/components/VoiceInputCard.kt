package com.example.foodmanager.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.util.Log
import com.example.foodmanager.voice.VoiceRecognizer
import com.example.foodmanager.voice.VoiceState
import kotlinx.coroutines.delay

/**
 * 「按住说话」语音输入卡片。
 *
 * 交互与文案严格一致：**按下手指即开始录音，松开手指立即结束识别**。
 * - 按住时：麦克风按钮变红并脉动、显示实时识别文字、提示"松开结束"
 * - 松开后：自动把识别结果交给 [onResult]
 * - 支持长按超过 [maxHoldMillis] 自动结束，避免忘记松手
 */
@Composable
fun VoiceInputCard(
    title: String = "语音录入",
    hints: List<String> = emptyList(),
    onResult: (String) -> Unit,
    modifier: Modifier = Modifier,
    maxHoldMillis: Long = 20_000L,
    onCollapse: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val recognizer = remember { VoiceRecognizer(context) }

    var listening by remember { mutableStateOf(false) }
    var processing by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    var processStart by remember { mutableLongStateOf(0L) }
    var finished by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf("") }
    var lastSessionId by remember { mutableIntStateOf(-1) }
    var liveText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var permissionAsked by remember { mutableStateOf(false) }

    // 用普通变量记录实时文字，避免手势闭包读到旧的 state 值
    var latestPartial by remember { mutableStateOf("") }

    val currentOnResult by rememberUpdatedState(onResult)
    fun markFinished(resultText: String) {
        lastResult = resultText
        finished = true
        Log.i("VoiceCard", "识别收尾：result=「$resultText」")
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        status = if (granted) "麦克风已就绪，按住下面的按钮说话" else "没有麦克风权限，请到系统设置里允许本应用录音"
    }

    // 进入页面就先把麦克风权限要下来，避免第一次按住时被打断
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted && !permissionAsked) {
            permissionAsked = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        // 进页面就预热离线模型，第一次按住不用等
        recognizer.warmUp()
        recognizer.state.collect { state ->
            Log.i("VoiceCard", "收到状态：$state（已收尾=$finished）")
            when (state) {
                VoiceState.Preparing -> {
                    listening = true
                    preparing = true
                    status = "首次使用正在准备离线语音模型，请稍候…"
                }

                is VoiceState.Listening -> {
                    // 新的一轮「按住」开始：复位收尾标记，否则后续结果会被当成迟到状态丢掉
                    if (lastSessionId != recognizer.sessionId) {
                        lastSessionId = recognizer.sessionId
                        finished = false
                        processing = false
                        liveText = ""
                        lastResult = ""
                    }
                    listening = true
                    preparing = false
                    status = "正在聆听… 说完松开手指"
                }

                is VoiceState.Partial -> {
                    preparing = false
                    liveText = state.text
                    status = "已识别到「${state.text}」，继续说话或松手完成"
                }

                VoiceState.Processing -> {
                    listening = false
                    processing = true
                    status = "正在出最终结果，请稍候…"
                }

                is VoiceState.Result -> {
                    // 只有松手那一刻才提交一次
                    listening = false
                    preparing = false
                    processing = false
                    liveText = state.text
                    if (!finished) {
                        status = "识别完成，已自动填入"
                        markFinished(state.text)
                        currentOnResult(state.text)
                    }
                }

                is VoiceState.Error -> {
                    if (!finished) {
                        listening = false
                        preparing = false
                        processing = false
                        status = state.message
                    }
                }

                VoiceState.Idle -> {
                    listening = false
                    preparing = false
                    processing = false
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { recognizer.releaseModel() }
    }

    val infinite = rememberInfiniteTransition(label = "voice")
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "pulse",
    )
    val ringAlpha by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Restart),
        label = "ring",
    )

    val grantedNow = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (listening) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ---------- 标题 ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (listening) Icons.Filled.Mic else Icons.Filled.MicNone,
                    contentDescription = null,
                    tint = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (listening) "正在录音，请说话…" else "按住说话",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (listening) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                )
            }

            Spacer(Modifier.height(10.dp))

            // ---------- 操作说明（明显提示） ----------
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                shape = RoundedCornerShape(10.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    StepText("1", "按住下面的大按钮，手指不要松开")
                    StepText("2", "说完再松开手指，自动结束并识别")
                    StepText("3", "识别结果会自动填进表单，可再手动修改")
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------- 按住说话按钮 ----------
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                // 录音时的扩散圈
                if (listening) {
                    Box(
                        modifier = Modifier
                            .size(104.dp)
                            .alpha(ringAlpha)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error),
                    )
                }
                Surface(
                    color = when {
                        listening -> MaterialTheme.colorScheme.error
                        !grantedNow -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.primary
                    },
                    shape = CircleShape,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .size(88.dp)
                        .scale(if (listening) pulse else 1f)
                        .pointerInput(grantedNow) {
                            // 自己处理按下/抬起：手指轻微移动、滑出按钮都不算松手
                            // （系统的 detectTapGestures 一检测到滑动就取消手势，导致"挪一下就停"）
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                if (!grantedNow) {
                                    status = "没有麦克风权限，请到系统设置里允许本应用录音"
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    return@awaitEachGesture
                                }

                                if (processing) return@awaitEachGesture
                                finished = false
                                // 开始录音（首次会先准备离线模型）
                                status = "正在启动…"
                                liveText = ""
                                Log.i("VoiceCard", "手指按下 -> 开始录音")
                                recognizer.start()

                                // 等这根手指抬起为止
                                var stillDown = true
                                while (stillDown) {
                                    val event = awaitPointerEvent()
                                    stillDown = event.changes.any { c -> c.id == down.id && c.pressed }
                                }

                                Log.i("VoiceCard", "手指抬起 -> 提交本轮识别文字")
                                listening = false
                                processStart = System.currentTimeMillis()
                                processing = true
                                status = "正在识别，请稍候…"
                                recognizer.finish()
                            }
                        },
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "按住说话",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = when {
                                listening -> "松开"
                                processing -> "识别中"
                                else -> "按住"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // ---------- 超时保护：按住太久自动结束 ----------
            LaunchedEffect(listening) {
                if (listening) {
                    delay(maxHoldMillis)
                    if (listening) {
                        recognizer.finish()
                        listening = false
                        processing = true
                        processStart = System.currentTimeMillis()
                        status = "录音时间过长，已自动结束，正在识别…"
                    }
                }
            }

            // ---------- 超时保护：等不到最终结果再兜底（最多等 3 秒） ----------
            LaunchedEffect(processing) {
                if (processing) {
                    delay(3_000L)
                    if (processing && !finished) {
                        val fallback = liveText.trim().ifBlank { recognizer.fullText().trim() }
                        recognizer.stop()
                        processing = false
                        if (fallback.isNotBlank()) {
                            liveText = fallback
                            status = "已识别到「$fallback」"
                            markFinished(fallback)
                            currentOnResult(fallback)
                        } else {
                            status = "没听清，请靠近手机再说一遍"
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ---------- 实时识别文字 ----------
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "实时识别",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = liveText.ifBlank {
                            when {
                                preparing -> "…正在准备离线模型（仅首次需要几秒）"
                                listening -> "…正在听，请说话"
                                processing -> "…正在识别，请稍候"
                                else -> "还没有内容，按住上面的按钮开始"
                            }
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (liveText.isBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    if (processing) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            if (status.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (listening) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    fontWeight = if (listening) FontWeight.Bold else FontWeight.Normal,
                )
            }

            if (hints.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "可以这样说：",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                hints.forEach { hint ->
                    Text(
                        text = "· $hint",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            if (onCollapse != null) {
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = onCollapse,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("收起语音录入")
                }
            }
        }
    }
}

@Composable
private fun StepText(step: String, text: String) {
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text(
            text = step,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(14.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 小号「按住说话」按钮，用于表单内联（同样是按住录音、松开结束） */
@Composable
fun HoldToTalkButton(
    onResult: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val recognizer = remember { VoiceRecognizer(context) }
    var listening by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var lastText by remember { mutableStateOf("") }

    val currentOnResult by rememberUpdatedState(onResult)

    LaunchedEffect(Unit) {
        recognizer.state.collect { state ->
            when (state) {
                is VoiceState.Listening -> {
                    if (!listening) listening = true
                    error = ""
                }

                is VoiceState.Partial -> {
                    lastText = state.text
                }

                is VoiceState.Result -> {
                    // 只记录，松手时统一提交一次
                    lastText = state.text
                }

                is VoiceState.Error -> {
                    if (!recognizer.holdActive) {
                        listening = false
                        error = state.message
                    }
                }

                else -> Unit
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { recognizer.stop() }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            shape = CircleShape,
            modifier = Modifier
                .size(72.dp)
                .pointerInput(Unit) {
                    // 同样用自定义手势：轻微移动不打断录音
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            error = "没有麦克风权限，请到系统设置里允许录音"
                            return@awaitEachGesture
                        }
                        recognizer.start()
                        var stillDown = true
                        while (stillDown) {
                            val event = awaitPointerEvent()
                            stillDown = event.changes.any { c -> c.id == down.id && c.pressed }
                        }
                        val transcript = recognizer.fullText().trim().ifBlank { lastText.trim() }
                        recognizer.finish()
                        listening = false
                        if (transcript.isNotBlank()) currentOnResult(transcript)
                    }
                },
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "按住说话",
                tint = Color.White,
                modifier = Modifier.padding(20.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = when {
                listening -> "松开结束"
                error.isNotBlank() -> error
                else -> "按住说话"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            fontWeight = if (listening) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
