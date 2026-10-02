package com.example.foodmanager.ui.theme

import androidx.compose.ui.graphics.Color

// 品牌色：温暖的「果蔬橙 + 保鲜绿」
val BrandOrange = Color(0xFFB4531A)
val BrandOrangeLight = Color(0xFFFFB77C)
val BrandContainerLight = Color(0xFFFFDCC4)
val BrandContainerDark = Color(0xFF7A3410)

val FreshGreen = Color(0xFF2E6B4F)
val FreshGreenLight = Color(0xFF8FD6B0)
val FreshContainerLight = Color(0xFFC8EDD7)
val FreshContainerDark = Color(0xFF1E4D38)

val WarnAmber = Color(0xFF8A6100)
val WarnAmberLight = Color(0xFFF2C66D)
val WarnContainerLight = Color(0xFFFFE9B8)
val WarnContainerDark = Color(0xFF5F4200)

val DangerRed = Color(0xFFB3261E)
val DangerRedLight = Color(0xFFFFB4AB)
val DangerContainerLight = Color(0xFFFFDAD6)
val DangerContainerDark = Color(0xFF8C1D18)

val SurfaceLight = Color(0xFFFFF8F5)
val SurfaceDark = Color(0xFF1A110D)
val OnSurfaceLight = Color(0xFF231A16)
val OnSurfaceDark = Color(0xFFF1DFD8)

/** 状态配色（列表里的圆点与标签） */
object StatusColors {
    val expired: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.error
    val expiring: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.tertiary
    val fresh: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.secondary
    val noExpiry: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
}
