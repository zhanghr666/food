package com.example.foodmanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.foodmanager.core.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 可编辑的下拉框（组合框）：既能从常用项里选，也能直接输入自定义值。
 *
 * 用于「存放位置」「分类」这类既要有常用项、又允许自由填写的字段
 * （例如语音识别出「茶几底下」，或用户临时输入「宝宝辅食」）。
 *
 * @param resetToken 父级每次清空/重置表单时 +1。
 *   用来**强制关闭已展开的下拉并同步收起状态** —— 否则表单被清空时，
 *   值从「主食」变成 `""` 会触发 onValueChange 而把下拉重新弹开，盖住整个页面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComboField(
    label: String,
    options: List<String>,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    resetToken: Int = 0,
) {
    // resetToken 变化时重建状态（= 收起下拉）
    var expanded by remember(resetToken) { mutableStateOf(false) }
    // 输入时过滤出匹配项，方便快速定位
    val filtered = remember(value, options) {
        if (value.isBlank()) options else options.filter { it.contains(value) }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                // 只在"用户真的在输入"时展开：值为空说明是表单被清空，不要弹下拉
                if (it.isNotBlank()) expanded = true
                onValueChange(it)
            },
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.labelSmall) } },
            supportingText = supportingText?.let { { Text(it) } },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .fillMaxWidth(),
        )
        if (filtered.isNotEmpty()) {
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                filtered.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onValueChange(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * 数量输入框的初始文本：未填写（0）时显示 `0`。
 *
 * 输入框用独立的文本状态维护，用户键入时直接替换，
 * 不会出现"初始 0 占位、输入 5 变成 50"的问题。
 */
fun initialQuantityText(quantity: Double): String =
    if (quantity <= 0.0) "0" else formatQuantity(quantity)

/** 下拉选择框：分类 / 单位 共用 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownField(
    label: String,
    options: List<String>,
    selected: String,
    labelOf: (String) -> String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    /** 父级重置表单时 +1，用于关闭可能还开着的下拉 */
    resetToken: Int = 0,
) {
    var expanded by remember(resetToken) { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = labelOf(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            supportingText = supportingText?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labelOf(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** 日期选择行（点击弹出日历，可清除） */
@Composable
fun DateFieldRow(
    label: String,
    value: String,
    onPick: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    Card(
        onClick = onPick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (onClear != null) {
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "清除", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** 小标题 */
@Composable
fun FormSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

/** 一排可点选标签 */
@Composable
fun FormChipRow(
    options: List<String>,
    selected: String,
    labelOf: (String) -> String,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(labelOf(option), style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

/**
 * 把若干信息片段拼成一行，自动跳过空白片段。
 *
 * 用于列表副标题：位置可能为空、数量可能为 0，
 * 直接字符串拼接会留下多余的「　　」空白。
 */
fun joinInfo(vararg parts: String?, separator: String = "　"): String =
    parts.mapNotNull { it?.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(separator)

/** 数量描述：数量为 0 表示未填写，返回空串（避免出现「0个」） */
fun describeQuantityText(quantity: Double, unit: String): String =
    if (quantity > 0) "${formatQuantity(quantity)}$unit" else ""

/** 数量 + 单位 一行 */
@Composable
fun QuantityUnitRow(
    quantityText: String,
    onQuantityChange: (String) -> Unit,
    unitOptions: List<String>,
    unit: String,
    onUnitChange: (String) -> Unit,
    resetToken: Int = 0,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = quantityText,
            onValueChange = onQuantityChange,
            label = { Text("数量") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        DropdownField(
            label = "单位",
            options = unitOptions,
            selected = unit,
            labelOf = { it },
            onSelect = onUnitChange,
            modifier = Modifier.weight(1f),
            resetToken = resetToken,
        )
    }
}
