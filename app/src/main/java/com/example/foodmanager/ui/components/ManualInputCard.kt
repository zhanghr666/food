package com.example.foodmanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.foodmanager.core.DateUtils
import com.example.foodmanager.data.Categories
import com.example.foodmanager.data.FoodItem
import com.example.foodmanager.data.Locations
import com.example.foodmanager.data.Units

/**
 * 手工录入 / 编辑表单卡片（受控组件）。
 *
 * 与拍照、语音卡片保持同一视觉结构（图标 + 标题 + 说明 + 内容 + 操作），
 * 被「录入」页和「编辑食物」页共用 —— 所有编辑入口的页面样式因此保持一致。
 *
 * @param item          当前编辑中的数据（父级持有）
 * @param onChange      字段变化回调
 * @param onSave        保存（新增或修改）
 * @param saveLabel     主按钮文案
 * @param secondaryAction 次要操作（如「标记已用完」）
 * @param onCollapse    收起回调（为 null 时不显示）
 */
@Composable
fun ManualInputCard(
    item: FoodItem,
    onChange: (FoodItem) -> Unit,
    onSave: () -> Unit,
    customLocations: List<String> = emptyList(),
    onAddCustomLocation: (String) -> Unit = {},
    onRemoveCustomLocation: (String) -> Unit = {},
    title: String = "手工录入",
    subtitle: String = "逐项填写食物信息，填完点下方按钮保存",
    saveLabel: String = "添加到库存",
    showAddIcon: Boolean = true,
    secondaryAction: (@Composable () -> Unit)? = null,
    onCollapse: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var newLocation by remember { mutableStateOf("") }
    var dateTarget by remember { mutableStateOf<String?>(null) }
    var showLocationWarning by remember { mutableStateOf(false) }

    val canSave = item.name.isNotBlank()
    val doSave = onSave

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // 与拍照/语音卡片一致的标题行
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Edit, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = item.name,
                onValueChange = { v -> onChange(item.copy(name = v)) },
                label = { Text("食物名称 *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))
            DropdownField(
                label = "分类",
                options = listOf("") + Categories.ALL,
                selected = item.category,
                labelOf = { if (it.isBlank()) "未分类" else it },
                onSelect = { v -> onChange(item.copy(category = v)) },
            )

            Spacer(Modifier.height(10.dp))
            QuantityUnitRow(
                quantityText = formatQuantity(item.quantity),
                onQuantityChange = { v -> onChange(item.copy(quantity = v.toDoubleOrNull() ?: 0.0)) },
                unitOptions = Units.ALL,
                unit = item.unit,
                onUnitChange = { v -> onChange(item.copy(unit = v)) },
            )

            Spacer(Modifier.height(10.dp))
            DropdownField(
                label = "存放位置",
                options = (Locations.ALL + customLocations).distinct(),
                selected = item.location,
                labelOf = { it.ifBlank { "未填写" } },
                onSelect = { v -> onChange(item.copy(location = v)) },
                isError = item.location.isBlank(),
                supportingText = if (item.location.isBlank()) "建议填写存放位置，方便以后查找" else null,
            )

            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newLocation,
                    onValueChange = { newLocation = it },
                    label = { Text("添加常用位置（如：茶几底下）") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val clean = newLocation.trim()
                        if (clean.isNotBlank()) {
                            onAddCustomLocation(clean)
                            onChange(item.copy(location = clean))
                            newLocation = ""
                        }
                    },
                    enabled = newLocation.isNotBlank(),
                ) { Text("添加") }
            }
            if (customLocations.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    customLocations.forEach { loc ->
                        InputChip(
                            selected = false,
                            onClick = { onRemoveCustomLocation(loc) },
                            label = { Text("$loc ✕", style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            DateFieldRow(
                label = "买入时间",
                value = DateUtils.format(item.purchaseDate),
                onPick = { dateTarget = "purchase" },
            )
            Spacer(Modifier.height(8.dp))
            DateFieldRow(
                label = "生产日期（可选）",
                value = DateUtils.format(item.productionDate),
                onPick = { dateTarget = "production" },
                onClear = { onChange(item.copy(productionDate = null)) },
            )
            Spacer(Modifier.height(8.dp))
            DateFieldRow(
                label = "保质期至（可选）",
                value = DateUtils.format(item.expiryDate),
                onPick = { dateTarget = "expiry" },
                onClear = { onChange(item.copy(expiryDate = null)) },
            )

            Spacer(Modifier.height(6.dp))
            FormSectionLabel("快速设置保质期")
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("3天", "7天", "1个月", "3个月", "6个月", "1年", "2年").forEach { label ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            val days = when (label) {
                                "3天" -> 3
                                "7天" -> 7
                                "1个月" -> 30
                                "3个月" -> 90
                                "6个月" -> 182
                                "1年" -> 365
                                else -> 730
                            }
                            val base = item.productionDate ?: DateUtils.today()
                            onChange(
                                item.copy(
                                    productionDate = item.productionDate ?: base,
                                    expiryDate = DateUtils.plusDays(base, days),
                                ),
                            )
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = item.note,
                onValueChange = { v -> onChange(item.copy(note = v)) },
                label = { Text("备注") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { if (item.location.isBlank()) showLocationWarning = true else doSave() },
                    enabled = canSave,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = if (showAddIcon) Icons.Filled.Add else Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(saveLabel)
                }
                secondaryAction?.invoke()
                if (onCollapse != null) {
                    TextButton(onClick = onCollapse) { Text("收起") }
                }
            }
        }
    }

    // 未填存放位置的提醒
    if (showLocationWarning) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLocationWarning = false },
            title = { Text("还没有填写存放位置") },
            text = {
                Text("填写存放位置后，可以按位置快速查找，语音录入时也更容易自动识别。\n\n确定要这样保存吗？")
            },
            confirmButton = {
                TextButton(onClick = { showLocationWarning = false }) { Text("回去填写") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showLocationWarning = false
                    doSave()
                }) { Text("仍要保存") }
            },
        )
    }

    // 日期选择
    dateTarget?.let { target ->
        val initial = when (target) {
            "purchase" -> item.purchaseDate
            "production" -> item.productionDate ?: DateUtils.today()
            else -> item.expiryDate ?: DateUtils.today()
        }
        FoodDatePickerDialog(
            initialDate = initial,
            onDismiss = { dateTarget = null },
            onConfirm = { millis ->
                onChange(
                    when (target) {
                        "purchase" -> item.copy(purchaseDate = millis)
                        "production" -> item.copy(productionDate = millis)
                        else -> item.copy(expiryDate = millis)
                    },
                )
                dateTarget = null
            },
        )
    }
}
