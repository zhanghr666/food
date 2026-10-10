package com.example.foodmanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.foodmanager.FoodApp
import com.example.foodmanager.core.*
import com.example.foodmanager.data.*
import com.example.foodmanager.ui.components.NotificationPermissionCard
import com.example.foodmanager.ui.components.FoodBottomSheet
import com.example.foodmanager.ui.components.FoodSearchField
import com.example.foodmanager.ui.components.FoodSegments
import com.example.foodmanager.ui.components.FoodTabs
import com.example.foodmanager.ui.entry.*
import kotlinx.coroutines.launch

@Composable
fun PantryApp(openAlertsOnStart: Boolean = false, onOpenAlertsHandled: () -> Unit = {}) {
    val repository = (LocalContext.current.applicationContext as FoodApp).repository
    val inventory: InventoryViewModel = viewModel(factory = InventoryViewModel.factory(repository))
    val entry: EntryViewModel = viewModel(factory = EntryViewModel.factory(repository))
    val state by inventory.state.collectAsState()
    val entryState by entry.state.collectAsState()
    val today by inventory.today.collectAsState()
    val message by inventory.message.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("全部") }
    var archived by rememberSaveable { mutableStateOf(false) }
    var pendingEdit by remember { mutableStateOf<FoodItem?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    DateRefreshEffect { inventory.refreshToday() }
    LaunchedEffect(openAlertsOnStart) { if (openAlertsOnStart) {
        tab = 0; archived = false; filter = "需处理"; query = ""; location = ""; onOpenAlertsHandled()
    } }
    LaunchedEffect(entryState.saved) { if (entryState.saved) {
        val savedMessage = entryState.message; tab = 0; archived = false; filter = "全部"; query = ""; location = ""
        snackbar.showSnackbar(savedMessage)
        entry.consumeSaved()
    } }
    LaunchedEffect(message) { if (message.isNotBlank()) { snackbar.showSnackbar(message); inventory.consumeMessage() } }
    BackHandler(tab != 0 || archived) { if (archived) archived = false else tab = 0 }
    fun edit(item: FoodItem) { if (entryState.dirty) pendingEdit = item else { entry.beginEdit(item); tab = 1 } }
    fun archive(item: FoodItem) { inventory.archive(item, true) {
        scope.launch { if (snackbar.showSnackbar("${item.name}已归档", "撤销", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) inventory.archive(item, false) }
    } }
    PantryLayout(tab, entryState.dirty, snackbar, { tab = it }) {
        when (tab) {
            0 -> PantryScreen(state, today, query, { query = it }, location, { location = it }, filter, { filter = it }, archived,
                { archived = !archived }, { tab = 1 }, ::edit, ::archive, { inventory.archive(it, false) }, inventory::quantity, inventory::delete)
            1 -> EntryScreen(entry)
            2 -> LocationScreen(state, { name -> tab = 0; archived = false; location = name; query = ""; filter = "全部" }, inventory)
            3 -> PreferencesScreen(state, inventory, { tab = 0; archived = true })
        }
    }
    pendingEdit?.let { item -> AlertDialog(onDismissRequest = { pendingEdit = null }, title = { Text("还有未保存的录入") }, text = { Text("打开编辑会替换当前草稿，也可以先保存当前录入。") },
        confirmButton = { TextButton(onClick = { entry.beginEdit(item); pendingEdit = null; tab = 1 }) { Text("替换草稿并编辑") } },
        dismissButton = { TextButton(onClick = { pendingEdit = null; tab = 1 }) { Text("返回草稿") } }) }
}

/** Scaffold 的边距先消费，再补键盘剩余边距；录入内容只保留一个滚动视口。 */
@Composable
internal fun PantryLayout(tab: Int, hasDraft: Boolean, snackbar: SnackbarHostState, onTab: (Int) -> Unit,
    content: @Composable () -> Unit) {
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        if (!keyboardVisible) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            listOf("食物" to Icons.Outlined.Kitchen, "录入" to Icons.Outlined.AddCircleOutline, "位置" to Icons.Outlined.Place, "设置" to Icons.Outlined.Tune).forEachIndexed { index, (label, icon) ->
                NavigationBarItem(tab == index, { onTab(index) }, icon = { Icon(icon, label) }, label = { Text(if (index == 1 && hasDraft) "录入 · 草稿" else label) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary))
            }
        }
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()
            .testTag("pantry-content")) { content() }
    }
}

