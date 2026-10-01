package com.example.foodmanager.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.foodmanager.core.CameraFiles
import com.example.foodmanager.core.DateUtils
import com.example.foodmanager.core.FoodDraft
import com.example.foodmanager.data.FoodItem
import com.example.foodmanager.ocr.OcrEngine
import com.example.foodmanager.ocr.OcrTextParser
import com.example.foodmanager.ui.components.ManualInputCard
import com.example.foodmanager.ui.components.PhotoInputCard
import com.example.foodmanager.ui.components.VoiceInputCard
import com.example.foodmanager.voice.VoiceTextParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * OCR 批量录入 + 语音批量录入。
 * 流程：拍照/选图 -> 离线识别 -> 解析成草稿 -> 逐条校对 -> 一键入库。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    defaultLocation: String,
    defaultUnit: String,
    customLocations: List<String> = emptyList(),
    customFoods: List<String> = emptyList(),
    /** 当前展开的方式：photo / voice / manual（由上层持有，便于首页快捷入口指定） */
    entryMode: String = "manual",
    onEntryModeChange: (String) -> Unit = {},
    onAddCustomLocation: (String) -> Unit = {},
    onRemoveCustomLocation: (String) -> Unit = {},
    onAddItems: (List<FoodItem>, (Int) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val ocrEngine = remember { OcrEngine() }

    // 三种录入方式由 entryMode 控制，默认手工录入
    val photoExpanded = entryMode == "photo"
    val voiceExpanded = entryMode == "voice"
    val manualExpanded = entryMode == "manual"

    // 手工录入的表单数据（受控）
    var manualDraft by remember { mutableStateOf(blankDraft(defaultLocation, defaultUnit)) }

    var processing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    val drafts = remember { emptyList<FoodDraft>().toMutableStateList() }
    var rawText by remember { mutableStateOf("") }
    var showRaw by remember { mutableStateOf(false) }
    var pendingLocation by remember { mutableStateOf(defaultLocation) }

    var pendingCameraPath by remember { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val path = pendingCameraPath
        if (success && path != null) {
            processing = true
            statusText = "正在识别图片文字…"
            scope.launch {
                runCatching {
                    withContext(Dispatchers.Default) {
                        val bitmap = OcrEngine.loadBitmapFromFile(path)
                        ocrEngine.recognizeBitmap(bitmap)
                    }
                }.onSuccess { text ->
                    rawText = text
                    val parsed = OcrTextParser.parse(text, "ocr")
                    drafts.addAll(parsed)
                    processing = false
                    statusText = if (parsed.isEmpty()) {
                        "没有从图片里识别到食物信息，可尝试重新拍摄或手动添加"
                    } else {
                        "识别到 ${parsed.size} 条，请核对后入库"
                    }
                }.onFailure {
                    processing = false
                    statusText = "识别失败：${it.message}"
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = CameraFiles.createImageFile(context)
            pendingCameraPath = file.absolutePath
            runCatching { cameraLauncher.launch(CameraFiles.uriFor(context, file)) }
                .onFailure { statusText = "无法启动相机：${it.message}" }
        } else {
            statusText = "需要相机权限才能拍照识别"
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            processing = true
            statusText = "正在识别图片文字…"
            scope.launch {
                runCatching { ocrEngine.recognizeAndParse(context, uri) }
                    .onSuccess { result ->
                        rawText = result.rawText
                        drafts.addAll(result.drafts)
                        processing = false
                        statusText = if (result.drafts.isEmpty()) {
                            "没有从图片里识别到食物信息，可展开原文查看识别结果"
                        } else {
                            "识别到 ${result.drafts.size} 条，请核对后入库"
                        }
                    }
                    .onFailure {
                        processing = false
                        statusText = "识别失败：${it.message}"
                    }
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("录入食物") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
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
                            rawText = text
                            statusText = if (parsed.isEmpty()) {
                                "没有从图片里识别到食物信息，可换一张或手工录入"
                            } else {
                                "识别到 ${parsed.size} 条，请核对后入库"
                            }
                            if (parsed.isNotEmpty()) drafts.addAll(parsed)
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
                                scope.launch {
                                    snackbar.showSnackbar("识别到「$text」，但没能解析出食物信息，可手工录入")
                                }
                            } else {
                                drafts.addAll(parsed)
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
                        item = manualDraft,
                        onChange = { manualDraft = it },
                        onSave = {
                            val toSave = manualDraft
                            onAddItems(listOf(toSave)) { count ->
                                statusText = "已入库 $count 条：「${toSave.name}」"
                            }
                            // 保存后清空表单，方便连续录入下一条
                            manualDraft = blankDraft(defaultLocation, defaultUnit)
                        },
                        customLocations = customLocations,
                        onAddCustomLocation = onAddCustomLocation,
                        onRemoveCustomLocation = onRemoveCustomLocation,
                        title = "手工录入",
                        subtitle = "逐项填写食物信息，填完点下方按钮保存",
                        saveLabel = "添加到库存",
                        showAddIcon = true,
                    )
                }
            }

            if (processing) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(statusText)
                    }
                }
            } else if (statusText.isNotBlank()) {
                item {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }
            }

            if (rawText.isNotBlank()) {
                item {
                    TextButton(onClick = { showRaw = !showRaw }) {
                        Text(if (showRaw) "收起识别原文" else "查看识别原文（${rawText.length} 字）")
                    }
                }
                if (showRaw) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Text(
                                text = rawText,
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

                items(drafts.size) { index ->
                    val draft = drafts[index]
                    DraftCard(
                        index = index,
                        draft = draft,
                        onUpdate = { updated -> drafts[index] = updated },
                        onRemove = { drafts.removeAt(index) },
                        onAddSingle = {
                            val item = draft.toFoodItem(
                                pendingLocation.ifBlank { defaultLocation },
                                defaultUnit,
                            )
                            onAddItems(listOf(item)) { count ->
                                drafts.removeAt(index)
                                statusText = "已单独入库 $count 条：「${item.name}」"
                            }
                        },
                    )
                }

                item {
                    Button(
                        onClick = {
                            val items = drafts.map { it.toFoodItem(pendingLocation.ifBlank { defaultLocation }, defaultUnit) }
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

/** 空表单草稿 */
private fun blankDraft(location: String, unit: String) = FoodItem(
    name = "",
    unit = unit.ifBlank { "个" },
    location = location,
    purchaseDate = com.example.foodmanager.core.DateUtils.today(),
    source = "manual",
)

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
    onUpdate: (FoodDraft) -> Unit,
    onRemove: () -> Unit,
    onAddSingle: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }

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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = com.example.foodmanager.ui.components.formatQuantity(draft.quantity),
                        onValueChange = { v -> onUpdate(draft.copy(quantity = v.toDoubleOrNull() ?: draft.quantity)) },
                        label = { Text("数量") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = draft.unit,
                        onValueChange = { onUpdate(draft.copy(unit = it)) },
                        label = { Text("单位") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = draft.location,
                    onValueChange = { onUpdate(draft.copy(location = it)) },
                    label = { Text("存放位置") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = DateUtils.format(draft.expiryDate),
                    onValueChange = { v ->
                        val parsed = DateUtils.parse(v)
                        if (parsed != null || v.isBlank()) onUpdate(draft.copy(expiryDate = parsed))
                    },
                    label = { Text("保质期至（yyyy-MM-dd）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
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
}

/** 草稿 -> 数据库实体 */
private fun FoodDraft.toFoodItem(defaultLocation: String, defaultUnit: String): FoodItem = FoodItem(
    name = name.ifBlank { "未命名食物" },
    category = category,
    quantity = if (quantity > 0) quantity else 1.0,
    unit = unit.ifBlank { defaultUnit },
    purchaseDate = purchaseDate,
    productionDate = productionDate,
    expiryDate = expiryDate,
    location = location.ifBlank { defaultLocation },
    note = note,
    source = source,
)
