package com.example.foodmanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.example.foodmanager.core.*
import com.example.foodmanager.data.*
import com.example.foodmanager.ui.components.*

@Composable
fun ObjectInventoryScreen(state: HouseholdState, onAdd: () -> Unit, onOpen: (ManagedItem) -> Unit, today: Long,
    onEdit: (ManagedItem) -> Unit, onArchive: (ManagedItem) -> Unit,
    onQuantity: (ManagedItem, Double, (Result<Unit>) -> Unit) -> Unit, onDelete: (ManagedItem) -> Unit, busy: Boolean, onSearch: () -> Unit, onReminders: () -> Unit, onBatch: (List<Long>, Boolean, String, String, (Result<Unit>) -> Unit) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var archived by rememberSaveable { mutableStateOf(false) }
    var location by rememberSaveable { mutableStateOf("") }
    var order by rememberSaveable { mutableStateOf("最近录入") }
    var picking by remember { mutableStateOf(false) }
    var moreFilters by remember { mutableStateOf(false) }
    var quantityItem by remember { mutableStateOf<ManagedItem?>(null) }
    var deleteItem by remember { mutableStateOf<ManagedItem?>(null) }
    val records = state.records.filter { it.kind == "object" && it.archived == archived }
    val activeRecords = state.records.filter { it.kind == "object" && !it.archived }
    val locationCount = activeRecords.map { it.location.trim() }.filter(String::isNotBlank).distinct().size
    val list = InventoryOrder.objects(records.filter { HouseholdSearch.matches(it, search, state.locations) && (location.isBlank() || HouseholdSearch.placeMatches(it.location, location, state.locations)) }, order).let { sorted -> if (search.isBlank()) sorted else sorted.sortedBy { HouseholdSearch.rank(it, search, state.locations) } }
    val reminderCount = state.reminders.count { !it.completed && it.itemId in activeRecords.map { record -> record.id } }
    val photoCounts = state.photos.groupingBy { it.itemId }.eachCount()
    InventoryBatchLayout(list.map { it.id }, listOf(search, location, archived.toString()), archived, busy, state.locations,
        { ids, action, target, done -> onBatch(ids, archived, action, target, done) }) { selectionMode, selected, toggle, select ->
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { InventoryHeading(if (archived) "已归档物品" else "我的物品", today, onSearch, onReminders) }
        if (!archived) item { InventoryStats(listOf(
            InventoryStat("${activeRecords.size}", "件在库", MaterialTheme.colorScheme.primary) { search = ""; location = "" },
            InventoryStat("$locationCount", "个地点", MaterialTheme.colorScheme.primary) { picking = true },
            InventoryStat("$reminderCount", "项待处理提醒", MaterialTheme.colorScheme.primary, onReminders))) }
        item { InventorySearchActions(search, { search = it }, "搜索名称、位置", "录入物品", onAdd) }
        item {
            FoodTabs(listOf("在库", "已归档"), if (archived) "已归档" else "在库", { archived = it == "已归档"; location = "" })
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton({ picking = true }, Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text(location.ifBlank { "按位置查看" }, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
                }
                if (location.isNotBlank()) IconButton({ location = "" }) { Icon(Icons.Outlined.Close, "清除位置筛选", Modifier.size(18.dp)) }
                Box {
                    TextButton({ moreFilters = true }) { Text(if (archived) "已归档" else "筛选"); Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp)) }
                    DropdownMenu(moreFilters, { moreFilters = false }) {
                        DropdownMenuItem({ Text(if (archived) "返回库存" else "查看已归档") }, { moreFilters = false; archived = !archived; location = "" })
                        DropdownMenuItem({ Text("清除全部筛选") }, { moreFilters = false; search = ""; location = ""; archived = false })
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("共 ${list.size} 件", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                InventorySort(order, InventoryOrder.objectOptions, { order = it })
            }
        }
        if (!state.loaded) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else if (list.isEmpty()) item { EmptyRecords(if (search.isNotBlank() || location.isNotBlank()) "没有符合条件的物品" else if (archived) "还没有归档物品" else "记下物品，下次好找") }
        items(list, key = { it.id }) { item ->
            val reminder = state.reminders.firstOrNull { it.itemId == item.id && !it.completed }
            val photos = photoCounts[item.id] ?: 0
            val metadata = listOfNotNull(item.category.takeIf { it.isNotBlank() && it != "未分类" },
                item.specification.takeIf(String::isNotBlank), if (photos > 0) "照片 $photos 张" else null).joinToString(" · ")
            InventoryRecordCard(item.name, if (item.quantitySpecified) formatAmount(item.quantity) + item.unit else "数量未填",
                item.location, metadata, DetailNotes.from(item.note).user, status = if (archived) "已归档" else reminder?.let { if (it.due(today, 0)) "提醒待处理" else "${DateUtils.formatShort(it.checkDate(0))}提醒" }.orEmpty(),
                statusColor = MaterialTheme.colorScheme.onSurfaceVariant, archived = archived, busy = busy,
                onOpen = { if (selectionMode) toggle(item.id) else onOpen(item) }, onEdit = { onEdit(item) }, onQuantity = { quantityItem = item },
                onArchive = { onArchive(item) }, onDelete = { deleteItem = item },
                selectionMode = selectionMode, selected = item.id in selected, onLongPress = { select(item.id) })
        }
    }
    }
    if (picking) RecordLocationPicker(records, location, { location = it; picking = false }, { picking = false }, state.locations)
    quantityItem?.let { item -> InventoryQuantityDialog(item.id, item.name, item.unit, item.quantity, item.quantitySpecified,
        { quantityItem = null }, { value, done -> onQuantity(item, value, done) }) }
    deleteItem?.let { item -> InventoryDeleteDialog(item.name, busy, { deleteItem = null }, { onDelete(item); deleteItem = null }) }
}

