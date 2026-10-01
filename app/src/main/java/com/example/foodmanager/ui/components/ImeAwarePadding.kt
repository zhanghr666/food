package com.example.foodmanager.ui.components

import android.os.Build
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 按平台自适应地为输入法（键盘）留出空间。
 *
 * ## 为什么需要区分版本
 *
 * - **Android 15（API 35）及以后**：应用被强制边到边，键盘**叠加**在内容之上、窗口不再收缩。
 *   此时给可滚动容器加内边距，内容才会被顶起来，底部的输入框（如「备注」）才可见。
 * - **Android 14 及以前**：`android:windowSoftInputMode="adjustResize"` 会让窗口随键盘收缩，
 *   键盘占用的高度**已经体现在内容区**。此时再加内边距就会重复计算，出现
 *   "键盘上方一大片空白"（曾在小米 Android 16 上验证过：那是 Scaffold 底色问题，
 *   而重复内边距则会造成真实空白）。
 *
 * 因此这里只在 API 35+ 生效，低版本交给系统 `adjustResize` 处理，避免引入留白。
 *
 * 用法：加在**可滚动容器**（LazyColumn / verticalScroll 的 Column）上，
 * 而不是整个 Scaffold —— 只影响滚动区域，不影响顶栏与底部栏。
 */
@Composable
fun Modifier.imeAwarePadding(): Modifier =
    if (Build.VERSION.SDK_INT >= 35) this.imePadding() else this
