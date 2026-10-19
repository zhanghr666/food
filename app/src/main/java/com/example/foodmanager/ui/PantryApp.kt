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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
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
import com.example.foodmanager.ui.components.EntryExitDialog
import com.example.foodmanager.ui.components.*
import com.example.foodmanager.ui.entry.*
import kotlinx.coroutines.launch

private data class PantryRoute(val route: String, val tab: Int, val detailId: Long, val locationName: String, val locationKind: String) : java.io.Serializable

@Composable
fun PantryApp(openAlertsOnStart: Boolean = false, onOpenAlertsHandled: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as FoodApp
    val repository = app.repository
    val inventory: InventoryViewModel = viewModel(factory = InventoryViewModel.factory(repository))
    val entry: EntryViewModel = viewModel(factory = EntryViewModel.factory(repository))
    val objectEntry: ObjectEntryViewModel = viewModel(factory = ObjectEntryViewModel.factory(app.items, repository))
    val household: HouseholdViewModel = viewModel(factory = HouseholdViewModel.factory(app.items))
    val state by inventory.state.collectAsState()
    val entryState by entry.state.collectAsState()
    val objects by objectEntry.state.collectAsState()
    val home by household.state.collectAsState()
    val actionBusy by household.busy.collectAsState()
    val today by inventory.today.collectAsState()
    val message by inventory.message.collectAsState()
    val itemMessage by household.message.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    var route by rememberSaveable { mutableStateOf("") }
    var history by rememberSaveable { mutableStateOf(emptyList<PantryRoute>()) }
    var editPhotos by rememberSaveable { mutableStateOf(false) }
    var pendingPhotos by rememberSaveable { mutableStateOf(false) }
    var editingExisting by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableLongStateOf(0L) }
    var locationKind by rememberSaveable { mutableStateOf("全部") }
    var locationName by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("全部") }
    var archived by rememberSaveable { mutableStateOf(false) }
    var exitEntry by rememberSaveable { mutableStateOf(false) }
    var exitTab by rememberSaveable { mutableStateOf<Int?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val pages = rememberSaveableStateHolder()
    fun pushRoute() { history = history + PantryRoute(route, tab, detailId, locationName, locationKind) }
    fun back() {
        val previous = history.lastOrNull()
        if (previous != null) { history = history.dropLast(1); route = previous.route; tab = previous.tab; detailId = previous.detailId; locationName = previous.locationName; locationKind = previous.locationKind }
        else if (route.isNotBlank()) route = "" else if (archived) archived = false else tab = 0
    }
    fun entryRoute(value: String, nextTab: Int = tab) { pushRoute(); editPhotos = false; editingExisting = false; route = value; tab = nextTab }
    fun locationRoute(name: String, kind: String) { pushRoute(); locationName = name; locationKind = kind; route = "location-items" }
    fun leaveEntry() {
        entry.cancelVoice(); objectEntry.cancelVoice()
        if (route == "food-entry") entry.clear() else if (route == "object-entry") objectEntry.clear()
        val next = exitTab
        exitEntry = false; exitTab = null
        if (next == null) back() else { history = emptyList(); tab = next; route = "" }
    }
    fun requestEntryExit(nextTab: Int? = null) {
        val food = route == "food-entry"
        val busy = if (food) entryState.saving || (entryState.busy && !entryState.recording) else objects.saving || (objects.busy && !objects.recording)
        if (busy) return
        focusManager.clearFocus()
        if (food) entry.cancelVoice() else objectEntry.cancelVoice()
        exitTab = nextTab
        if (if (food) entry.hasUnsavedChanges() else objectEntry.hasUnsavedChanges()) exitEntry = true else leaveEntry()
    }
    DateRefreshEffect { inventory.refreshToday() }
    // 进程重建后只恢复页面导航，不恢复表单；避免空表单被当作原记录的编辑。
    LaunchedEffect(route, editingExisting, entryState.current.itemId, entryState.saved, objects.draft.id, objects.saved) {
        if (editingExisting && (route == "food-entry" && entryState.current.itemId == 0L && !entryState.saved && !entryState.saving ||
                route == "object-entry" && objects.draft.id == 0L && !objects.saved && !objects.saving)) back()
    }
    LaunchedEffect(openAlertsOnStart) { if (openAlertsOnStart) {
        if (route == "food-entry" || route == "object-entry") {
            onOpenAlertsHandled(); snackbar.showSnackbar("请先保存或取消当前录入，再打开提醒清单")
        } else { history = emptyList(); tab = 0; route = "reminders"; onOpenAlertsHandled() }
    } }
    LaunchedEffect(entryState.saved) { if (entryState.saved) {
        val savedMessage = entryState.message
        if (editingExisting) back() else { history = emptyList(); tab = 0; route = ""; archived = false; filter = "全部"; query = ""; location = "" }
        entry.consumeSaved(); snackbar.showSnackbar(savedMessage)
    } }
    LaunchedEffect(objects.saved) { if (objects.saved) {
        val savedMessage = objects.message
        if (editingExisting) back() else { history = emptyList(); tab = 1; route = "" }
        objectEntry.consumeSaved(); snackbar.showSnackbar(savedMessage)
    } }
    LaunchedEffect(message) { if (message.isNotBlank()) { inventory.consumeMessage(); snackbar.showSnackbar(message) } }
    LaunchedEffect(itemMessage) { if (itemMessage.isNotBlank()) { household.consumeMessage(); snackbar.showSnackbar(itemMessage) } }
    BackHandler(route.isNotBlank() || tab != 0 || archived) {
        if (route == "food-entry" || route == "object-entry") requestEntryExit()
        else if (!(route == "detail" && actionBusy)) back()
    }
    fun doEdit(item: ManagedItem) {
        if (item.kind == "food") {
            val food = (state.items + state.archived).firstOrNull { it.id == item.id } ?: return
            entry.beginEdit(food); entryRoute("food-entry", 0)
        } else { objectEntry.beginEdit(item); entryRoute("object-entry", 1) }
        editPhotos = pendingPhotos
        editingExisting = true
    }
    fun edit(item: ManagedItem, photos: Boolean = false) {
        if (item.kind == "food" && (entryState.saving || entryState.busy) || item.kind == "object" && (objects.saving || objects.busy)) return
        pendingPhotos = photos
        doEdit(item)
    }
    fun open(item: ManagedItem) {
        pushRoute(); detailId = item.id; route = "detail"
    }
    fun addFood() { if (entry.beginNew()) entryRoute("food-entry", 0) }
    fun addObject() { if (objectEntry.beginNew()) entryRoute("object-entry", 1) }
    fun archiveRecord(item: ManagedItem) {
        household.setArchived(item, !item.archived) {
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val result = snackbar.showSnackbar(if (item.archived) "已恢复「${item.name}」" else "已归档「${item.name}」", if (item.archived) null else "撤销", duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) household.setArchived(item, false) { scope.launch { snackbar.showSnackbar("已恢复库存") } }
            }
        }
    }
    PantryLayout(tab, snackbar, { next ->
        if (route == "food-entry" || route == "object-entry") requestEntryExit(next)
        else { entry.cancelVoice(); objectEntry.cancelVoice(); history = emptyList(); tab = next; route = "" }
    }, showNavigation = route != "detail") {
        when (route) {
            "search" -> GlobalSearchScreen(home, ::back, ::open)
            "reminders" -> ReminderCenter(home, today, state.settings.warnDays, actionBusy, ::back, ::open, household::complete, household::snooze)
            "food-entry" -> EntryScreen(entry, focusPhotos = editPhotos, onBack = { requestEntryExit() })
            "object-entry" -> ObjectEntryScreen(objectEntry, focusPhotos = editPhotos, onBack = { requestEntryExit() })
            "location-items" -> pages.SaveableStateProvider("location:$locationName:$locationKind") { LocationContentsScreen(home, locationName, locationKind, ::back, ::open) }
            "detail" -> {
                val item = home.records.firstOrNull { it.id == detailId }
                val detailBusy = actionBusy || (entryState.current.itemId == detailId && (entryState.busy || entryState.saving)) || (objects.draft.id == detailId && (objects.busy || objects.saving))
                pages.SaveableStateProvider("detail:$detailId") {
                    ItemDetailScreen(item, (state.items + state.archived).firstOrNull { it.id == detailId }, home.photoKeys(detailId), app.items.photoStore, today, detailBusy,
                        ::back, { item?.let { edit(it) } }, { item?.let(::archiveRecord) }, { item?.let { household.delete(it) { back() } } },
                        { item?.let { locationRoute(it.location, "全部") } }, { item?.let { edit(it, photos = true) } }, state.locations.map { it.name }, state.settings.warnDays,
                        { value, done -> item?.let { record -> household.quantity(record, value) { result ->
                            if (result.isSuccess) {
                                entry.syncRecord(record.id, EntryField.QUANTITY, formatAmount(value))
                                objectEntry.syncRecord(record.id, "quantity", formatAmount(value))
                            }; done(result)
                        } } },
                        { value, done -> item?.let { record -> household.location(record, value) { result ->
                            if (result.isSuccess) {
                                entry.syncRecord(record.id, EntryField.LOCATION, value.trim())
                                objectEntry.syncRecord(record.id, "location", value.trim())
                            }; done(result)
                        } } },
                        { value, done -> item?.let { record -> household.date(record, value) { result ->
                            if (result.isSuccess) entry.syncRecordDate(record.id, value)
                            done(result)
                        } } }, loading = !home.loaded, reminderContent = {
                            item?.let { record -> ItemReminderSection(record, home.reminders.firstOrNull { it.itemId == record.id }, today, state.settings.warnDays, detailBusy,
                                { title, date, done -> household.reminder(record.id, title, date, done) },
                                { done -> household.removeReminder(record.id, done) },
                                { household.complete(record.id, home.reminders.firstOrNull { it.itemId == record.id }?.completed != true) },
                                { household.snooze(record.id, it) }) }
                        })
                }
            }
            else -> pages.SaveableStateProvider("home:$tab") { when (tab) {
                0 -> PantryScreen(state, today, query, { query = it }, location, { location = it }, filter, { filter = it }, archived,
                    { archived = !archived }, ::addFood, { edit(it.common()) }, { archiveRecord(it.common()) }, { archiveRecord(it.common()) }, { item, value, done -> household.quantity(item.common(), value, done) }, { household.delete(it.common()) {} },
                    onOpen = { open(it.common()) }, photoCounts = home.photos.groupingBy { it.itemId }.eachCount(), actionBusy = actionBusy, onSearch = { pushRoute(); route = "search" }, onReminders = { pushRoute(); route = "reminders" }, onBatch = { ids, oldArchived, action, target, done -> household.batch(ids, "food", oldArchived, action, target, done) })
                1 -> ObjectInventoryScreen(home, ::addObject, ::open, today, { edit(it) }, ::archiveRecord,
                    { item, value, done -> household.quantity(item, value, done) }, { household.delete(it) {} }, actionBusy, { pushRoute(); route = "search" }, { pushRoute(); route = "reminders" },
                    { ids, oldArchived, action, target, done -> household.batch(ids, "object", oldArchived, action, target, done) })
                2 -> LocationScreen(state, home, ::locationRoute, inventory, household)
                3 -> PreferencesScreen(state, inventory, { history = emptyList(); tab = 0; archived = true; route = "" })
            } }
        }
    }
    if (exitEntry) {
        val food = route == "food-entry"
        val saving = if (food) entryState.saving || entryState.busy else objects.saving || objects.busy
        EntryExitDialog(editing = editingExisting, busy = saving,
            onContinue = { exitEntry = false; exitTab = null },
            onDiscard = ::leaveEntry)
    }
}

