package com.example.foodmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.foodmanager.core.*
import com.example.foodmanager.data.*
import com.example.foodmanager.ui.components.*

@Composable
fun ReminderCenter(state: HouseholdState, today: Long, warnDays: Int, busy: Boolean, onBack: () -> Unit,
    onOpen: (ManagedItem) -> Unit, onComplete: (Long, Boolean) -> Unit, onSnooze: (Long, Int) -> Unit) {
    var kind by rememberSaveable { mutableStateOf("全部") }
    var mode by rememberSaveable { mutableStateOf("待处理") }
    val items = state.records.filter { !it.archived && (kind == "全部" || it.kind == if (kind == "食品") "food" else "object") }.associateBy { it.id }
    val list = state.reminders.filter { it.itemId in items && it.completed == (mode == "已处理") }
        .sortedWith(compareBy<ItemReminder> { it.checkDate(warnDays) }.thenBy { it.itemId })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }; Text("提醒清单", style = MaterialTheme.typography.titleLarge) } }
        item { Text("食品和物品共用 · 已处理不改变库存或食品到期日", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { FoodTabs(listOf("全部", "食品", "物品"), kind, { kind = it }); FoodSegments(listOf("待处理", "已处理").map { it to it }, mode, { mode = it }) }
        item { Text("共 ${list.size} 项 · 提醒可能受系统省电限制延迟", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (!state.loaded) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else if (list.isEmpty()) item { Text("暂无${mode}提醒", Modifier.padding(vertical = 32.dp)) }
        items(list, key = { it.itemId }) { reminder ->
            val item = items.getValue(reminder.itemId)
            Card(onClick = { onOpen(item) }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(item.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium); Text(if (item.kind == "food") "食品" else "物品", style = MaterialTheme.typography.bodySmall) }
                    ReminderDescription(reminder, today, warnDays)
                    Text(item.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ReminderActions(reminder, today, warnDays, busy, { onComplete(item.id, !reminder.completed) }, { onSnooze(item.id, it) })
                }
            }
        }
    }
}

@Composable
private fun ReminderDescription(reminder: ItemReminder, today: Long, warnDays: Int) {
    val date = reminder.checkDate(warnDays)
    val days = DateUtils.daysBetween(today, date)
    Text(reminder.title, style = MaterialTheme.typography.bodyMedium)
    Text("${if (reminder.kind == "expiry") "包装到期" else "目标日期"} ${DateUtils.format(reminder.targetDate)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(if (reminder.completed) "已处理" else "${if (reminder.snoozedUntil != null) "延后至" else "提醒日期"} ${DateUtils.format(date)} · ${when { days < 0 -> "已到提醒日"; days == 0L -> "今天提醒"; else -> "$days 天后提醒" }}",
        style = MaterialTheme.typography.labelLarge, color = if (!reminder.completed && days <= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
}

@Composable
private fun ReminderActions(reminder: ItemReminder, today: Long, warnDays: Int, busy: Boolean, onComplete: () -> Unit, onSnooze: (Int) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row {
        TextButton(onComplete, enabled = !busy) { Text(if (reminder.completed) "恢复提醒" else "已处理") }
        if (!reminder.completed && reminder.due(today, warnDays)) Box {
            TextButton({ menu = true }, enabled = !busy) { Text("稍后提醒"); Icon(Icons.Outlined.ExpandMore, null) }
            DropdownMenu(menu, { menu = false }) { listOf(1, 3, 7).forEach { days -> DropdownMenuItem({ Text("$days 天后") }, { menu = false; onSnooze(days) }) } }
        }
    }
}

@Composable
fun ItemReminderSection(item: ManagedItem, reminder: ItemReminder?, today: Long, warnDays: Int, busy: Boolean,
    onSave: (String, Long, (Result<Unit>) -> Unit) -> Unit, onRemove: ((Result<Unit>) -> Unit) -> Unit,
    onComplete: () -> Unit, onSnooze: (Int) -> Unit) {
    var edit by rememberSaveable(item.id) { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("提醒", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                if (item.kind == "object") TextButton({ edit = true }, enabled = !busy && !item.archived) { Text(if (reminder == null) "设置提醒" else "修改提醒") }
            }
            if (reminder == null) Text(if (item.kind == "food") "设置食品日期后自动建立提醒" else "可设置到期、保养、归还等单次提醒", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                ReminderDescription(reminder, today, warnDays)
                if (item.archived) Text("已归档，暂停通知", style = MaterialTheme.typography.bodySmall)
                else ReminderActions(reminder, today, warnDays, busy, onComplete, onSnooze)
            }
        }
    }
    if (edit) ReminderEditor(reminder, { edit = false }, onSave, onRemove)
}

@Composable
private fun ReminderEditor(reminder: ItemReminder?, onDismiss: () -> Unit,
    onSave: (String, Long, (Result<Unit>) -> Unit) -> Unit, onRemove: ((Result<Unit>) -> Unit) -> Unit) {
    var title by remember { mutableStateOf(reminder?.title ?: "检查物品") }
    var date by remember { mutableLongStateOf(reminder?.targetDate ?: DateUtils.todayPlus(7)) }
    var picking by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf(false) }
    val done: (Result<Unit>) -> Unit = { result -> saving = false; if (result.isSuccess) onDismiss() else error = result.exceptionOrNull()?.message ?: "操作失败，请重试" }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("设置单次提醒") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("提醒什么") }, placeholder = { Text("例如：更换滤芯、归还工具") }, enabled = !saving, singleLine = true)
            OutlinedButton({ picking = true }, enabled = !saving) { Icon(Icons.Outlined.CalendarMonth, null); Text(" ${DateUtils.format(date)}") }
            Text("本机每日检查，可能受省电限制延迟", style = MaterialTheme.typography.bodySmall)
            if (reminder != null) TextButton({ removing = true }, enabled = !saving) { Text("移除提醒", color = MaterialTheme.colorScheme.error) }
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton({ saving = true; onSave(title, date, done) }, enabled = !saving && title.isNotBlank()) { Text("保存") } }, dismissButton = { TextButton(onDismiss, enabled = !saving) { Text("取消") } })
    if (picking) FoodDatePickerDialog(date, { picking = false }, { date = it; picking = false }, "提醒日期")
    if (removing) AlertDialog(onDismissRequest = { removing = false }, title = { Text("移除这项提醒？") }, text = { Text("库存和照片不会删除。") },
        confirmButton = { TextButton({ removing = false; saving = true; onRemove(done) }) { Text("移除") } }, dismissButton = { TextButton({ removing = false }) { Text("取消") } })
}

