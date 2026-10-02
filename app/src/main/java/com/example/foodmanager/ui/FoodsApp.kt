package com.example.foodmanager.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.foodmanager.FoodApp
import com.example.foodmanager.data.Locations
import com.example.foodmanager.data.Units
import com.example.foodmanager.notify.ExpiryReminderWorker
import com.example.foodmanager.ui.screens.EditorScreen
import com.example.foodmanager.ui.screens.ScanScreen
import kotlinx.coroutines.launch

/** 主界面：4 个主页面用状态直接切换（不再走 NavHost，避免内容与底部栏不同步） */
@Composable
fun FoodsApp(
    /** 从通知点进来时为 true：直接落到「首页」并高亮提醒清单 */
    openAlertsOnStart: Boolean = false,
    onOpenAlertsHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as FoodApp
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val viewModel: FoodViewModel = viewModel(factory = FoodViewModel.factory(app.repository))
    val editorViewModel: EditorViewModel = viewModel(factory = EditorViewModel.factory(app.repository))
    DateRefreshEffect(viewModel)
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val customLocations by viewModel.customLocations.collectAsStateWithLifecycle()

    // 0=首页 1=库存 2=录入 3=设置
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // 全屏子页面（不走底部栏）：null = 主页面
    // 用 rememberSaveable + Saver：进程被系统回收后重建时，"正在编辑哪一条"
    // 也能恢复，不会莫名掉回首页。
    var fullScreen by rememberSaveable(stateSaver = FullScreenSaver) {
        mutableStateOf<FullScreen?>(null)
    }
    // 「录入」页当前展开的方式；首次进入用设置里记住的值，默认 manual
    var entryMode by rememberSaveable { mutableStateOf<String?>(null) }
    val effectiveEntryMode = entryMode ?: settings.lastEntryMode.ifBlank { "manual" }
    val customFoods by viewModel.customFoods.collectAsStateWithLifecycle()
    val locationAliasMap by viewModel.locationAliasMap.collectAsStateWithLifecycle()
    val foodAliasMap by viewModel.foodAliasMap.collectAsStateWithLifecycle()
    val ocrSingleItem by viewModel.ocrSingleItem.collectAsStateWithLifecycle()

    // 「录入」页的界面状态放在这一层：切到其它标签页再回来，草稿与表单内容都不会丢
    val scanState = viewModel.scanState

    // 从通知点进来：切到首页，让提醒清单可见
    var confirmNotificationNavigation by remember { mutableStateOf(false) }
    var highlightAlerts by remember { mutableStateOf(false) }
    BackHandler(enabled = fullScreen == FullScreen.Archived) { fullScreen = null }
    LaunchedEffect(fullScreen) {
        (fullScreen as? FullScreen.Editor)?.let { editorViewModel.beginSession(it.itemId, it.session) }
    }
    LaunchedEffect(openAlertsOnStart, editorViewModel.saving) {
        if (openAlertsOnStart && !editorViewModel.saving) {
            if (fullScreen is FullScreen.Editor && editorViewModel.hasChanges) {
                confirmNotificationNavigation = true
                return@LaunchedEffect
            }
            editorViewModel.endSession()
            fullScreen = null
            tab = 0
            highlightAlerts = true
            onOpenAlertsHandled()
        }
    }

    if (confirmNotificationNavigation) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmNotificationNavigation = false; onOpenAlertsHandled() },
            title = { Text("先查看提醒清单？") },
            text = { Text("当前有未保存的修改。可继续编辑，或放弃修改后查看提醒。") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                confirmNotificationNavigation = false
                editorViewModel.endSession()
                fullScreen = null
                tab = 0
                highlightAlerts = true
                onOpenAlertsHandled()
            }) { Text("放弃修改并查看") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = {
                confirmNotificationNavigation = false; onOpenAlertsHandled()
            }) { Text("继续编辑") } },
        )
    }
    Scaffold(
        // Scaffold 默认底色是 surface（近白 #FFFBFF），与页面米色背景（background）不一致：
        // 内容不足一屏时（例如键盘弹出把内容顶起），会露出一块近白色，看起来像"白框"。
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (fullScreen == null) {
                NavigationBar {
                    BottomTabs.forEachIndexed { index, item ->
                        val selected = tab == index
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                tab = index
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.icon,
                                    contentDescription = item.label,
                                    modifier = Modifier.size(26.dp),
                                )
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding).consumeWindowInsets(padding),
        ) {
            when (fullScreen) {
                FullScreen.Archived -> ArchivedScreen(
                    viewModel = viewModel,
                    onBack = { fullScreen = null },
                )

                is FullScreen.Editor -> {
                    val screen = fullScreen as FullScreen.Editor
                    key(screen.session) {
                    if (editorViewModel.sessionId == screen.session) EditorScreen(
                        viewModel = editorViewModel,
                        customLocations = customLocations,
                        customFoods = customFoods,
                        locationAliases = locationAliasMap,
                        foodAliases = foodAliasMap,
                        onBack = { editorViewModel.endSession(); fullScreen = null },
                        onSaved = {
                            scope.launch { snackbar.showSnackbar("已保存到本地库存") }
                            editorViewModel.endSession()
                            fullScreen = null
                            viewModel.refreshToday()
                        },
                    )
                    }
                }

                null -> when (tab) {
                    0 -> HomeScreen(
                        viewModel = viewModel,
                        highlightAlerts = highlightAlerts,
                        onAlertsHighlighted = { highlightAlerts = false },
                        onOpenInventory = { tab = 1 },
                        onAddManual = { entryMode = "manual"; tab = 2 },
                        onOpenScan = { entryMode = "photo"; tab = 2 },
                        onOpenVoice = { entryMode = "voice"; tab = 2 },
                        onOpenItem = { id -> fullScreen = FullScreen.Editor(id) },
                    )

                    1 -> InventoryScreen(
                        viewModel = viewModel,
                        onAddManual = { fullScreen = FullScreen.Editor(0L) },
                        onOpenScan = { entryMode = "photo"; tab = 2 },
                        onOpenItem = { id -> fullScreen = FullScreen.Editor(id) },
                    )

                    2 -> ScanScreen(
                        state = scanState,
                        defaultUnit = settings.lastUnit.ifBlank { Units.DEFAULT },
                        customLocations = customLocations,
                        customFoods = customFoods,
                        locationAliases = locationAliasMap,
                        foodAliases = foodAliasMap,
                        ocrSingleItem = ocrSingleItem,
                        entryMode = effectiveEntryMode,
                        onEntryModeChange = { mode ->
                            entryMode = mode
                            if (mode.isNotBlank()) viewModel.saveEntryMode(mode)
                        },
                        onAddItems = { items, onDone -> viewModel.addDrafts(items, onDone) },
                    )

                    else -> SettingsScreen(
                        viewModel = viewModel,
                        scanState = scanState,
                        onOpenArchived = { fullScreen = FullScreen.Archived },
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshToday()
        ExpiryReminderWorker.runNow(context)
    }
}

private data class BottomTab(
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
)

private val BottomTabs = listOf(
    BottomTab("首页", Icons.Filled.Home, Icons.Outlined.Home),
    BottomTab("库存", Icons.Filled.Inventory2, Icons.Outlined.Inventory2),
    BottomTab("录入", Icons.Filled.DocumentScanner, Icons.Outlined.DocumentScanner),
    BottomTab("设置", Icons.Filled.Settings, Icons.Outlined.Settings),
)
