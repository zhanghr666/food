package com.example.foodmanager.ui.entry

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.example.foodmanager.core.*
import com.example.foodmanager.ui.components.FoodDatePickerDialog
import com.example.foodmanager.ui.components.FoodDateSelector
import com.example.foodmanager.ui.components.FoodBottomSheet

@Composable
fun FieldCaption(draft: EntryDraft, field: EntryField) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(field.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (draft.needsConfirmation(field)) Text("请核对", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
internal fun EntryFormRow(draft: EntryDraft, field: EntryField, labelWidth: androidx.compose.ui.unit.Dp = 84.dp, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(labelWidth)) { FieldCaption(draft, field) }
        Column(Modifier.weight(1f)) { content() }
    }
}

@Composable
internal fun EntryEditableValue(value: String, onValue: (String) -> Unit, placeholder: String, enabled: Boolean,
    modifier: Modifier = Modifier, keyboardOptions: KeyboardOptions = KeyboardOptions.Default) {
    BasicTextField(value, onValue, enabled = enabled, singleLine = true, keyboardOptions = keyboardOptions,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = placeholder },
        decorationBox = { input ->
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                input()
            }
        })
}

@Composable
internal fun EntryChoiceValue(value: String, placeholder: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
        Text(value.ifBlank { placeholder }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
            color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NameCandidates(draft: EntryDraft, enabled: Boolean, onPick: (EntryField, String) -> Unit, onFoodPick: (String) -> Unit) {
    if (!draft.needsConfirmation(EntryField.NAME)) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("名称需要核对，可点选或直接修改", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            draft.fieldCandidates[EntryField.NAME].orEmpty().forEach { candidate ->
                AssistChip(onClick = { onFoodPick(candidate) }, enabled = enabled,
                    modifier = Modifier.testTag("entry-candidate-NAME-$candidate"), label = { Text(if (draft.hasSuggestedQuantity) "${draft.quantity}${draft.unit}$candidate" else candidate) })
            }
            TextButton(onClick = { onPick(EntryField.NAME, draft.name) }, enabled = enabled) { Text("保留原文") }
        }
    }
}

/** 六个字段使用统一候选状态；已有值也需核对，不把填入等同于正确。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FieldCandidates(draft: EntryDraft, field: EntryField, enabled: Boolean, onPick: (EntryField, String) -> Unit) {
    if (!draft.needsConfirmation(field)) return
    val current = when (field) {
        EntryField.NAME -> draft.name; EntryField.QUANTITY -> draft.quantity; EntryField.UNIT -> draft.unit
        EntryField.LOCATION -> draft.location; EntryField.CATEGORY -> draft.category
        EntryField.DATE -> draft.expiryDate?.let(DateUtils::format).orEmpty()
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${field.label}需要核对，可点选或直接修改", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf(current) + draft.fieldCandidates[field].orEmpty()).filter(String::isNotBlank).distinct().forEach { value ->
                val label = if (field == EntryField.DATE) DateUtils.parse(value)?.let { date ->
                    val days = DateUtils.daysBetween(DateUtils.today(), date)
                    "$value · ${if (days >= 0) "${days}天后" else "${-days}天前"}"
                } ?: value else value
                AssistChip(onClick = { onPick(field, value) }, enabled = enabled,
                    modifier = Modifier.testTag("entry-candidate-${field.name}-$value"), label = { Text(if (value == current) "确认 $label" else label) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChoiceSheet(title: String, options: List<String>, current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var custom by rememberSaveable(title, current) { mutableStateOf(current) }
    FoodBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.distinct().forEach { option -> FilterChip(selected = current == option, onClick = { onPick(option); onDismiss() }, label = { Text(option) }) }
            }
            OutlinedTextField(custom, { custom = it }, label = { Text("也可以自己填写") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Button(onClick = { if (custom.isNotBlank()) { onPick(custom.trim()); onDismiss() } }, enabled = custom.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("使用这个值") }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DateSheet(draft: EntryDraft, vm: EntryViewModel, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf(draft.dateKind) }
    var calendar by remember { mutableStateOf("") }
    var production by remember { mutableStateOf(draft.productionDate) }
    var amount by remember { mutableStateOf(draft.shelfLifeAmount?.toString().orEmpty()) }
    var unit by remember { mutableStateOf(draft.shelfLifeUnit) }
    var selected by rememberSaveable { mutableStateOf(CalendarDate.fromMillis(draft.expiryDate ?: DateUtils.today())) }
    var calculation by rememberSaveable { mutableStateOf(false) }
    FoodBottomSheet(onDismiss = onDismiss, modifier = Modifier.testTag("entry-date-sheet")) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("设置日期", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(kind == "expiry", { kind = "expiry" }, label = { Text("包装到期日") })
                FilterChip(kind == "reminder", { kind = "reminder" }, label = { Text("计划食用日") })
            }
            Text(if (kind == "expiry") "按包装标注设置，不根据食品种类猜测保质期。" else "适合水果、蔬菜和剩菜。这是食用计划，不代表过期。", style = MaterialTheme.typography.bodySmall)
            FoodDateSelector(selected) { selected = it }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "今天", 1 to "明天", 3 to "3 天后", 5 to "5 天后", 7 to "7 天后", 30 to "30 天后").forEach { (days, label) ->
                    AssistChip(onClick = { vm.date(DateUtils.todayPlus(days), kind); onDismiss() }, label = { Text(label) })
                }
            }
            FilledTonalButton(onClick = { vm.date(selected.toMillis(), kind); onDismiss() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("date-confirm")) { Text("使用这个日期") }
            if (kind == "expiry") {
                HorizontalDivider()
                TextButton(onClick = { calculation = !calculation }) { Text(if (calculation) "收起保质期计算" else "用生产日期和保质期计算") }
                if (calculation) {
                    OutlinedButton(onClick = { calendar = "production" }, modifier = Modifier.fillMaxWidth()) { Text("生产日期：${production?.let(DateUtils::format) ?: "请选择"}") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(amount, { amount = it }, label = { Text("保质期") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        Column { listOf("天", "月", "年").forEach { value -> FilterChip(unit == value, { unit = value }, label = { Text(value) }) } }
                    }
                    Button(onClick = { production?.let { vm.shelfLife(it, amount.toInt(), unit); onDismiss() } },
                        enabled = production != null && (amount.toIntOrNull() ?: 0) in 1..36500, modifier = Modifier.fillMaxWidth()) { Text("计算到期日并使用") }
                }
            }
        }
    }
    if (calendar.isNotBlank()) FoodDatePickerDialog(
        initialDate = production ?: DateUtils.today(), title = "选择生产日期",
        onDismiss = { calendar = "" }, onConfirm = { value ->
            production = value; calendar = ""
        })
}
