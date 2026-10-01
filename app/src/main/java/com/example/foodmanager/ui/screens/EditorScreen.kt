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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
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
import com.example.foodmanager.ui.EditorViewModel
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
    onAddCustomLocation: (String) -> Unit = {},
    onRemoveCustomLocation: (String) -> Unit = {},
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    // 默认展开手工录入（编辑页的主要目的就是改字段）
    var mode by remember { mutableStateOf("manual") }
    // 键盘是否弹出：用于给滚动内容追加余量
    val imeVisible = WindowInsets.isImeVisible
    var lastBatchHint by remember { mutableStateOf("") }

    val item = viewModel.item
    val canSave = item.name.isNotBlank()

    fun mergeFromDraft(draft: com.example.foodmanager.core.FoodDraft) {
        viewModel.update { current ->
            current.copy(
                name = draft.name.ifBlank { current.name },
                category = draft.category.ifBlank { current.category },
                quantity = if (draft.quantity > 0) draft.quantity else current.quantity,
                unit = draft.unit.ifBlank { current.unit },
                location = draft.location.ifBlank { current.location },
                purchaseDate = draft.purchaseDate,
                productionDate = draft.productionDate ?: current.productionDate,
                expiryDate = draft.expiryDate ?: current.expiryDate,
                note = if (draft.note.isNotBlank()) draft.note else current.note,
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditing) "编辑食物" else "新增食物") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.save { id -> onSaved(id) } },
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
                Text("加载中…")
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
                        VoiceTextParser.parse(text, customLocations = customLocations, customFoods = customFoods)
                            ?.let { mergeFromDraft(it) }
                    },
                )
                Spacer(Modifier.height(8.dp))
            }

            // 手工录入：始终展示（拍照/语音识别结果也填进这张表）
            ManualInputCard(
                item = item,
                onChange = { updated -> viewModel.update { updated } },
                onSave = { viewModel.save { id -> onSaved(id) } },
                customLocations = customLocations,
                parentHandlesLocationWarning = true,
                title = if (viewModel.isEditing) "食物信息" else "手工录入",
                subtitle = if (mode == "manual") {
                    "逐项填写食物信息，填完点下方按钮保存"
                } else {
                    "识别结果已填入下表，可再手动修正"
                },
                saveLabel = if (viewModel.isEditing) "保存修改" else "添加到库存",
                showAddIcon = !viewModel.isEditing,
                secondaryAction = if (viewModel.isEditing) {
                    {
                        TextButton(onClick = {
                            viewModel.update { it.copy(archived = true) }
                            viewModel.save { id -> onSaved(id) }
                        }) { Text("标记已用完") }
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