/** Scaffold 的边距先消费，再补键盘剩余边距；录入内容只保留一个滚动视口。 */
@Composable
internal fun PantryLayout(tab: Int, snackbar: SnackbarHostState, onTab: (Int) -> Unit,
    showNavigation: Boolean = true, content: @Composable () -> Unit) {
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        if (showNavigation && !keyboardVisible) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            listOf("食物" to Icons.Outlined.Kitchen, "物品" to Icons.Outlined.Inventory2, "位置" to Icons.Outlined.Place, "设置" to Icons.Outlined.Tune).forEachIndexed { index, (label, icon) ->
                NavigationBarItem(tab == index, { onTab(index) }, icon = { Icon(icon, label) }, label = { Text(label) },
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
    onRestore: (FoodItem) -> Unit, onQuantity: (FoodItem, Double, (Result<Unit>) -> Unit) -> Unit, onDelete: (FoodItem) -> Unit, onOpen: (FoodItem) -> Unit = onEdit, photoCounts: Map<Long, Int> = emptyMap(), actionBusy: Boolean = false, onSearch: () -> Unit, onReminders: () -> Unit, onBatch: (List<Long>, Boolean, String, String, (Result<Unit>) -> Unit) -> Unit) {
    var quantityItem by remember { mutableStateOf<FoodItem?>(null) }
    var deleteItem by remember { mutableStateOf<FoodItem?>(null) }
    var choosingLocation by remember { mutableStateOf(false) }
    var moreFilters by remember { mutableStateOf(false) }
    var locationSort by rememberSaveable { mutableStateOf("从多到少") }
    val locationItems = if (archived) state.archived else state.items
    val locationOptions = (state.locations.map { it.name } + locationItems.map { it.location } + location)
        .filter(String::isNotBlank).distinct()
    var order by rememberSaveable { mutableStateOf("最近录入") }
    val filtered = (if (archived) state.archived else state.items).filter { item ->
        HouseholdSearch.matches(item.common(), query, state.locations) &&
        (location.isBlank() || HouseholdSearch.placeMatches(item.location, location, state.locations)) && (archived || when (filter) {
            "需处理" -> (item.daysToExpiry(today) ?: Long.MAX_VALUE) <= state.settings.warnDays
            "临期" -> item.dateKind == "expiry" && item.status(today, state.settings.warnDays) == FoodStatus.EXPIRING_SOON
            "过期" -> item.dateKind == "expiry" && item.status(today, state.settings.warnDays) == FoodStatus.EXPIRED
            "食用计划" -> item.dateKind == "reminder"; else -> true
        })
    }
    val list = InventoryOrder.foods(filtered, order).let { sorted -> if (query.isBlank()) sorted else sorted.sortedBy { HouseholdSearch.rank(it.common(), query, state.locations) } }
    val soon = state.items.count { it.dateKind == "expiry" && it.status(today, state.settings.warnDays) == FoodStatus.EXPIRING_SOON }
    val expired = state.items.count { it.dateKind == "expiry" && it.status(today, state.settings.warnDays) == FoodStatus.EXPIRED }
    InventoryBatchLayout(list.map { it.id }, listOf(query, location, filter, archived.toString()), archived, actionBusy, state.locations,
        { ids, action, target, done -> onBatch(ids, archived, action, target, done) }) { selectionMode, selected, toggle, select ->
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { InventoryHeading(if (archived) "已归档食物" else "我的食物", today, onSearch, onReminders) }
        if (!archived) item { InventoryStats(listOf(
            InventoryStat("${state.items.size}", "件在库", MaterialTheme.colorScheme.primary) { onFilter("全部"); onQuery(""); onLocation("") },
            InventoryStat("$soon", "件临期", MaterialTheme.colorScheme.tertiary) { onFilter("临期") },
            InventoryStat("$expired", "件过期", MaterialTheme.colorScheme.error) { onFilter("过期") })) }
        item { InventorySearchActions(query, onQuery, "搜索名称、位置", "录入食物", onAdd) }
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
                        Text(if (archived) "已归档" else if (filter == "需处理") "需处理" else "筛选")
                        Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
                    }
                    DropdownMenu(moreFilters, { moreFilters = false }) {
                        DropdownMenuItem({ Text(if (archived) "返回库存" else "查看已归档") }, { moreFilters = false; toggleArchive() })
                        if (!archived) DropdownMenuItem({ Text("需处理（含食用计划）") }, { moreFilters = false; onFilter("需处理") })
                        DropdownMenuItem({ Text("清除全部筛选") }, { moreFilters = false; onQuery(""); onLocation(""); onFilter("全部"); if (archived) toggleArchive() })
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("共 ${list.size} 件", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                InventorySort(order, InventoryOrder.foodOptions, { order = it })
            }
        }
        if (!state.loaded) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else if (list.isEmpty()) item { Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Eco, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Text(if (query.isNotBlank() || location.isNotBlank() || filter != "全部") "没有符合条件的食物" else if (archived) "还没有归档记录" else "从第一件食物开始", style = MaterialTheme.typography.titleMedium)
        } }
        items(list, key = { it.id }) { item ->
            val photos = photoCounts[item.id] ?: 0
            val metadata = listOfNotNull(item.category.ifBlank { "未分类" },
                item.expiryDate?.let { "${if (item.dateKind == "reminder") "计划" else "到期"} ${DateUtils.format(it)}" },
                if (photos > 0) "照片 $photos 张" else null).joinToString(" · ")
            InventoryRecordCard(item.name, if (item.quantitySpecified) formatAmount(item.quantity) + item.unit else "数量未填",
                item.location, metadata, DetailNotes.from(item.note).user,
                status = if (archived) "已归档" else item.dateDescription(today), statusColor = when {
                    archived || item.expiryDate == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    item.dateKind == "expiry" && item.status(today, state.settings.warnDays) == FoodStatus.EXPIRED -> MaterialTheme.colorScheme.error
                    (item.daysToExpiry(today) ?: Long.MAX_VALUE) <= state.settings.warnDays -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                }, archived = archived, busy = actionBusy, onOpen = { if (selectionMode) toggle(item.id) else onOpen(item) }, onEdit = { onEdit(item) },
                onQuantity = { quantityItem = item }, onArchive = { if (archived) onRestore(item) else onArchive(item) }, onDelete = { deleteItem = item },
                selectionMode = selectionMode, selected = item.id in selected, onLongPress = { select(item.id) })
        }
    }
    }
    if (choosingLocation) InventoryLocationPicker(locationOptions, locationItems.groupingBy { it.location }.eachCount(), locationItems.size, location, locationSort, { locationSort = it },
        onPick = { onLocation(it); choosingLocation = false }, onDismiss = { choosingLocation = false }, pinned = state.locations.filter { it.sortOrder < 0 }.map { it.name })
    quantityItem?.let { item -> InventoryQuantityDialog(item.id, item.name, item.unit, item.quantity, item.quantitySpecified,
        { quantityItem = null }, { value, done -> onQuantity(item, value, done) }) }
    deleteItem?.let { item -> InventoryDeleteDialog(item.name, actionBusy, { deleteItem = null }, { onDelete(item); deleteItem = null }) }
}

