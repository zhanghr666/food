package com.example.foodmanager.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.foodmanager.core.DateUtils
import com.example.foodmanager.core.FoodDraft
import com.example.foodmanager.data.Categories
import com.example.foodmanager.data.FoodItem
import com.example.foodmanager.data.Units
import com.example.foodmanager.ui.ScanEntryState
import com.example.foodmanager.ui.components.ComboField
import com.example.foodmanager.ui.components.DateFieldRow
import com.example.foodmanager.ui.components.DropdownField
import com.example.foodmanager.ui.components.FoodDatePickerDialog
import com.example.foodmanager.ui.components.ManualInputCard
import com.example.foodmanager.ui.components.PhotoInputCard
import com.example.foodmanager.ui.components.VoiceInputCard
import com.example.foodmanager.ui.components.initialQuantityText
import com.example.foodmanager.voice.VoiceTextParser
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * OCR 批量录入 + 语音批量录入。
 * 流程：拍照/选图 -> 离线识别 -> 解析成草稿 -> 逐条校对 -> 一键入库。
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ScanScreen(
    /** 界面状态由上层持有：切标签页后草稿与表单内容不丢 */
    state: ScanEntryState,
    defaultUnit: String,
    customLocations: List<String> = emptyList(),
    customFoods: List<String> = emptyList(),
    /** 当前展开的方式：photo / voice / manual（由上层持有，便于首页快捷入口指定） */
    entryMode: String = "manual",
    onEntryModeChange: (String) -> Unit = {},
    onAddItems: (List<FoodItem>, (Int) -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // 三种录入方式由 entryMode 控制，默认手工录入
    val photoExpanded = entryMode == "photo"
    val voiceExpanded = entryMode == "voice"
    val manualExpanded = entryMode == "manual"

    // 键盘是否弹出：用于给滚动内容追加余量（见下面的 contentPadding）
    val imeVisible = WindowInsets.isImeVisible
    // 保存后滚动回表单顶部用
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val nameFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    val drafts = state.drafts
    var showRaw by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }


    Scaffold(
        topBar = { TopAppBar(title = { Text("录入食物") }) },
        // 与主框架统一底色，避免内容不足一屏时露出近白色块（"白框"）
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 12.dp,
                // 键盘弹出时，在列表**内容**底部追加一段可滚动余量：
                // 「备注」是最后一项，没有余量就滚不动，会被输入法永久遮住。
                //
                // 注意：这里加在 contentPadding（内容留白）而不是 Modifier.padding（容器内边距）。
                // 后者会缩小视口、在键盘上方露出背景色 —— 就是用户看到的"白框"。
                bottom = if (imeVisible) 280.dp else 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ---------- 三种录入方式入口：样式统一，点击互斥展开 ----------
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ModeEntry(
                        icon = Icons.Filled.PhotoCamera,
                        title = "拍照录入",
                        subtitle = "识别包装信息",
                        selected = photoExpanded,
                        modifier = Modifier.weight(1f),
                        onClick = { onEntryModeChange("photo") },
                    )
                    ModeEntry(
                        icon = Icons.Filled.Mic,
                        title = "语音录入",
                        subtitle = "说一句就记好",
                        selected = voiceExpanded,
                        modifier = Modifier.weight(1f),
                        onClick = { onEntryModeChange("voice") },
                    )
                    ModeEntry(
                        icon = Icons.Filled.Edit,
                        title = "手工录入",
                        subtitle = "逐项填写",
                        selected = manualExpanded,
                        modifier = Modifier.weight(1f),
                        onClick = { onEntryModeChange("manual") },
                    )
                }
            }

            if (photoExpanded) {
                item {
                    PhotoInputCard(
                        title = "拍照录入",
                        onRecognized = { text, parsed ->
                            state.rawText = text
                            state.addDrafts(parsed)
                            statusText = if (parsed.isEmpty()) {
                                "没有从图片里识别到食物信息，可换一张或手工录入"
                            } else {
                                "识别到 ${parsed.size} 条，请核对后入库"
                            }
                        },
                    )
                }
            }

            if (voiceExpanded) {
                item {
                    VoiceInputCard(
                        title = "语音录入",
                        hints = listOf(
                            "牛奶两瓶放冰箱冷藏，六月一号过期",
                            "鸡蛋两盒放冰箱冷藏，三周后过期",
                            "一袋大米放常温储物柜",
                        ),
                        
                        onResult = { text ->
                            val parsed = VoiceTextParser.parseBatch(text, "voice", customLocations, customFoods)
                            if (parsed.isEmpty()) {
                                // 解析失败：提供「改用手工补填」，把原话带进名称框
                                state.prefillName(text, defaultUnit)
                                onEntryModeChange("manual")
                                scope.launch {
                                    snackbar.showSnackbar("没解析出食物信息，已切到手工录入并填入原文")
                                }
                            } else {
                                state.addDrafts(parsed)
                                scope.launch {
                                    snackbar.showSnackbar("已加入 ${parsed.size} 条待入库记录，请核对后点「全部入库」")
                                }
                            }
                        },
                    )
                }
            }

            // 手工录入：默认展开，与拍照/语音卡片同款样式
            if (manualExpanded) {
                item {
                    ManualInputCard(
                        item = state.manualDraft,
                        onChange = { state.manualDraft = it },
                        onSave = {
                            val toSave = state.manualDraft
                            if (toSave.location.isBlank()) {
                                // 位置未填：拦下并给出提醒（不落库）
                                state.manualStatusIsError = true
                                state.manualStatus = "还没有填写存放位置，填写后再保存（位置会用于快速查找）"
                                scope.launch { listState.animateScrollToItem(0) }
                                return@ManualInputCard
                            }
                            onAddItems(listOf(toSave)) { count ->
                                state.manualStatusIsError = false
                                state.manualStatus = "已入库 $count 条：「${toSave.name}」"
                            }
                            // ---------- 保存后的交互顺序 ----------
                            // 1) 先收起键盘与焦点，避免残留的聚焦状态让下拉/菜单重新弹开
                            keyboard?.hide()
                            focusManager.clearFocus(force = true)
                            // 2) 再清空表单；resetToken 变化会强制关闭所有已展开的下拉
                            state.resetManual(defaultUnit)
                            state.manualStatusIsError = false
                            state.manualStatus = "已保存「${toSave.name}」，可以继续录入下一条"
                            // 3) 滚动回表单顶部，让用户看到提示 + 名称框
                            scope.launch { listState.animateScrollToItem(0) }
                        },
                        customLocations = customLocations,
                        title = "手工录入",
                        subtitle = "逐项填写食物信息，填完点下方按钮保存",
                        saveLabel = "保存并继续录入",
                        showAddIcon = true,
                        nameFocusRequester = nameFocus,
                        resetToken = state.manualResetToken,
                        statusMessage = state.manualStatus,
                        statusIsError = state.manualStatusIsError,
                    )
                }
            }

            if (statusText.isNotBlank()) {
                item {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }
            }

            if (state.rawText.isNotBlank()) {
                item {
                    TextButton(onClick = { showRaw = !showRaw }) {
                        Text(if (showRaw) "收起识别原文" else "查看识别原文（${state.rawText.length} 字）")
                    }
                }
                if (showRaw) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Text(
                                text = state.rawText,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
            }

            if (drafts.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "待入库（${drafts.size}）",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { drafts.clear() }) { Text("清空") }
                    }
                }

                // 用 draftId 做 key：按下标做身份会在删除/入库后错位，导致删错条目、
                // 或把某条的展开状态串到下一条上。
                items(drafts, key = { it.draftId }) { draft ->
                    DraftCard(
                        index = drafts.indexOf(draft),
                        draft = draft,
                        customLocations = customLocations,
                        onUpdate = { updated ->
                            val i = drafts.indexOfFirst { it.draftId == updated.draftId }
                            if (i >= 0) drafts[i] = updated
                        },
                        onRemove = {
                            drafts.removeAll { it.draftId == draft.draftId }
                        },
                        onAddSingle = {
                            // 位置留空就存空（不套用"最近位置"，见 toFoodItem 的说明）
                            val item = draft.toFoodItem(defaultUnit)
                            onAddItems(listOf(item)) { count ->
                                drafts.removeAll { it.draftId == draft.draftId }
                                statusText = "已单独入库 $count 条：「${item.name}」"
                            }
                        },
                    )
                }

                item {
                    Button(
                        onClick = {
                            // 位置留空就存空，不用统一的 pendingLocation 覆盖每条草稿
                            val items = drafts.map { it.toFoodItem(defaultUnit) }
                            onAddItems(items) { count ->
                                drafts.clear()
                                statusText = "已入库 $count 条食物"
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("全部入库（${drafts.size} 条）")
                    }
                }
            }
        }
    }
}

