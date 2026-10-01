package com.example.foodmanager.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 屏幕**居中**的提示浮层，替代默认的底部 Snackbar。
 *
 * ## 为什么不用默认的底部 Snackbar
 *
 * 底部 Snackbar 会被输入法遮挡，而各 Android 版本 / 厂商对输入法的避让行为不一致：
 * 有的收缩窗口、有的叠加内容，加 IME 内边距在部分设备上会留下空白，不加又被挡住。
 * 放在**屏幕垂直居中**位置后，任何设备、任何键盘状态下都不会被遮挡，
 * 也不需要任何 inset 计算，行为完全一致。
 *
 * 用法与 `SnackbarHost` 相同：`snackbarHost = { CenteredSnackbarHost(snackbar) }`。
 */
@Composable
fun CenteredSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val data = hostState.currentSnackbarData ?: return
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Snackbar(
            modifier = Modifier.padding(horizontal = 32.dp),
            snackbarData = data,
        )
    }
}

/** 保留给需要自定义内容的场景 */
@Suppress("unused")
private fun SnackbarVisuals.unused() = Unit
