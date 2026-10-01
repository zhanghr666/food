package com.example.foodmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.foodmanager.data.AppSettings
import com.example.foodmanager.data.CommonFood
import com.example.foodmanager.data.FoodItem
import com.example.foodmanager.data.FoodRepository
import com.example.foodmanager.data.StorageLocation
import com.example.foodmanager.notify.ExpiryReminderWorker
import kotlinx.coroutines.launch

/** 设置：提醒开关、提醒阈值、提醒时间、数据管理 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: FoodViewModel,
    /** 录入页的临时状态：用于展示"待入库暂存 / 上次拍照解析结果"等诊断信息 */
    scanState: ScanEntryState,
    onOpenArchived: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val archived by viewModel.archived.collectAsStateWithLifecycle()
    val storageLocations by viewModel.storageLocations.collectAsStateWithLifecycle()
    val commonFoods by viewModel.commonFoods.collectAsStateWithLifecycle()

    // 每个位置被多少条**在库**记录使用（删除确认时提示用），随记录变化自动刷新
    val allItems by viewModel.allItems.collectAsStateWithLifecycle()
    val usageCounts = remember(allItems) {
        allItems.filter { !it.archived && it.location.isNotBlank() }
            .groupingBy { it.location }
            .eachCount()
    }

    var showClearDialog by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ---------- 提醒 ----------
            SettingsSection(
                icon = Icons.Filled.NotificationsActive,
                title = "过期提醒",
                subtitle = "临近过期与已经过期时提醒你，支持系统通知和首页横幅",
            ) {
                SwitchRow(
                    title = "系统通知提醒",
                    subtitle = "允许通知后可在后台检查，系统可能推迟提醒",
                    checked = settings.notifyEnabled,
                    onCheckedChange = { checked ->
                        viewModel.updateSettings { it.copy(notifyEnabled = checked) }
                    },
                )
                com.example.foodmanager.ui.components.NotificationPermissionCard()
                SwitchRow(
                    title = "首页提醒横幅",
                    subtitle = "在 App 首页顶部显示需要处理的清单",
                    checked = settings.homeBannerEnabled,
                    onCheckedChange = { checked ->
                        viewModel.updateSettings { it.copy(homeBannerEnabled = checked) }
                    },
                )

                Spacer(Modifier.height(6.dp))
                Text(
                    text = "提前提醒天数",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                ChipRow(
                    options = listOf(1, 2, 3, 5, 7, 14),
                    selectedLabel = "${settings.warnDays} 天",
                    labelOf = { "$it 天" },
                    onSelect = { days -> viewModel.updateSettings { it.copy(warnDays = days) } },
                )

                Spacer(Modifier.height(6.dp))
                Text(
                    text = "每天约在此时检查（可能延迟）",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                ChipRow(
                    options = listOf(7, 8, 9, 12, 18, 20),
                    selectedLabel = "%02d:00".format(settings.notifyHour),
                    labelOf = { "%02d:00".format(it) },
                    onSelect = { hour -> viewModel.updateSettings { it.copy(notifyHour = hour) } },
                )

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        ExpiryReminderWorker.runNow(context)
                        scope.launch {
                            checkResult = ExpiryReminderWorker.checkNow(context)
                        }
                    }) {
                        Text("立即检查一次")
                    }
                    if (checkResult.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = checkResult,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ---------- 常用地点 / 常用食品 ----------
            SettingsSection(
                icon = Icons.Filled.Place,
                title = "常用存放地点",
                subtitle = "添加后：位置下拉里可选，语音说它也能识别。" +
                    "「别名」用于同义词，例如给「冰箱」填「冷藏」",
            ) {
                ManagedListEditor(
                    placeholder = "如：茶几底下、飘窗、书桌抽屉",
                    items = storageLocations,
                    nameOf = { it.name },
                    aliasOf = { it.alias },
                    idOf = { it.id },
                    usageLabel = { item ->
                        val count = usageCounts[item.name] ?: 0
                        if (count > 0) "有 $count 条在库记录在用这个位置" else null
                    },
                    onAdd = { name, result -> viewModel.addStorageLocation(name, result) },
                    onRemove = { viewModel.removeStorageLocation(it) },
                    onAliasChange = { id, alias -> viewModel.updateStorageLocationAlias(id, alias) },
                )
            }

            SettingsSection(
                icon = Icons.Filled.Restaurant,
                title = "常用食品",
                subtitle = "添加后：语音识别会优先按你的叫法识别（进入离线语法词表），OCR 也会优先匹配",
            ) {
                ManagedListEditor(
                    placeholder = "如：西兰花、车厘子、螺蛳粉",
                    items = commonFoods,
                    nameOf = { it.name },
                    aliasOf = { it.alias },
                    idOf = { it.id },
                    usageLabel = { null },
                    onAdd = { name, result -> viewModel.addCommonFood(name, result) },
                    onRemove = { viewModel.removeCommonFood(it) },
                    onAliasChange = { id, alias -> viewModel.updateCommonFoodAlias(id, alias) },
                )
            }

            // ---------- 录入偏好 ----------
            SettingsSection(
                icon = Icons.Filled.Storage,
                title = "录入偏好",
                subtitle = "让批量录入更快",
            ) {
                SwitchRow(
                    title = "一张照片只识别一件商品",
                    subtitle = if (settings.ocrSingleItem) {
                        "推荐。单件包装上的「品名 / 口味 / 规格」会合并成一条，不会被拆成好几条"
                    } else {
                        "已关闭：按「一行一件商品」拆分，适合拍购物小票批量录入"
                    },
                    checked = settings.ocrSingleItem,
                    onCheckedChange = { checked ->
                        viewModel.updateSettings { it.copy(ocrSingleItem = checked) }
                    },
                )
            }

            // ---------- 数据 ----------
            SettingsSection(
                icon = Icons.Filled.PrivacyTip,
                title = "数据与隐私",
                subtitle = "识别与库存仅在本机运行，应用无联网权限，已关闭系统云备份。Android 12 及以上仅允许系统在换机时迁移食物数据库；较旧系统不自动备份。卸载前请注意本地数据无法保证恢复。",
            ) {
                InfoRow("当前记录", "${stats.total} 件")
                InfoRow("已归档（已用完/丢弃）", "${archived.size} 件")
                InfoRow("分类数量", "${stats.categories} 个")
                InfoRow("存放位置数量", "${stats.locations} 个")
                InfoRow("待入库暂存", "${scanState.drafts.size} 条")
                InfoRow(
                    "拍照识别模式",
                    if (settings.ocrSingleItem) "一张照片一条" else "按行拆分（小票模式）",
                )
                if (scanState.lastParsedCount >= 0) {
                    InfoRow(
                        "上次拍照解析结果",
                        "${scanState.lastParsedCount} 条" +
                            if (scanState.lastSingleItemMode) "（单件模式）" else "（拆分模式）",
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onOpenArchived) {
                        Icon(Icons.Filled.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("查看归档")
                    }
                    TextButton(onClick = { showClearDialog = true }) {
                        Icon(
                            Icons.Filled.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("清空全部食物记录", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                // 版本号从 APK 读取，避免硬编码导致与实际版本不一致
                text = "食物存储管理 v${appVersionName(context)}　·　本地离线运行",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "常用存放地点与常用食品最多各 ${FoodViewModel.MAX_CUSTOM_ITEMS} 条" +
                    "（它们会进入离线语音词表，过多会影响识别）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空全部食物记录？") },
            text = { Text("所有食物记录都会被删除，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    showClearDialog = false
                }) { Text("确认清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            },
        )
    }
}

/** 归档列表 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedScreen(
    viewModel: FoodViewModel,
    onBack: () -> Unit,
) {
    val archived by viewModel.archived.collectAsStateWithLifecycle()
    // 与库存页保持一致：删除前必须二次确认
    var pendingDelete by remember { mutableStateOf<FoodItem?>(null) }
    // 操作反馈：恢复/删除后告诉用户发生了什么（原来点了就消失，无从确认）
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("已用完 / 已丢弃（${archived.size}）") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (archived.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                com.example.foodmanager.ui.components.EmptyState(
                    title = "还没有归档记录",
                    description = "在库存里点击「标记已用完」，记录会移动到这里",
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            items(archived, key = { it.id }) { item ->                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = com.example.foodmanager.ui.components.joinInfo(
                                    com.example.foodmanager.ui.components.describeQuantityText(item.quantity, item.unit),
                                    item.location,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = {
                            viewModel.setArchived(item.id, false)
                            scope.launch {
                                snackbar.showSnackbar("「${item.name}」已恢复到库存")
                            }
                        }) {
                            Text("恢复到库存")
                        }
                        TextButton(onClick = { pendingDelete = item }) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    // 归档项的删除确认（与库存页行为一致）
    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条归档记录？") },
            text = { Text("「${target.name}」将被永久删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(target.id)
                    pendingDelete = null
                    scope.launch { snackbar.showSnackbar("已删除「${target.name}」") }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

/**
 * 可维护的列表：输入 + 添加，已有项以可删除的胶囊展示。
 *
 * 每个条目支持两件事：
 * - 点胶囊本体 → **删除**（会先弹确认；若有关联记录数会一并提示）
 * - 点「别名」→ 展开一个小输入框，填语音同义词（如「冰箱」的别名「冷藏」）
 *
 * 泛型是为了让「常用存放地点」和「常用食品」共用同一套界面。
 */
@Composable
private fun <T> ManagedListEditor(
    placeholder: String,
    items: List<T>,
    nameOf: (T) -> String,
    aliasOf: (T) -> String,
    idOf: (T) -> Long,
    /** 返回提示文案（如"有 N 条在库记录在用这个位置"），null 表示不提示 */
    usageLabel: (T) -> String?,
    onAdd: (String, (Boolean) -> Unit) -> Unit,
    onRemove: (Long) -> Unit,
    onAliasChange: (Long, String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var addHint by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<T?>(null) }
    var aliasEditingId by remember { mutableStateOf<Long?>(null) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                addHint = ""
            },
            placeholder = { Text(placeholder, style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = {
                val name = input.trim()
                if (name.isBlank()) return@Button
                onAdd(name) { ok ->
                    if (ok) {
                        input = ""
                        addHint = ""
                    } else {
                        // 明确告诉用户为什么没加上，而不是静默失败
                        addHint = "「$name」没有添加：可能已存在、或已达到 " +
                            "${FoodRepository.MAX_CUSTOM_ITEMS} 条上限"
                    }
                }
            },
            enabled = input.isNotBlank(),
        ) { Text("添加") }
    }

    if (addHint.isNotBlank()) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = addHint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    Spacer(Modifier.height(6.dp))
    if (items.isEmpty()) {
        Text(
            text = "还没有添加，点击上方输入框添加一条",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            items.forEach { item ->
                val name = nameOf(item)
                val alias = aliasOf(item)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        androidx.compose.material3.InputChip(
                            selected = false,
                            onClick = { pendingDelete = item },
                            label = {
                                Text(
                                    text = if (alias.isBlank()) "$name ✕" else "$name（$alias）✕",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                // 点击「别名」时自动把当前别名填进输入框，见下方 aliasEditingId 分支
                                aliasEditingId = if (aliasEditingId == idOf(item)) null else idOf(item)
                            },
                        ) {
                            Text(
                                text = if (alias.isBlank()) "加别名" else "改别名",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    if (aliasEditingId == idOf(item)) {
                        var draft by remember(idOf(item), alias) { mutableStateOf(alias) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = draft,
                                onValueChange = { draft = it },
                                label = { Text("语音别名（多个用 | 分隔）", style = MaterialTheme.typography.labelSmall) },
                                placeholder = { Text("如：冷藏|冷藏室", style = MaterialTheme.typography.labelSmall) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                onAliasChange(idOf(item), draft)
                                aliasEditingId = null
                            }) { Text("保存") }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "点击条目可删除（共 ${items.size} 条，最多 " +
                    "${FoodRepository.MAX_CUSTOM_ITEMS} 条）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // 删除确认：位置被记录引用时给出提示（提示里说明不会改动已有记录）
    pendingDelete?.let { target ->
        val usage = usageLabel(target)
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Icons.Filled.DeleteForever, contentDescription = null) },
            title = { Text("删除「${nameOf(target)}」？") },
            text = {
                Text(
                    buildString {
                        append("删除后它不再出现在位置下拉里。")
                        if (usage != null) {
                            append("\n\n")
                            append(usage)
                            append("，这些记录的位置文字**保持不变**，不会丢数据。")
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(idOf(target))
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SettingsSection(    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )
            content()
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 读取 APK 的 versionName（读不到时返回占位符，不影响界面） */
private fun appVersionName(context: android.content.Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "?"

@Composable
private fun InfoRow(label: String, value: String) {    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun <T> ChipRow(
    options: List<T>,
    selectedLabel: String,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { option ->
            val label = labelOf(option)
            FilterChip(
                selected = label == selectedLabel,
                onClick = { onSelect(option) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

/** 保留默认设置工厂，便于测试 */
internal fun defaultSettings(): AppSettings = AppSettings.DEFAULT
