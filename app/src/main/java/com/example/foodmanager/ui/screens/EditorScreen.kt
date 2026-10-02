package com.example.foodmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.foodmanager.core.DateUtils
import com.example.foodmanager.ui.EditorViewModel
import com.example.foodmanager.ui.LocationConfirmDialog
import com.example.foodmanager.ui.ManualEntryState
import com.example.foodmanager.ui.components.FoodDatePickerDialog
import com.example.foodmanager.ui.components.ManualInputCard
import com.example.foodmanager.ui.components.PhotoInputCard
import com.example.foodmanager.ui.components.VoiceInputCard
import com.example.foodmanager.voice.VoiceTextParser

/**
 * 新增 / 编辑食物。
 *
 * 页面结构与「录入」页保持一致：顶部三个入口卡片（拍照 / 语音 / 手工），互斥展开，
 * 默认展开「手工录入」。从库存、首页、通知等任意入口进来都是同一套界面。
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    customLocations: List<String> = emptyList(),
    customFoods: List<String> = emptyList(),
    /** 位置别名 → 正式名（如「冷藏」→「冰箱」） */
    locationAliases: Map<String, String> = emptyMap(),
    /** 食品别名 → 正式名（如「樱桃」→「车厘子」） */
    foodAliases: Map<String, String> = emptyMap(),
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    // 默认展开手工录入（编辑页的主要目的就是改字段）
    var mode by rememberSaveable { mutableStateOf("manual") }
    // 键盘是否弹出：用于给滚动内容追加余量
    val imeVisible = WindowInsets.isImeVisible
    var lastBatchHint by remember { mutableStateOf("") }

    val item = viewModel.item
    val canSave = viewModel.loaded && !viewModel.saving && item.name.isNotBlank()
    var showingLocationConfirm by rememberSaveable { mutableStateOf(false) }
    var showingDiscardConfirm by rememberSaveable { mutableStateOf(false) }
    var showingArchiveConfirm by rememberSaveable { mutableStateOf(false) }
    val statusMessage = if (viewModel.saving) "正在保存…" else viewModel.error
    val statusIsError = viewModel.error.isNotBlank()
    var datePickerTarget by remember { mutableStateOf<String?>(null) }
    val requestBack: () -> Unit = {
        if (!viewModel.saving) {
            if (viewModel.hasChanges) showingDiscardConfirm = true else onBack()
        }
    }
    BackHandler(onBack = requestBack)
    LaunchedEffect(viewModel.savedId) {
        viewModel.savedId?.let(onSaved)
    }
    val doSave: () -> Unit = { viewModel.save() }
    val requestSave: () -> Unit = {
        if (canSave) {
            if (item.location.isBlank()) showingLocationConfirm = true else doSave()
        }
    }
    fun mergeFromDraft(draft: com.example.foodmanager.core.FoodDraft) {
        viewModel.mergeRecognition(draft)
        lastBatchHint = "已补充可识别字段，保留已有记录和手动修改的内容。" +
            draft.note.takeIf { it.isNotBlank() }?.let { "\n本次识别提示：$it" }.orEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditing) "编辑食物" else "新增食物") },
                navigationIcon = {
                    IconButton(onClick = requestBack, enabled = !viewModel.saving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = requestSave,
                        enabled = canSave,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "保存")
                    }
                },
            )
        },
    ) { padding ->
        if (!viewModel.loaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column {
                    Text(viewModel.error.ifBlank { "加载中…" })
                    if (viewModel.error.isNotBlank()) TextButton(onClick = { viewModel.retryLoad() }) { Text("重试") }
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = 8.dp,
                    // 键盘弹出时给滚动内容追加余量，否则最后的「备注」框滚不动、会被遮住。
                    // 加在内容留白上而不是容器内边距，避免缩小视口露出背景色（白框）。
                    bottom = if (imeVisible) 280.dp else 8.dp,
                ),
        ) {
            // ---------- 三种录入方式入口（与「录入」页一致） ----------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditorModeEntry(
                    icon = Icons.Filled.PhotoCamera,
                    title = "拍照录入",
                    subtitle = "识别后填表",
                    selected = mode == "photo",
                    modifier = Modifier.weight(1f),
                    onClick = { mode = "photo" },
                )
                EditorModeEntry(
                    icon = Icons.Filled.Mic,
                    title = "语音录入",
                    subtitle = "说完自动填表",
                    selected = mode == "voice",
                    modifier = Modifier.weight(1f),
                    onClick = { mode = "voice" },
                )
                EditorModeEntry(
                    icon = Icons.Filled.Edit,
                    title = "手工录入",
                    subtitle = "逐项填写",
                    selected = mode == "manual",
                    modifier = Modifier.weight(1f),
                    onClick = { mode = "manual" },
                )
            }

            Spacer(Modifier.height(10.dp))

            if (mode == "photo") {
                PhotoInputCard(
                    title = "拍照录入",
                    customFoods = customFoods,
                    foodAliases = foodAliases,
                    // 编辑页没有"日期字段"入口，这里给一个直接选保质期的按钮
                    onPickDate = { datePickerTarget = "expiry" },
                    onRecognized = { _, drafts ->
                        drafts.firstOrNull()?.let { mergeFromDraft(it) }
                        if (drafts.size > 1) {
                            // 编辑页只填第一条，其余提示去「录入」页批量处理
                            lastBatchHint = "识别到 ${drafts.size} 条，已填入第一条；批量录入请到「录入」页"
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
            }

            if (mode == "voice") {
                VoiceInputCard(
                    title = "语音录入",
                    hints = listOf(
                        "牛奶两瓶放冰箱冷藏，六月一号过期",
                        "鸡蛋两盒放冰箱冷藏，三周后过期",
                    ),
                    onResult = { text ->
                        VoiceTextParser.parse(
                            text,
                            customLocations = customLocations,
                            customFoods = customFoods,
                            locationAliases = locationAliases,
                            foodAliases = foodAliases,
                        )
                            ?.let { mergeFromDraft(it) }
                    },
                )
                Spacer(Modifier.height(8.dp))
            }

            // 手工录入：始终展示（拍照/语音识别结果也填进这张表）
            // 表单展示、识别填充和保存校验都读取同一份 ViewModel 状态。
            ManualInputCard(
                item = item,
                onChange = { updated ->
                    viewModel.update { updated }

                },
                onSave = requestSave,
                saving = viewModel.saving,
                customLocations = customLocations,
                title = if (viewModel.isEditing) "食物信息" else "手工录入",
                subtitle = if (mode == "manual") {
                    "逐项填写食物信息，填完点下方按钮保存"
                } else {
                    "识别结果已填入下表，可再手动修正"
                },
                saveLabel = if (viewModel.isEditing) "保存修改" else "添加到库存",
                showAddIcon = !viewModel.isEditing,
                statusMessage = statusMessage,
                statusIsError = statusIsError,
                secondaryAction = if (viewModel.isEditing) {
                    {
                        TextButton(enabled = !viewModel.saving, onClick = { showingArchiveConfirm = true }) { Text("标记已用完") }
                    }
                } else {
                    null
                },
            )

            if (lastBatchHint.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Text(
                        text = lastBatchHint,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // 未填存放位置的确认：顶栏 ✓ 与表单按钮都走这里
    if (showingLocationConfirm) {
        LocationConfirmDialog(
            itemName = item.name,
            onDismiss = { showingLocationConfirm = false },
            onConfirm = {
                showingLocationConfirm = false
                doSave()
            },
        )
    }

    if (showingDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showingDiscardConfirm = false },
            title = { Text("放弃未保存的修改？") },
            text = { Text("返回后，本次尚未保存的内容将被丢弃。") },
            confirmButton = { TextButton(onClick = onBack) { Text("放弃修改") } },
            dismissButton = { TextButton(onClick = { showingDiscardConfirm = false }) { Text("继续编辑") } },
        )
    }
    if (showingArchiveConfirm) {
        AlertDialog(
            onDismissRequest = { showingArchiveConfirm = false },
            title = { Text("标记为已用完？") },
            text = { Text("将保存当前修改并移至归档，可以在设置中恢复。") },
            confirmButton = { TextButton(onClick = { showingArchiveConfirm = false; viewModel.save(archive = true) }) { Text("标记已用完") } },
            dismissButton = { TextButton(onClick = { showingArchiveConfirm = false }) { Text("取消") } },
        )
    }
    // 拍照卡片里「手动选保质期」的日期选择
    datePickerTarget?.let {
        FoodDatePickerDialog(
            initialDate = item.expiryDate ?: DateUtils.today(),
            onDismiss = { datePickerTarget = null },
            onConfirm = { millis ->
                viewModel.update { current -> current.copy(expiryDate = millis) }
                datePickerTarget = null
            },
        )
    }
}

/** 编辑页顶部的录入方式入口卡片（与「录入」页 ModeEntry 同款样式） */
@Composable
private fun EditorModeEntry(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 3.dp else 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(26.dp),
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