@Composable
fun PlaceEditor(source: String, mode: String, state: HouseholdState, onDismiss: () -> Unit,
    onChange: (String, String, Boolean, (Result<Unit>) -> Unit) -> Unit, onAliases: (String, String, (Result<Unit>) -> Unit) -> Unit) {
    val saved = state.locations.firstOrNull { it.name == source }
    var value by remember(source, mode) { mutableStateOf(if (mode == "别名") saved?.aliasList?.joinToString("、").orEmpty() else "") }
    var confirmed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val count = state.records.count { it.location == source }
    val targets = (state.locations.map { it.name } + state.records.map { it.location }).filter { it.isNotBlank() && it != source }.distinct()
    val done: (Result<Unit>) -> Unit = { result -> saving = false; if (result.isSuccess) onDismiss() else error = result.exceptionOrNull()?.message ?: "操作失败，请重试" }
    FoodBottomSheet({ if (!saving) onDismiss() }) {
        Text("$mode · $source", style = MaterialTheme.typography.titleLarge)
        Text(if (mode == "别名") "只匹配你明确设置的名称，多个别名用逗号分隔" else "将更新 $count 条记录（含已归档）、常用模板及最近位置；旧名称保留为别名", Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value, { value = it; confirmed = false; error = "" }, label = { Text(if (mode == "别名") "位置别名" else "目标位置") }, enabled = !saving, modifier = Modifier.fillMaxWidth())
        if (mode == "合并") LazyColumn(Modifier.fillMaxWidth().heightIn(max = 160.dp)) {
            items(targets.filter { value.isBlank() || it.contains(value.trim(), true) }, key = { it }) { target -> TextButton({ value = target; confirmed = false }, enabled = !saving) { Text(target) } }
        }
        if (mode != "别名") Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(confirmed, { confirmed = it }, enabled = !saving); Text("确认更新上述记录", style = MaterialTheme.typography.bodyMedium) }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
            OutlinedButton(onDismiss, enabled = !saving, modifier = Modifier.weight(1f)) { Text("取消") }
            Button({ saving = true; if (mode == "别名") onAliases(source, value, done) else onChange(source, value, mode == "合并", done) },
                enabled = !saving && (mode == "别名" || confirmed && value.isNotBlank() && value.trim() != source), modifier = Modifier.weight(1f)) { Text("保存") }
        }
    }
}

@Composable
fun GlobalSearchScreen(state: HouseholdState, onBack: () -> Unit, onOpen: (ManagedItem) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("全部") }
    var archived by rememberSaveable { mutableStateOf(false) }
    val records = state.records.filter { it.archived == archived && (kind == "全部" || it.kind == if (kind == "食品") "food" else "object") }
    val results = HouseholdSearch.find(records, query, state.locations)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }; Text("查找家里的东西", style = MaterialTheme.typography.titleLarge) } }
        item { FoodSearchField(query, { query = it }, "名称、位置、型号或备注") }
        item { Text("可用空格组合条件，例如：被子 衣柜；支持位置别名", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { FoodTabs(listOf("全部", "食品", "物品"), kind, { kind = it }); Row(verticalAlignment = Alignment.CenterVertically) {
            Text("查看已归档", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); Switch(archived, { archived = it })
        } }
        item { Text("共 ${results.size} 条记录", style = MaterialTheme.typography.bodySmall) }
        if (!state.loaded) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else if (results.isEmpty()) item { Text("没有找到，试试名称或位置的一部分", Modifier.padding(vertical = 32.dp)) }
        items(results, key = { it.id }) { item -> Card(onClick = { onOpen(item) }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row { Text(item.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium); Text(if (item.kind == "food") "食品" else "物品", style = MaterialTheme.typography.bodySmall) }
                Text("${formatAmount(item.quantity)}${item.unit} · ${item.location}", maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.specification.isNotBlank()) Text(item.specification, style = MaterialTheme.typography.bodySmall)
            }
        } }
    }
}
