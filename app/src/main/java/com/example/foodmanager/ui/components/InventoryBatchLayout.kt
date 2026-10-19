package com.example.foodmanager.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.foodmanager.data.StorageLocation

typealias InventoryBatchAction = (List<Long>, String, String, (Result<Unit>) -> Unit) -> Unit

/** 多选仅作用于当前可见结果，操作栏位于列表外，不遮挡最后一条记录。 */
@Composable
fun InventoryBatchLayout(ids: List<Long>, filterKey: List<String>, archived: Boolean, busy: Boolean,
    locations: List<StorageLocation>, onBatch: InventoryBatchAction,
    content: @Composable (Boolean, Set<Long>, (Long) -> Unit, (Long) -> Unit) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(listOf<Long>()) }
    var action by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    val selectedSet = selected.toSet().intersect(ids.toSet())
    fun exit() { mode = false; selected = emptyList(); action = ""; error = "" }
    LaunchedEffect(filterKey) { if (!saving) exit() }
    LaunchedEffect(ids) {
        if (!saving) { selected = selected.filter { it in ids }; if (ids.isEmpty()) exit() }
    }
    BackHandler(mode) { if (!saving && !busy) exit() }
    val toggle: (Long) -> Unit = { id -> if (!busy && !saving) {
        selected = if (id in selected) selected - id else selected + id
    } }
    Column(Modifier.fillMaxSize()) {
        if (mode) Surface(color = MaterialTheme.colorScheme.primaryContainer) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("已选 ${selectedSet.size} 条", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TextButton({ selected = if (selectedSet.size == ids.size) emptyList() else ids }, enabled = !busy && !saving) {
                    Text(if (selectedSet.size == ids.size && ids.isNotEmpty()) "取消全选" else "全选当前结果")
                }
                TextButton({ exit() }, enabled = !busy && !saving) { Text("取消多选") }
            }
        }
        Box(Modifier.weight(1f)) { content(mode, selectedSet, toggle, { id -> if (!busy && !saving) { mode = true; if (id !in selected) selected = selected + id } }) }
        if (mode) Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ exit() }, enabled = !busy && !saving,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)) { Text("取消") }
                listOf("move" to "移动位置", (if (archived) "restore" else "archive") to (if (archived) "恢复" else "归档"), "delete" to "删除").forEach { (key, title) ->
                    OutlinedButton({ action = key; target = ""; error = "" }, enabled = selectedSet.isNotEmpty() && !busy && !saving,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(title, color = if (key == "delete") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    if (action.isNotEmpty()) {
        val title = when (action) { "move" -> "移动位置"; "archive" -> "归档"; "restore" -> "恢复到库存"; else -> "永久删除" }
        AlertDialog(onDismissRequest = { if (!saving) action = "" }, title = { Text("$title ${selectedSet.size} 条记录？") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(when (action) {
                    "move" -> "选择或填写目标位置，再点确认移动。取消不会修改任何记录。"
                    "archive" -> "归档后暂停提醒，可在已归档列表中恢复。"
                    "restore" -> "恢复后重新纳入库存，未处理提醒将继续生效。"
                    else -> "记录、照片附件和提醒将被永久删除，无法恢复。暂时不用可选择归档。"
                }, style = MaterialTheme.typography.bodyMedium)
                if (action == "move") {
                    OutlinedTextField(target, { target = it; error = "" }, singleLine = true, enabled = !saving,
                        label = { Text("存放位置，可直接填写") }, modifier = Modifier.fillMaxWidth())
                    val common = locations.sortedBy { it.sortOrder }.take(4)
                    if (common.isNotEmpty()) {
                        Text("常用位置", style = MaterialTheme.typography.labelMedium)
                        common.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { location -> OutlinedButton({ target = location.name }, enabled = !saving, modifier = Modifier.weight(1f)) { Text(location.name, maxLines = 2) } }
                        } }
                    }
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton({ action = ""; error = "" }, enabled = !saving,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(10.dp)) { Text("取消") }
                Button({
                val snapshot = selectedSet.toList()
                saving = true
                onBatch(snapshot, action, target) { result ->
                    saving = false
                    if (result.isSuccess) exit() else error = result.exceptionOrNull()?.message ?: "操作失败，请重试"
                }
                }, enabled = !saving && !busy && selectedSet.isNotEmpty() && (action != "move" || target.isNotBlank()),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (action == "delete") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)) {
                    Text(if (saving) "处理中" else when (action) { "move" -> "确认移动"; "delete" -> "永久删除"; "restore" -> "确认恢复"; else -> "确认归档" })
                }
            }
        })
    }
}
