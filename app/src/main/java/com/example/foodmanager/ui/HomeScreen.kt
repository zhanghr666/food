package com.example.foodmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.util.Log
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.foodmanager.core.DateUtils
import com.example.foodmanager.data.FoodItem
import com.example.foodmanager.data.FoodStatus
import com.example.foodmanager.ui.components.EmptyState
import com.example.foodmanager.ui.components.StatCard
import com.example.foodmanager.ui.components.StatusChip
import com.example.foodmanager.ui.theme.StatusColors

/** 首页：提醒横幅 + 统计 + 快捷录入 + 需要处理的清单 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: FoodViewModel,
    onOpenInventory: () -> Unit,
    onAddManual: () -> Unit,
    onOpenScan: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenItem: (Long) -> Unit,
) {
    val context = LocalContext.current
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val allItems by viewModel.items.collectAsStateWithLifecycle()
    var today by remember { mutableLongStateOf(DateUtils.today()) }

    LaunchedEffect(Unit) {
        Log.i("HomeScreen", "首页已渲染：记录数=${stats.total}")
        viewModel.refreshToday()
        today = DateUtils.today()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("我的食物库", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = DateUtils.formatCn(today) + "　共 ${stats.total} 件",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ---------- 提醒横幅 ----------
            if (settings.homeBannerEnabled && alerts.isNotEmpty()) {
                item {
                    AlertBanner(
                        alerts = alerts,
                        today = today,
                        warnDays = settings.warnDays,
                        onClick = onOpenInventory,
                    )
                }
            } else if (stats.total > 0) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = StatusColors.fresh.copy(alpha = 0.12f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = StatusColors.fresh,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "暂时没有临期或过期食物",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = StatusColors.fresh,
                                )
                                Text(
                                    text = "继续保持！有新的临期食物会在这里和通知里提醒你",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // ---------- 统计 ----------
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatCard("已过期", stats.expired.toString(), StatusColors.expired, Modifier.weight(1f))
                    StatCard("临期", stats.expiringSoon.toString(), StatusColors.expiring, Modifier.weight(1f))
                    StatCard("新鲜", stats.fresh.toString(), StatusColors.fresh, Modifier.weight(1f))
                    StatCard("无保质期", stats.noExpiry.toString(), StatusColors.noExpiry, Modifier.weight(1f))
                }
            }

            // ---------- 快捷录入 ----------
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QuickAction(
                        title = "拍照录入",
                        subtitle = "批量识别包装",
                        icon = Icons.Filled.PhotoCamera,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenScan,
                    )
                    QuickAction(
                        title = "语音录入",
                        subtitle = "说一句就记好",
                        icon = Icons.Filled.Mic,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenVoice,
                    )
                    QuickAction(
                        title = "手工录入",
                        subtitle = "逐项填写",
                        icon = Icons.Filled.Edit,
                        modifier = Modifier.weight(1f),
                        onClick = onAddManual,
                    )
                }
            }

            // ---------- 需要处理的清单 ----------
            if (alerts.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable { onOpenInventory() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = StatusColors.expired,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "需要尽快处理（${alerts.size}）",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text("全部", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Icon(
                            Icons.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                items(alerts.take(8), key = { it.id }) { item ->
                    AlertRow(item = item, today = today, warnDays = settings.warnDays, onClick = { onOpenItem(item.id) })
                }
            } else if (allItems.isEmpty()) {
                item {
                    EmptyState(
                        title = "还没有记录任何食物",
                        description = "用「拍照/OCR」一次录入多条，或者用语音说一句就完成录入",
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertBanner(
    alerts: List<FoodItem>,
    today: Long,
    warnDays: Int,
    onClick: () -> Unit,
) {
    val expired = alerts.count { (it.daysToExpiry(today) ?: Long.MAX_VALUE) < 0 }
    val soon = alerts.size - expired
    val color = if (expired > 0) StatusColors.expired else StatusColors.expiring

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.13f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (expired > 0) Icons.Filled.ErrorOutline else Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = color,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = when {
                        expired > 0 && soon > 0 -> "$expired 件已过期，$soon 件即将过期"
                        expired > 0 -> "$expired 件食物已经过期"
                        else -> "$soon 件食物即将过期"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.ArrowForward,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "提前 $warnDays 天提醒　·　" + alerts.take(3).joinToString("、") { it.name } +
                    if (alerts.size > 3) " 等" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuickAction(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun AlertRow(
    item: FoodItem,
    today: Long,
    warnDays: Int,
    onClick: () -> Unit,
) {
    val status = item.status(today, warnDays)
    val color = when (status) {
        FoodStatus.EXPIRED -> StatusColors.expired
        FoodStatus.EXPIRING_SOON -> StatusColors.expiring
        else -> StatusColors.fresh
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name.ifBlank { "未命名食物" },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "${com.example.foodmanager.ui.components.formatQuantity(item.quantity)}${item.unit}　${item.location}　到期 ${DateUtils.formatShort(item.expiryDate)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusChip(status)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = DateUtils.describeRemaining(item.daysToExpiry(today)),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                )
            }
        }
    }
}