/** 录入方式入口卡片（拍照 / 语音 / 手工） */
@Composable
private fun ModeEntry(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
                .padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(30.dp),
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
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

@Composable
private fun DraftCard(
    index: Int,
    draft: FoodDraft,
    customLocations: List<String>,
    onUpdate: (FoodDraft) -> Unit,
    onRemove: () -> Unit,
    onAddSingle: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var dateTarget by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "#${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = draft.name.ifBlank { "未识别到名称" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${com.example.foodmanager.ui.components.formatQuantity(draft.quantity)}${draft.unit}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = buildString {
                    append("到期：").append(DateUtils.format(draft.expiryDate))
                    if (draft.productionDate != null) {
                        append("　生产：").append(DateUtils.format(draft.productionDate))
                    }
                    append("　剩余：").append(DateUtils.describeRemaining(
                        draft.expiryDate?.let { DateUtils.daysBetween(DateUtils.today(), it) }
                    ))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (draft.highlights.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "识别字段：" + draft.highlights.joinToString("、") +
                        "（置信度 ${(draft.confidence * 100).toInt()}%）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (editing) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { onUpdate(draft.copy(name = it)) },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 数量用独立文本状态：绑定数值会导致清空时"跳回"旧数字
                    var qtyText by remember(draft) {
                        mutableStateOf(initialQuantityText(draft.quantity))
                    }
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { raw ->
                            val cleaned = raw.filter { it.isDigit() || it == '.' }
                            qtyText = cleaned
                            onUpdate(draft.copy(quantity = cleaned.toDoubleOrNull() ?: 0.0))
                        },
                        label = { Text("数量") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    DropdownField(
                        label = "单位",
                        options = Units.ALL,
                        selected = draft.unit,
                        labelOf = { it },
                        onSelect = { onUpdate(draft.copy(unit = it)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(6.dp))
                // 与手工录入表单一致：可选常用位置，也可自由输入
                ComboField(
                    label = "存放位置",
                    options = customLocations.distinct(),
                    value = draft.location,
                    onValueChange = { onUpdate(draft.copy(location = it)) },
                    placeholder = "可直接输入，如：茶几底下",
                )
                Spacer(Modifier.height(6.dp))
                // 分类：与手工录入表单一致，可选可手填
                ComboField(
                    label = "分类（可选）",
                    options = Categories.ALL,
                    value = draft.category,
                    onValueChange = { onUpdate(draft.copy(category = it)) },
                    placeholder = "可留空，也可直接输入",
                )
                Spacer(Modifier.height(6.dp))
                DateFieldRow(
                    label = "保质期至",
                    value = DateUtils.format(draft.expiryDate),
                    onPick = { dateTarget = "expiry" },
                    onClear = { onUpdate(draft.copy(expiryDate = null)) },
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // 单条入库：不用等其它条目一起提交
                androidx.compose.material3.FilledTonalButton(
                    onClick = onAddSingle,
                    modifier = Modifier.height(36.dp),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("单条入库", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { editing = !editing }) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (editing) "完成" else "修改")
                }
                TextButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("移除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    // 保质期日期选择
    dateTarget?.let { target ->
        FoodDatePickerDialog(
            initialDate = draft.expiryDate ?: DateUtils.today(),
            onDismiss = { dateTarget = null },
            onConfirm = { millis ->
                onUpdate(draft.copy(expiryDate = millis))
                dateTarget = null
            },
        )
    }
}

/**
 * 草稿 -> 数据库实体。
 *
 * **位置留空就存空**：不再用"最近一次的位置"兜底。
 * （原先会套用 settings.lastLocation，导致语音/OCR 没识别出位置时，
 *   被静默改成上次的位置，看起来像"识别错了位置"。）
 */
private fun FoodDraft.toFoodItem(defaultUnit: String): FoodItem = FoodItem(
    name = name.ifBlank { "未命名食物" },
    category = category,
    quantity = if (quantity > 0) quantity else 1.0,
    unit = unit.ifBlank { defaultUnit },
    purchaseDate = purchaseDate,
    productionDate = productionDate,
    expiryDate = expiryDate,
    location = location,
    note = note,
    source = source,
)
