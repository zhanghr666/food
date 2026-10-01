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
import com.example.foodmanager.data.FoodItem
import com.example.foodmanager.notify.ExpiryReminderWorker
import kotlinx.coroutines.launch

/** 设置：提醒开关、提醒阈值、提醒时间、数据管理 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: FoodViewModel,
    onOpenArchived: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val archived by viewModel.archived.collectAsStateWithLifecycle()
    val customLocations by viewModel.customLocations.collectAsStateWithLifecycle()
    val customFoods by viewModel.customFoods.collectAsStateWithLifecycle()

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
                    subtitle = "即使没有打开 App 也会收到通知",
                    checked = settings.notifyEnabled,
                    onCheckedChange = { checked ->
                        viewModel.updateSettings { it.copy(notifyEnabled = checked) }
                    },
                )
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
                    text = "每天提醒时间",
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
                subtitle = "添加后：位置下拉里可选，语音说它也能识别（支持「底下/下面/旁边」等说法）",
            ) {
                ManagedListEditor(
                    placeholder = "如：茶几底下、飘窗、书桌抽屉",
                    items = customLocations,
                    onAdd = { viewModel.addCustomLocation(it) },
                    onRemove = { viewModel.removeCustomLocation(it) },
                )
            }

            SettingsSection(
                icon = Icons.Filled.Restaurant,
                title = "常用食品",
                subtitle = "添加后：语音识别会优先按你的叫法识别（进入离线语法词表），OCR 也会优先匹配",
            ) {
                ManagedListEditor(
                    placeholder = "如：西兰花、车厘子、螺蛳粉",
                    items = customFoods,
                    onAdd = { viewModel.addCustomFood(it) },
                    onRemove = { viewModel.removeCustomFood(it) },
                )
            }

            // ---------- 录入偏好 ----------
            SettingsSection(
                icon = Icons.Filled.Storage,
                title = "录入偏好",
                subtitle = "让批量录入更快",
            ) {
                SwitchRow(
                    title = "沿用上次的存放位置",
                    subtitle = "OCR / 语音没有识别到位置时，使用最近一次填写的位置：${
                        settings.lastLocation.ifBlank { "未设置" }
                    }",
                    checked = settings.reuseLastLocation,
                    onCheckedChange = { checked ->
                        viewModel.updateSettings { it.copy(reuseLastLocation = checked) }
                    },
                )
            }

            // ---------- 数据 ----------
            SettingsSection(
                icon = Icons.Filled.PrivacyTip,
                title = "数据与隐私",
                subtitle = "全部数据保存在本机 SQLite 数据库，不会上传网络",
            ) {
                InfoRow("当前记录", "${stats.total} 件")
                InfoRow("已归档（已用完/丢弃）", "${archived.size} 件")
                InfoRow("分类数量", "${stats.categories} 个")
                InfoRow("存放位置数量", "${stats.locations} 个")
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
                        Text("清空全部数据", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "食物存储管理 v1.0　·　本地离线运行",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空全部数据？") },
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("已用完 / 已丢弃（${archived.size}）") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                },
            )
        },
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
                                text = "${com.example.foodmanager.ui.components.formatQuantity(item.quantity)}${item.unit}　${item.location}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { viewModel.setArchived(item.id, false) }) {
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
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

/** 可维护的字符串列表：输入 + 添加，已有项以可删除的胶囊展示 */
@Composable
private fun ManagedListEditor(
    placeholder: String,
    items: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            placeholder = { Text(placeholder, style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = {
                val name = input.trim()
                if (name.isNotBlank()) {
                    onAdd(name)
                    input = ""
                }
            },
            enabled = input.isNotBlank(),
        ) { Text("添加") }
    }
    Spacer(Modifier.height(6.dp))
    if (items.isEmpty()) {
        Text(
            text = "还没有添加，点击上方输入框添加一条",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEach { item ->
                androidx.compose.material3.InputChip(
                    selected = false,
                    onClick = { onRemove(item) },
                    label = { Text("$item ✕", style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = "点击任意条目可删除（共 ${items.size} 条）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
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