fun FoodItem.dateDescription(today: Long): String {
    val days = daysToExpiry(today) ?: return "未设置日期"
    return if (dateKind == "reminder") when { days < 0 -> "食用计划已过 ${-days} 天"; days == 0L -> "今天计划食用"; else -> "$days 天后计划食用" }
        else DateUtils.describeRemaining(days)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PantryScreen(state: InventoryState, today: Long, query: String, onQuery: (String) -> Unit,
    location: String, onLocation: (String) -> Unit, filter: String, onFilter: (String) -> Unit, archived: Boolean,
    toggleArchive: () -> Unit, onAdd: () -> Unit, onEdit: (FoodItem) -> Unit, onArchive: (FoodItem) -> Unit,
    onRestore: (FoodItem) -> Unit, onQuantity: (FoodItem, Double) -> Unit, onDelete: (FoodItem) -> Unit) {
    var quantityItem by remember { mutableStateOf<FoodItem?>(null) }
    var quantityValue by remember { mutableStateOf("") }
    var deleteItem by remember { mutableStateOf<FoodItem?>(null) }
    var choosingLocation by remember { mutableStateOf(false) }
    var moreFilters by remember { mutableStateOf(false) }
    var locationSort by rememberSaveable { mutableStateOf("从多到少") }
    val locationItems = if (archived) state.archived else state.items
    val locationOptions = (state.locations.map { it.name } + locationItems.map { it.location } + location)
        .filter(String::isNotBlank).distinct()
    val list = (if (archived) state.archived else state.items).filter { item ->
        (query.isBlank() || listOf(item.name, item.category, item.location, item.note, item.brand).any { it.contains(query, true) }) &&
        (location.isBlank() || item.location == location) && (archived || when (filter) {
            "需处理" -> (item.daysToExpiry(today) ?: Long.MAX_VALUE) <= state.settings.warnDays
            "临期" -> item.dateKind == "expiry" && item.status(today, state.settings.warnDays) == FoodStatus.EXPIRING_SOON
            "过期" -> item.dateKind == "expiry" && item.status(today, state.settings.warnDays) == FoodStatus.EXPIRED
            "食用计划" -> item.dateKind == "reminder"; else -> true
        })
    }
    val soon = state.items.count { it.dateKind == "expiry" && it.status(today, state.settings.warnDays) == FoodStatus.EXPIRING_SOON }
    val expired = state.items.count { it.dateKind == "expiry" && it.status(today, state.settings.warnDays) == FoodStatus.EXPIRED }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(if (archived) "已归档" else "我的食物", style = MaterialTheme.typography.headlineMedium); Text("食物管家 · ${DateUtils.formatShort(today)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } }
        if (!archived) {
            item { Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(Triple(state.items.size, "件在库", "全部"), Triple(soon, "件临期", "临期"), Triple(expired, "件过期", "过期")).forEach { (count, label, key) ->
                        TextButton(onClick = { onFilter(key) }, modifier = Modifier.weight(1f)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$count", style = MaterialTheme.typography.headlineMedium,
                                    color = when (key) { "临期" -> MaterialTheme.colorScheme.tertiary; "过期" -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.primary })
                                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
            } }
            item { Button(onClick = onAdd, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.AddCircleOutline, contentDescription = null)
                Spacer(Modifier.width(8.dp)); Text("录入食物")
            } }
        }
        item { FoodSearchField(query, onQuery, "搜索食物、位置或品牌") }
        item {
            if (!archived) FoodTabs(listOf("全部", "临期", "过期", "食用计划"), filter, onFilter)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { choosingLocation = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) {
                    Text(location.ifBlank { "按位置查看" }, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Outlined.ExpandMore, contentDescription = null)
                }
                if (location.isNotBlank()) IconButton(onClick = { onLocation("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = "清除位置筛选")
                }
                Box {
                    TextButton(onClick = { moreFilters = true }) {
                        Text(if (archived) "已归档" else if (filter == "需处理") "需处理" else "更多筛选")
                        Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
                    }
                    DropdownMenu(moreFilters, { moreFilters = false }) {
                        DropdownMenuItem({ Text(if (archived) "返回库存" else "查看已归档") }, { moreFilters = false; toggleArchive() })
                        if (!archived) DropdownMenuItem({ Text("需处理（含食用计划）") }, { moreFilters = false; onFilter("需处理") })
                        DropdownMenuItem({ Text("清除全部筛选") }, { moreFilters = false; onQuery(""); onLocation(""); onFilter("全部"); if (archived) toggleArchive() })
                    }
                }
            }
            Text("共 ${list.size} 件", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!state.loaded) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else if (list.isEmpty()) item { Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Eco, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Text(if (query.isNotBlank() || location.isNotBlank() || filter != "全部") "没有符合条件的食物" else if (archived) "还没有归档记录" else "从第一件食物开始", style = MaterialTheme.typography.titleMedium)
        } }
        items(list, key = { it.id }) { item ->
            var menu by remember { mutableStateOf(false) }
            Card(onClick = { onEdit(item) }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(item.dateDescription(today), modifier = Modifier.weight(1f).padding(start = 8.dp),
                            style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End, color = when {
                                item.expiryDate == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                item.dateKind == "expiry" && item.status(today, state.settings.warnDays) == FoodStatus.EXPIRED -> MaterialTheme.colorScheme.error
                                (item.daysToExpiry(today) ?: Long.MAX_VALUE) <= state.settings.warnDays -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                            })
                        Box { IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "更多操作") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem({ Text("编辑全部信息") }, { menu = false; onEdit(item) })
                                DropdownMenuItem({ Text("修改余量") }, { menu = false; quantityItem = item; quantityValue = if (item.quantitySpecified) formatAmount(item.quantity) else "" })
                                DropdownMenuItem({ Text(if (archived) "恢复到库存" else "已用完 / 归档") }, { menu = false; if (archived) onRestore(item) else onArchive(item) })
                                DropdownMenuItem({ Text("永久删除", color = MaterialTheme.colorScheme.error) }, { menu = false; deleteItem = item })
                            }
                        }
                    }
                    Text("${if (item.quantitySpecified) formatAmount(item.quantity) + item.unit else "数量未填"} · ${item.location.ifBlank { "位置未填" }}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${item.category.ifBlank { "未分类" }} · ${if (item.dateKind == "reminder") "计划" else "到期"} ${DateUtils.format(item.expiryDate)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.brand.isNotBlank() || item.specification.isNotBlank()) Text(listOf(item.brand, item.specification).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    if (item.note.isNotBlank()) Text(item.note, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (choosingLocation) LocationFilterSheet(locationOptions, locationItems, location, locationSort, { locationSort = it },
        onPick = { onLocation(it); choosingLocation = false }, onDismiss = { choosingLocation = false })
    quantityItem?.let { item -> AlertDialog(onDismissRequest = { quantityItem = null }, title = { Text("${item.name}的余量") }, text = {
        OutlinedTextField(quantityValue, { quantityValue = it }, label = { Text("数量（${item.unit}）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    }, confirmButton = { TextButton(onClick = { quantityValue.toDoubleOrNull()?.let { onQuantity(item, it) }; quantityItem = null }, enabled = quantityValue.toDoubleOrNull()?.let { it.isFinite() && it >= 0 } == true) { Text("保存") } }, dismissButton = { TextButton(onClick = { quantityItem = null }) { Text("取消") } }) }
    deleteItem?.let { item -> AlertDialog(onDismissRequest = { deleteItem = null }, title = { Text("永久删除${item.name}？") }, text = { Text("此操作无法撤销，也可以选择归档。") }, confirmButton = { TextButton(onClick = { onDelete(item); deleteItem = null }) { Text("删除") } }, dismissButton = { TextButton(onClick = { deleteItem = null }) { Text("取消") } }) }
}

@Composable
private fun LocationFilterSheet(options: List<String>, items: List<FoodItem>, current: String, sort: String, onSort: (String) -> Unit,
    onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    val counts = items.groupingBy { it.location }.eachCount()
    val filtered = options.filter { search.isBlank() || it.contains(search.trim(), ignoreCase = true) }
    val matching = when (sort) {
        "从多到少" -> filtered.sortedByDescending { counts[it] ?: 0 }
        "从少到多" -> filtered.sortedBy { counts[it] ?: 0 }
        else -> filtered
    }
    val listState = rememberLazyListState()
    LaunchedEffect(sort, search) { listState.scrollToItem(0) }
    FoodBottomSheet(onDismiss = onDismiss) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("按位置查看", style = MaterialTheme.typography.titleLarge)
                Text("选择位置，查看这里的食物", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "关闭位置选择") }
        }
        Spacer(Modifier.height(16.dp))
        FoodSearchField(search, { search = it }, "搜索存放位置")
        Spacer(Modifier.height(12.dp))
        LocationChoiceRow("全部位置", items.size, current.isBlank()) { onPick("") }
        Text(if (search.isBlank()) "存放位置 · ${options.size}" else "搜索结果 · ${matching.size}",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        FoodSegments(listOf("从多到少", "从少到多", "不排序").map { it to it }, sort, onSort)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 420.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 4.dp)) {
            if (matching.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (options.isEmpty()) "还没有存放位置" else "没有找到这个位置", style = MaterialTheme.typography.bodyMedium)
                    Text(if (options.isEmpty()) "录入食物时可以直接填写" else "试试输入位置名称的一部分", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
            items(matching, key = { it }) { name ->
                LocationChoiceRow(name, counts[name] ?: 0, current == name) { onPick(name) }
            }
        }
    }
}

@Composable
private fun LocationChoiceRow(name: String, count: Int, selected: Boolean, onPick: () -> Unit) {
    Card(onClick = onPick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("$count 件", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            if (selected) Icon(Icons.Outlined.CheckCircle, contentDescription = "已选择", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun LocationScreen(state: InventoryState, onOpen: (String) -> Unit, vm: InventoryViewModel) {
    var adding by remember { mutableStateOf(false) }; var name by remember { mutableStateOf("") }
    var editingAlias by remember { mutableStateOf<StorageLocation?>(null) }; var alias by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf<StorageLocation?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("存放位置", style = MaterialTheme.typography.titleLarge); TextButton(onClick = { adding = true }) { Text("＋ 新位置") } } }
        item { Text("按实际位置管理，也可以为语音设置顺口的别名。", style = MaterialTheme.typography.bodyMedium) }
        items((state.locations.map { it.name } + state.items.map { it.location }).filter(String::isNotBlank).distinct(), key = { it }) { location ->
            val saved = state.locations.firstOrNull { it.name == location }
            Card(onClick = { onOpen(location) }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { Text(location, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); Text("${state.items.count { it.location == location }} 件", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                    if (saved != null) { Text("语音别名：${saved.alias.ifBlank { "未设置" }}", style = MaterialTheme.typography.bodySmall)
                        Row { TextButton(onClick = { editingAlias = saved; alias = saved.alias }) { Text("设置别名") }; TextButton(onClick = { removing = saved }) { Text("移除位置选项") } }
                    } else Text("录入时填写的位置", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (adding) AlertDialog(onDismissRequest = { adding = false }, title = { Text("新增位置") }, text = { OutlinedTextField(name, { name = it }, label = { Text("例如：餐桌、厨房抽屉") }, singleLine = true) }, confirmButton = { TextButton(onClick = { vm.addLocation(name); name = ""; adding = false }, enabled = name.isNotBlank()) { Text("添加") } }, dismissButton = { TextButton(onClick = { adding = false }) { Text("取消") } })
    editingAlias?.let { loc -> AlertDialog(onDismissRequest = { editingAlias = null }, title = { Text("${loc.name}的别名") }, text = { OutlinedTextField(alias, { alias = it }, label = { Text("多个别名用逗号分开") }) }, confirmButton = { TextButton(onClick = { vm.alias(loc.id, alias); editingAlias = null }) { Text("保存") } }, dismissButton = { TextButton(onClick = { editingAlias = null }) { Text("取消") } }) }
    removing?.let { loc -> AlertDialog(onDismissRequest = { removing = null }, title = { Text("移除${loc.name}的位置选项？") }, text = { Text("已有食物仍保留这个位置，库存记录不会删除。") }, confirmButton = { TextButton(onClick = { vm.removeLocation(loc.id); removing = null }) { Text("移除") } }, dismissButton = { TextButton(onClick = { removing = null }) { Text("取消") } }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreferencesScreen(state: InventoryState, vm: InventoryViewModel, onArchived: () -> Unit) {
    val settings = state.settings
    val foods by vm.commonFoods.collectAsState()
    var editFood by remember { mutableStateOf<CommonFood?>(null) }
    var alias by remember { mutableStateOf("") }
    var removeFood by remember { mutableStateOf<CommonFood?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("设置", style = MaterialTheme.typography.titleLarge)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("完全离线 · 数据留在手机", style = MaterialTheme.typography.titleMedium); Text("语音、识物、包装文字与库存都在本机处理，首次启动也无需下载识别模型。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("系统通知提醒", Modifier.weight(1f)); Switch(settings.notifyEnabled, { value -> vm.settings { it.copy(notifyEnabled = value) } }) }
        NotificationPermissionCard()
        Text("包装到期日前提醒", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(1, 3, 7).forEach { days -> FilterChip(settings.warnDays == days, { vm.settings { it.copy(warnDays = days) } }, label = { Text("提前 $days 天") }) } }
        Text("每日检查时间", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(8, 9, 12, 18, 21).forEach { hour -> FilterChip(settings.notifyHour == hour, { vm.settings { it.copy(notifyHour = hour) } }, label = { Text("$hour:00") }) } }
        Text("系统可能因省电或后台限制延迟提醒，请同时查看库存中的日期状态。食用计划与包装过期通知分开显示。", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onArchived, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("查看已归档食物") }
        Text("常用食品", style = MaterialTheme.typography.titleMedium)
        Text("保存食物时可记住稳定信息；数量和日期每次重新设置。", style = MaterialTheme.typography.bodySmall)
        if (foods.isEmpty()) Text("保存第一件食物后会出现在这里。", style = MaterialTheme.typography.bodySmall)
        foods.forEach { food -> Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text(food.name, style = MaterialTheme.typography.titleMedium)
                Text(listOf(food.category, food.unit, food.location).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                Text("语音别名：${food.alias.ifBlank { "未设置" }}", style = MaterialTheme.typography.bodySmall)
                Row { TextButton(onClick = { editFood = food; alias = food.alias }) { Text("设置别名") }; TextButton(onClick = { removeFood = food }) { Text("移除常用") } }
            }
        } }
        Text("识物范围", style = MaterialTheme.typography.titleMedium)
        Text("当前视觉模型支持苹果、香蕉、橙子、西兰花、胡萝卜及部分主食零食。包装食品同时读取文字；无法确定的信息会留空或提示核对。", style = MaterialTheme.typography.bodySmall)
        Text("食物管家", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    editFood?.let { food -> AlertDialog(onDismissRequest = { editFood = null }, title = { Text("${food.name}的别名") }, text = { OutlinedTextField(alias, { alias = it }, label = { Text("多个别名用逗号分开") }) },
        confirmButton = { TextButton(onClick = { vm.foodAlias(food.id, alias); editFood = null }) { Text("保存") } }, dismissButton = { TextButton(onClick = { editFood = null }) { Text("取消") } }) }
    removeFood?.let { food -> AlertDialog(onDismissRequest = { removeFood = null }, title = { Text("移除常用食品${food.name}？") }, text = { Text("库存中的食物记录不会受影响。") },
        confirmButton = { TextButton(onClick = { vm.removeCommonFood(food.id); removeFood = null }) { Text("移除") } }, dismissButton = { TextButton(onClick = { removeFood = null }) { Text("取消") } }) }
}