@Composable
internal fun InventoryLocationPicker(options: List<String>, counts: Map<String, Int>, total: Int, current: String, sort: String, onSort: (String) -> Unit,
    onPick: (String) -> Unit, onDismiss: () -> Unit, pinned: List<String> = emptyList()) {
    var search by rememberSaveable { mutableStateOf("") }
    val filtered = options.filter { search.isBlank() || it.contains(search.trim(), ignoreCase = true) }
    val matching = when (sort) {
        "从多到少" -> filtered.sortedByDescending { counts[it] ?: 0 }
        "从少到多" -> filtered.sortedBy { counts[it] ?: 0 }
        else -> filtered
    }.sortedBy { if (it in pinned) pinned.indexOf(it) else Int.MAX_VALUE }
    val listState = rememberLazyListState()
    LaunchedEffect(sort, search) { listState.scrollToItem(0) }
    FoodBottomSheet(onDismiss = onDismiss) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("按位置查看", style = MaterialTheme.typography.titleLarge)
                Text("选择位置，查看这里的库存", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "关闭位置选择") }
        }
        Spacer(Modifier.height(16.dp))
        FoodSearchField(search, { search = it }, "搜索存放位置")
        Spacer(Modifier.height(12.dp))
        LocationChoiceRow("全部位置", total, current.isBlank()) { onPick("") }
        Text(if (search.isBlank()) "存放位置 · ${options.size}" else "搜索结果 · ${matching.size}",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        if (pinned.isNotEmpty()) Text("常用置顶优先，其余按所选方式排序", style = MaterialTheme.typography.bodySmall)
        FoodSegments(listOf("从多到少", "从少到多", "不排序").map { it to it }, sort, onSort)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 420.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 4.dp)) {
            if (matching.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (options.isEmpty()) "还没有存放位置" else "没有找到这个位置", style = MaterialTheme.typography.bodyMedium)
                    Text(if (options.isEmpty()) "录入时可以直接填写" else "试试输入位置名称的一部分", style = MaterialTheme.typography.bodySmall,
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
private fun LocationScreen(state: InventoryState, home: HouseholdState, onOpen: (String, String) -> Unit, vm: InventoryViewModel, household: HouseholdViewModel) {
    val records = home.records
    var placeEdit by remember { mutableStateOf<Pair<String, String>?>(null) }
    var adding by remember { mutableStateOf(false) }; var name by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf<StorageLocation?>(null) }
    var kind by rememberSaveable { mutableStateOf("全部") }; var search by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("从多到少") }
    val active = records.filter { !it.archived && (kind == "全部" || it.kind == if (kind == "食品") "food" else "object") }
    val names = (state.locations.map { it.name } + active.map { it.location }).filter(String::isNotBlank).distinct()
        .filter { search.isBlank() || HouseholdSearch.normalize(it).contains(HouseholdSearch.normalize(search)) || state.locations.firstOrNull { loc -> loc.name == it }?.aliasList?.any { alias -> HouseholdSearch.normalize(alias).contains(HouseholdSearch.normalize(search)) } == true }
    val counts = active.groupingBy { it.location }.eachCount()
    val ordered = when(sort) { "从多到少" -> names.sortedByDescending { counts[it] ?: 0 }; "从少到多" -> names.sortedBy { counts[it] ?: 0 }; else -> names }
    val options = ordered.sortedBy { name -> state.locations.firstOrNull { it.name == name }?.sortOrder?.takeIf { it < 0 } ?: 0 }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("存放位置", style = MaterialTheme.typography.titleLarge); TextButton(onClick = { adding = true }) { Text("＋ 新位置") } } }
        item { Text("食品和物品共用位置 · 常用置顶优先，其余按所选方式排序", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { FoodTabs(listOf("全部", "食品", "物品"), kind, { kind = it }) }
        item { FoodSearchField(search, { search = it }, "搜索位置或语音别名") }
        item { FoodSegments(listOf("从多到少", "从少到多", "不排序").map { it to it }, sort, { sort = it }) }
        if (options.isEmpty()) item { Text("暂无符合条件的位置", modifier = Modifier.padding(vertical = 32.dp), style = MaterialTheme.typography.bodyMedium) }
        items(options, key = { it }) { location ->
            val saved = state.locations.firstOrNull { it.name == location }
            var menu by remember { mutableStateOf(false) }
            Card(onClick = { onOpen(location, kind) }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Place, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(location + if ((saved?.sortOrder ?: 0) < 0) " · 常用" else "", style = MaterialTheme.typography.titleMedium)
                        val foodCount = records.count { !it.archived && it.kind == "food" && it.location == location }
                        val objectCount = records.count { !it.archived && it.kind == "object" && it.location == location }
                        Text(if (kind == "全部") "食品 $foodCount · 物品 $objectCount" else "${counts[location] ?: 0} 条${kind}记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (saved?.alias?.isNotBlank() == true) Text("别名：${saved.alias}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${counts[location] ?: 0}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Outlined.MoreVert, "位置设置") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("重命名位置") }, { placeEdit = location to "重命名"; menu = false })
                            DropdownMenuItem({ Text("合并到其他位置") }, { placeEdit = location to "合并"; menu = false })
                            DropdownMenuItem({ Text("设置位置别名") }, { placeEdit = location to "别名"; menu = false })
                            DropdownMenuItem({ Text(if ((saved?.sortOrder ?: 0) < 0) "取消常用置顶" else "常用置顶") }, { household.pin(location, (saved?.sortOrder ?: 0) >= 0); menu = false })
                            if (saved != null) DropdownMenuItem({ Text("移除常用选项") }, { removing = saved; menu = false })
                        }
                    }
                }
            }
        }
    }
    placeEdit?.let { (source, mode) -> PlaceEditor(source, mode, home, { placeEdit = null }, household::changePlace, household::aliases) }
    if (adding) AlertDialog(onDismissRequest = { adding = false }, title = { Text("新增位置") }, text = { OutlinedTextField(name, { name = it }, label = { Text("例如：餐桌、厨房抽屉") }, singleLine = true) }, confirmButton = { TextButton(onClick = { vm.addLocation(name); name = ""; adding = false }, enabled = name.isNotBlank()) { Text("添加") } }, dismissButton = { TextButton(onClick = { adding = false }) { Text("取消") } })
    removing?.let { loc -> AlertDialog(onDismissRequest = { removing = null }, title = { Text("移除${loc.name}的常用选项？") }, text = { Text("已有食品和物品保留原位置，库存记录不会删除。") }, confirmButton = { TextButton(onClick = { vm.removeLocation(loc.id); removing = null }) { Text("移除") } }, dismissButton = { TextButton(onClick = { removing = null }) { Text("取消") } }) }
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