@Composable
fun LocationContentsScreen(state: HouseholdState, location: String, initialKind: String, onBack: () -> Unit, onOpen: (ManagedItem) -> Unit) {
    var kind by rememberSaveable(location, initialKind) { mutableStateOf(initialKind) }; var search by rememberSaveable(location) { mutableStateOf("") }
    val records = state.records.filter { !it.archived && HouseholdSearch.placeMatches(it.location, location, state.locations) && HouseholdSearch.matches(it, search, state.locations) && (kind == "全部" || it.kind == if (kind == "食品") "food" else "object") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回位置") }; Text(location, style = MaterialTheme.typography.titleLarge) } }
        item { FoodTabs(listOf("全部", "食品", "物品"), kind, { kind = it }) }
        item { FoodSearchField(search, { search = it }, "搜索这里的食品和物品") }
        item { Text("共 ${records.size} 条在库记录", style = MaterialTheme.typography.bodySmall) }
        if (records.isEmpty()) item { EmptyRecords("这个位置暂无符合条件的记录") }
        items(records, key = { it.id }) { RecordCard(it, state.photoKeys(it.id).size, true) { onOpen(it) } }
    }
}

private fun matches(item: ManagedItem, search: String) = search.isBlank() || listOf(item.name, item.location, item.category, DetailNotes.from(item.note).user, item.brand, item.specification).any { it.contains(search.trim(), true) }

@Composable
private fun EmptyRecords(message: String) { Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Icon(Icons.Outlined.Inventory2, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(12.dp)); Text(message, style = MaterialTheme.typography.bodyMedium)
} }

@Composable
private fun RecordCard(item: ManagedItem, photos: Int, typeLabel: Boolean = false, onClick: () -> Unit) {
    Card(onClick, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(item.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("${formatAmount(item.quantity)}${item.unit} · ${item.location}", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val tags = listOfNotNull(if (typeLabel) if (item.kind == "food") "食品" else "物品" else null,
                item.category.takeIf { it.isNotBlank() && it != "未分类" }, if (photos > 0) "照片 $photos 张" else null, item.specification.takeIf(String::isNotBlank))
            if (tags.isNotEmpty()) Text(tags.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun RecordLocationPicker(records: List<ManagedItem>, selected: String, onPick: (String) -> Unit, onClose: () -> Unit, locations: List<StorageLocation> = emptyList()) {
    var sort by rememberSaveable { mutableStateOf("从多到少") }
    val counts = records.filter { it.location.isNotBlank() }.groupingBy { it.location }.eachCount()
    InventoryLocationPicker(counts.keys.toList(), counts, records.size, selected, sort, { sort = it }, onPick, onClose, locations.filter { it.sortOrder < 0 }.map { it.name })
}
