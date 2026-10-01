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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
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
    title: String = "手工录入",
    subtitle: String = "逐项填写食物信息，填完点下方按钮保存",
    saveLabel: String = "添加到库存",
    showAddIcon: Boolean = true,
    secondaryAction: (@Composable () -> Unit)? = null,
    onCollapse: (() -> Unit)? = null,
    /** 保存后把焦点送回名称框，便于连续录入 */
    nameFocusRequester: FocusRequester? = null,
    /** 父级每次重置表单时 +1，用于清空表单内部状态（如数量框） */
    resetToken: Int = 0,
    /** true 时由父级负责「未填存放位置」的提醒（避免两处弹窗） */
    parentHandlesLocationWarning: Boolean = false,
    /** 表单内显示的一行状态提示（如"已保存xxx"），不受键盘遮挡 */
    statusMessage: String = "",
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var dateTarget by remember { mutableStateOf<String?>(null) }
    var showLocationWarning by remember { mutableStateOf(false) }

    // 数量输入框用独立文本状态维护：
    // 若直接绑定数值，初始的 0/1 会占住输入框，用户输入 5 会变成 50/15。
    var quantityText by remember { mutableStateOf(initialQuantityText(item.quantity)) }
    // 编辑对象变化，或父级重置表单时，重新同步
    LaunchedEffect(item.id, resetToken) {
        quantityText = initialQuantityText(item.quantity)
    }

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

            // 保存成功提示放在表单顶部：键盘弹出时也一定看得见
            if (statusMessage.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(text = statusMessage, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = item.name,
                onValueChange = { v -> onChange(item.copy(name = v)) },
                label = { Text("食物名称 *") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (nameFocusRequester != null) {
                            Modifier.focusRequester(nameFocusRequester)
                        } else {
                            Modifier
                        },
                    ),
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
                quantityText = quantityText,
                onQuantityChange = { raw ->
                    // 只保留数字与小数点，避免带出其它字符
                    val cleaned = raw.filter { it.isDigit() || it == '.' }
                    quantityText = cleaned
                    onChange(item.copy(quantity = cleaned.toDoubleOrNull() ?: 0.0))
                },
                unitOptions = Units.ALL,
                unit = item.unit,
                onUnitChange = { v -> onChange(item.copy(unit = v)) },
            )

            Spacer(Modifier.height(10.dp))
            // 存放位置：可选常用位置，也可直接输入自定义值；
            // 不标红（红框在 Material 里表示"校验失败"，这里是"还没填"，用文字提示即可）
            ComboField(
                label = "存放位置",
                options = customLocations.distinct(),
                value = item.location,
                onValueChange = { v -> onChange(item.copy(location = v)) },
                placeholder = "可直接输入，如：茶几底下",
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (item.location.isBlank()) {
                    "建议填写位置，方便以后查找。常用位置可在「设置 - 常用存放地点」里增删；" +
                        "也可以直接输入，或语音说一个位置（如「放在茶几底下」）自动识别。"
                } else {
                    "常用位置可在「设置 - 常用存放地点」里增删；也可以直接输入，或" +
                        "语音说一个位置（如「放在茶几底下」）自动识别。"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
            )

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
            // 备注是表单最后一项：键盘弹出时若无处可滚，会被输入法永久遮住。
            // 这里在获得焦点时主动请求滚动到可视区（外层容器已做输入法避让）。
            val noteBringIntoView = remember { BringIntoViewRequester() }
            OutlinedTextField(
                value = item.note,
                onValueChange = { v -> onChange(item.copy(note = v)) },
                label = { Text("备注") },
                minLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(noteBringIntoView)
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            scope.launch { noteBringIntoView.bringIntoView() }
                        }
                    },
            )

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        if (!parentHandlesLocationWarning && item.location.isBlank()) {
                            showLocationWarning = true
                        } else {
                            doSave()
                        }
                    },
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
