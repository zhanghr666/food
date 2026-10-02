package com.example.foodmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.*
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.foodmanager.core.DateUtils
import com.example.foodmanager.data.FoodItem
import com.example.foodmanager.data.FoodStatus
import com.example.foodmanager.ui.theme.StatusColors

/** 状态圆点 + 文字标签 */
@Composable
fun StatusChip(status: FoodStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        FoodStatus.EXPIRED -> StatusColors.expired
        FoodStatus.EXPIRING_SOON -> StatusColors.expiring
        FoodStatus.FRESH -> StatusColors.fresh
        FoodStatus.NO_EXPIRY -> StatusColors.noExpiry
    }
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** 存放位置的小图标 */
@Composable
fun LocationIcon(location: String, modifier: Modifier = Modifier) {
    val icon = when {
        location.contains("冷冻") || location.contains("变温") -> Icons.Outlined.AcUnit
        location.contains("冷藏") -> Icons.Outlined.Kitchen
        else -> Icons.Outlined.WbSunny
    }
    Icon(
        imageVector = icon,
        contentDescription = location,
        modifier = modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
}

fun formatQuantity(value: Double): String =
    if (value.isFinite()) java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString() else "未填写"

/**
 * 食物卡片：展示名称、数量、位置、买入时间、保质期与状态，并提供数量加减、编辑、删除、归档操作。
 */
@Composable
fun FoodCard(
    item: FoodItem,
    warnDays: Int,
    today: Long,
    onClick: () -> Unit,
    onQuantityChange: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMore by rememberSaveable(item.id) { mutableStateOf(false) }
    val status = item.status(today, warnDays)
    val days = item.daysToExpiry(today)
    val accent = when (status) {
        FoodStatus.EXPIRED -> StatusColors.expired
        FoodStatus.EXPIRING_SOON -> StatusColors.expiring
        FoodStatus.FRESH -> StatusColors.fresh
        FoodStatus.NO_EXPIRY -> StatusColors.noExpiry
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // 左侧状态色条
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name.ifBlank { "未命名食物" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (item.category.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = item.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                }

                StatusChip(status)
                Spacer(Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(describeQuantityText(item.quantity, item.unit, item.quantitySpecified),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    LocationIcon(item.location)
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = item.location.ifBlank { "未填写位置" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }

                Spacer(Modifier.height(3.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "买入 ${DateUtils.describeRelative(item.purchaseDate)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (item.expiryDate == null) {
                            ""
                        } else {
                            "到期 ${DateUtils.formatShort(item.expiryDate)}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = accent,
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = if (days == null) "补充到期日后可提醒" else DateUtils.describeRemaining(days),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onQuantityChange) { Text("修改余量") }
                    TextButton(onClick = onEdit) { Text("编辑") }
                    Box {
                        IconButton(onClick = { showMore = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多操作：${item.name}")
                        }
                        DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                            DropdownMenuItem(text = { Text("归档（已用完 / 丢弃）") },
                                onClick = { showMore = false; onArchive() })
                            DropdownMenuItem(text = { Text("永久删除", color = MaterialTheme.colorScheme.error) },
                                onClick = { showMore = false; onDelete() })
                        }
                    }
                }
            }
        }
    }
}

/** 空状态 */
@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.Inventory2,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

/** 统计小卡片 */
@Composable
fun StatCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = color,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
