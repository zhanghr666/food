package com.example.foodmanager.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.foodmanager.core.DateUtils
import com.example.foodmanager.core.formatAmount

data class InventoryStat(val value: String, val label: String, val color: Color, val onClick: (() -> Unit)? = null)

@Composable
fun InventoryHeading(title: String, today: Long, onSearch: (() -> Unit)? = null, onReminders: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(DateUtils.formatShort(today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onSearch != null) IconButton(onSearch) { Icon(Icons.Outlined.Search, "全局查找食品和物品") }
        if (onReminders != null) IconButton(onReminders) { Icon(Icons.Outlined.NotificationsNone, "提醒清单") }
    }
}

@Composable
fun InventoryStats(stats: List<InventoryStat>) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            stats.forEach { stat ->
                val content: @Composable () -> Unit = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stat.value, style = MaterialTheme.typography.titleLarge, color = stat.color)
                        Text(stat.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                if (stat.onClick != null) TextButton(stat.onClick, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { content() }
                else Box(Modifier.weight(1f).padding(4.dp), contentAlignment = Alignment.Center) { content() }
            }
        }
    }
}

@Composable
fun InventorySearchActions(value: String, onValue: (String) -> Unit, placeholder: String, addLabel: String, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FoodSearchField(value, onValue, placeholder, Modifier.weight(1f))
        Button(onAdd, shape = RoundedCornerShape(12.dp), modifier = Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
            Text(addLabel, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun InventorySort(order: String, options: List<String>, onOrder: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton({ expanded = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(order, style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
        }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { expanded = false; onOrder(option) },
                trailingIcon = { if (order == option) Icon(Icons.Outlined.Check, null) }) }
        }
    }
}

@Composable
fun InventoryRecordCard(name: String, quantity: String, location: String, metadata: String, note: String = "",
    status: String = "", statusColor: Color = MaterialTheme.colorScheme.primary,
    archived: Boolean = false, busy: Boolean = false, onOpen: () -> Unit, onEdit: () -> Unit,
    onQuantity: () -> Unit, onArchive: () -> Unit, onDelete: () -> Unit,
    selectionMode: Boolean = false, selected: Boolean = false, onLongPress: (() -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    Card(modifier = Modifier.combinedClickable(enabled = !busy, onClick = onOpen, onLongClick = onLongPress,
        onLongClickLabel = "选择$name"), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(
        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.labelLarge, color = statusColor,
                    modifier = Modifier.widthIn(max = 128.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (selectionMode) Checkbox(selected, { onOpen() }, enabled = !busy) else Box {
                    IconButton({ menu = true }, enabled = !busy) { Icon(Icons.Outlined.MoreVert, "$name 更多操作") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("编辑全部信息") }, { menu = false; onEdit() })
                        DropdownMenuItem({ Text("修改数量") }, { menu = false; onQuantity() })
                        DropdownMenuItem({ Text(if (archived) "恢复到库存" else "归档") }, { menu = false; onArchive() })
                        DropdownMenuItem({ Text("永久删除", color = MaterialTheme.colorScheme.error) }, { menu = false; onDelete() })
                    }
                }
            }
            Text("$quantity · ${location.ifBlank { "位置未填" }}", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (metadata.isNotBlank()) Text(metadata, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (note.isNotBlank()) Text(note, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 两类库存共用；只有事务成功才关闭，错误留在输入旁。 */
@Composable
fun InventoryQuantityDialog(id: Long, name: String, unit: String, quantity: Double, specified: Boolean,
    onDismiss: () -> Unit, onSave: (Double, (Result<Unit>) -> Unit) -> Unit) {
    var value by remember(id) { mutableStateOf(if (specified) formatAmount(quantity) else "") }
    var saving by remember(id) { mutableStateOf(false) }
    var error by remember(id) { mutableStateOf("") }
    val number = value.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("修改数量") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(value, { value = it; error = "" }, label = { Text("数量（$unit）") }, singleLine = true,
                enabled = !saving, isError = error.isNotBlank(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(enabled = !saving && number != null, onClick = {
        number?.let { saving = true; onSave(it) { result ->
            saving = false
            if (result.isSuccess) onDismiss() else error = result.exceptionOrNull()?.message ?: "保存失败，请重试"
        } }
    }) { Text(if (saving) "保存中" else "保存") } }, dismissButton = { TextButton(onDismiss, enabled = !saving) { Text("取消") } })
}

@Composable
fun InventoryDeleteDialog(name: String, busy: Boolean, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("永久删除$name？") },
        text = { Text("删除后无法恢复。暂时不用的记录可以归档。") },
        confirmButton = { TextButton(onDelete, enabled = !busy) { Text("删除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onDismiss, enabled = !busy) { Text("取消") } })
}
