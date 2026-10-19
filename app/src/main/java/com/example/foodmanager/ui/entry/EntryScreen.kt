package com.example.foodmanager.ui.entry

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.foodmanager.core.*
import com.example.foodmanager.data.*
import com.example.foodmanager.ui.components.FoodDatePickerDialog
import com.example.foodmanager.ui.components.CommonFoodSheet
import com.example.foodmanager.ui.components.FoodSegments
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryScreen(vm: EntryViewModel, focusPhotos: Boolean = false, onBack: (() -> Unit)? = null) {
    val state by vm.state.collectAsState()
    val locations by vm.locations.collectAsState()
    val foods by vm.foods.collectAsState()
    val draft = state.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var sheet by remember { mutableStateOf("") }
    var optional by rememberSaveable { mutableStateOf(false) }
    var rememberFood by rememberSaveable { mutableStateOf(true) }
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraAwaiting by rememberSaveable { mutableStateOf(false) }
    var candidateDate by remember { mutableStateOf<Long?>(null) }
    var clearConfirm by remember { mutableStateOf(false) }
    val enabled = !state.saving && !state.busy && !state.recording
    val photoFocus = remember { BringIntoViewRequester() }
    LaunchedEffect(focusPhotos, draft.itemId) { if (focusPhotos) { withFrameNanos { }; photoFocus.bringIntoView() } }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { CameraFiles.cleanUpStale(context) } }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.message("麦克风已允许，请重新按住说话") else vm.message("麦克风权限未开启，可以继续使用文字或拍照录入")
    }
    LaunchedEffect(state.mode) { if (state.mode == "voice") vm.prepareVoice() }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        cameraAwaiting = false
        cameraPath?.let { path ->
            if (success) vm.photo(CameraFiles.uriFor(context, File(path)))
            else { File(path).delete(); cameraPath = null }
        }
    }
    fun takePhoto() {
        runCatching {
            val file = CameraFiles.createImageFile(context); cameraPath = file.absolutePath
            cameraAwaiting = true
            camera.launch(CameraFiles.uriFor(context, file))
        }.onFailure { cameraAwaiting = false; vm.message("相机无法启动，请从相册选择照片") }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) takePhoto() else vm.message("相机权限未开启，可以从相册选择照片")
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(vm::photo) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) vm.cancelVoice() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); vm.cancelVoice() }
    }
    LaunchedEffect(state.busy, cameraAwaiting) { if (!state.busy && !cameraAwaiting) cameraPath?.let { path -> File(path).delete(); cameraPath = null } }

    Column(Modifier.fillMaxSize()) {
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                if (onBack != null) IconButton(onBack, enabled = !state.saving && (!state.busy || state.recording)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回食物") }
                Text(if (draft.itemId != 0L) "编辑食物" else "录入食物", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            if (draft.itemId == 0L) {
                FoodSegments(listOf("voice" to "语音", "photo" to "拍照", "text" to "文字", "manual" to "手动"), state.mode, vm::setMode, enabled = enabled)
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (state.mode) {
                            "voice" -> {
                                HoldToTalkButton(recording = state.recording, preparing = state.preparing,
                                    enabled = !state.saving && (!state.busy || state.recording),
                                    onStart = {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                            vm.startVoice(); true
                                        } else { micPermission.launch(Manifest.permission.RECORD_AUDIO); false }
                                    }, onFinish = vm::finishVoice, onCancel = vm::cancelVoice)
                            }
                            "photo" -> {
                                Text("拍食物或完整包装", style = MaterialTheme.typography.titleMedium)
                                Text("顺便读取可见日期，缺失信息可直接补填", style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) takePhoto()
                                        else cameraPermission.launch(Manifest.permission.CAMERA)
                                    }, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Icon(Icons.Outlined.PhotoCamera, null); Text(" 拍照") }
                                    OutlinedButton(onClick = { gallery.launch("image/*") }, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("从相册选") }
                                }
                            }
                            "text" -> Text("输入一句话，自动拆分信息", style = MaterialTheme.typography.titleMedium)
                            else -> Text("直接填下面六项，也可选择常用食品", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (state.mode in listOf("voice", "text")) {
                            OutlinedTextField(state.input, vm::setInput, label = { Text(if (state.mode == "voice") "识别原文，可修改" else "一句话录入") },
                                placeholder = { Text("例如：三盒酸奶放冰箱，五天后到期", style = MaterialTheme.typography.bodySmall) },
                                minLines = 1, maxLines = 3, shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
                                enabled = !state.recording && !state.saving && !state.busy, modifier = Modifier.fillMaxWidth().testTag("entry-input"))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.weight(1f)) {
                                    if (state.canParseInput) TextButton(onClick = { focusManager.clearFocus(); vm.parseInput() }, enabled = enabled,
                                        modifier = Modifier.testTag("entry-reparse")) {
                                        Text(if (state.hasParsedInput) "按修改后的文字重新识别" else "按文字识别", style = MaterialTheme.typography.bodySmall)
                                    } else Text("修改文字后可重新识别", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("entry-reparse-hint"))
                                }
                                EntryClearButton(state, enabled) { clearConfirm = true }
                            }
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                EntryClearButton(state, enabled) { clearConfirm = true }
                            }
                        }
                    }
                }
                if (foods.isNotEmpty() && draft.completeCount == 0) {
                    OutlinedButton(onClick = { focusManager.clearFocus(); sheet = "common-food" }, enabled = enabled,
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("选择常买食品", style = MaterialTheme.typography.titleSmall)
                            Text("${foods.size} 种 · 只沿用稳定信息", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null)
                    }
                }
            }
            if (state.message.isNotBlank()) Text(state.message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            if (!draft.photosLoaded && !state.busy) TextButton(vm::retryPhotos) { Text("重试照片加载") }
            if (state.busy || state.saving || state.preparing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.drafts.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.drafts.forEachIndexed { index, entry -> FilterChip(state.selected == index, { vm.select(index) }, enabled = enabled, label = { Text("${index + 1}. ${entry.name.ifBlank { "新食物" }}") }) }
                    TextButton(onClick = vm::removeCurrent, enabled = enabled, modifier = Modifier.testTag("entry-remove-current")) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                        Text("删除当前项")
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("核对信息", style = MaterialTheme.typography.titleMedium)
                Text("已填写 ${draft.completeCount}/6${if (draft.pendingCount > 0) " · 待核对 ${draft.pendingCount} 项" else ""}", style = MaterialTheme.typography.labelMedium,
                    color = if (draft.pendingCount > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
            }
            if (draft.expiryDate == null && draft.dateCandidates.isNotEmpty()) {
                Text("照片读到的日期 · 点选后确认用途", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    draft.dateCandidates.forEach { value -> AssistChip(onClick = { candidateDate = value }, enabled = !state.saving, label = { Text(DateUtils.format(value)) }) }
                }
            }
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    EntryFormRow(draft, EntryField.NAME) {
                        EntryEditableValue(draft.name, { vm.edit(EntryField.NAME, it) }, "食物名称", !state.saving, Modifier.testTag("entry-name"))
                    }
                    NameCandidates(draft, !state.saving, vm::edit, vm::acceptFoodSuggestion)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.weight(1f)) {
                            EntryFormRow(draft, EntryField.QUANTITY, labelWidth = 44.dp) {
                                EntryEditableValue(draft.quantity, { vm.edit(EntryField.QUANTITY, it) }, "数量", !state.saving,
                                    Modifier.testTag("entry-quantity"), KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            EntryFormRow(draft, EntryField.UNIT, labelWidth = 36.dp) {
                                EntryChoiceValue(draft.unit, "选择", !state.saving, { sheet = "unit" })
                            }
                        }
                    }
                    FieldCandidates(draft, EntryField.QUANTITY, !state.saving, vm::confirmCandidate)
                    FieldCandidates(draft, EntryField.UNIT, !state.saving, vm::confirmCandidate)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    EntryFormRow(draft, EntryField.LOCATION) {
                        EntryEditableValue(draft.location, { vm.edit(EntryField.LOCATION, it) }, "直接填写位置", !state.saving, Modifier.testTag("entry-location"))
                    }
                    if (locations.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { focusManager.clearFocus(); sheet = "location" }, enabled = !state.saving,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) {
                                Text("选择常用位置", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    FieldCandidates(draft, EntryField.LOCATION, !state.saving, vm::confirmCandidate)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    EntryFormRow(draft, EntryField.DATE) {
                        EntryChoiceValue(draft.expiryDate?.let { "${if (draft.dateKind == "reminder") "计划 " else ""}${DateUtils.format(it)}" }.orEmpty(),
                            "选择日期 / 保质期", !state.saving, { sheet = "date" })
                        draft.expiryDate?.let { date ->
                            val days = DateUtils.daysBetween(DateUtils.today(), date)
                            val description = if (draft.dateKind == "reminder") when {
                                days < 0 -> "食用计划已过 ${-days} 天"
                                days == 0L -> "今天计划食用"
                                else -> "$days 天后计划食用"
                            } else DateUtils.describeRemaining(days)
                            Text(description,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    FieldCandidates(draft, EntryField.DATE, !state.saving, vm::confirmCandidate)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    EntryFormRow(draft, EntryField.CATEGORY) {
                        EntryChoiceValue(draft.category, "选择分类", !state.saving, { sheet = "category" }, Modifier.testTag("entry-category"))
                    }
                    FieldCandidates(draft, EntryField.CATEGORY, !state.saving, vm::confirmCandidate)
                    Box(Modifier.bringIntoViewRequester(photoFocus)) {
                        com.example.foodmanager.ui.components.ItemPhotosEditor(draft.photos, com.example.foodmanager.FoodApp.from(context).items.photoStore, enabled,
                            vm::addPhotos, vm::removePhoto, vm::coverPhoto, vm::movePhoto, vm::message)
                    }
                    draft.visibleWarnings.forEach { warning -> Text("· $warning", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(Modifier.fillMaxWidth().testTag("entry-actions"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { optional = !optional }, enabled = !state.saving,
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("entry-optional-toggle")) {
                            Icon(if (optional) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (optional) "收起其他信息" else "其他信息（可选）", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.weight(1f))
                            Text("入库 ${DateUtils.formatShort(draft.purchaseDate)}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (optional) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = { sheet = "purchase" }, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) { Text("入库日期：${DateUtils.format(draft.purchaseDate)}") }
                                OutlinedButton(onClick = { sheet = "production" }, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) { Text("生产日期：${draft.productionDate?.let(DateUtils::format) ?: "未填写"}") }
                                if (draft.shelfLifeAmount != null) Text("保质期：${draft.shelfLifeAmount}${draft.shelfLifeUnit}", style = MaterialTheme.typography.bodySmall)
                                listOf("brand" to "品牌", "specification" to "规格", "price" to "单价（元）", "note" to "备注").forEach { (key, label) ->
                                    val value = when (key) { "brand" -> draft.brand; "specification" -> draft.specification; "price" -> draft.price; else -> draft.note }
                                    OutlinedTextField(value, { vm.extra(key, it) }, label = { Text("$label · 可留空") }, enabled = !state.saving, modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
                                        keyboardOptions = KeyboardOptions(keyboardType = if (key == "price") KeyboardType.Decimal else KeyboardType.Text))
                                }
                                if (draft.rawText.isNotBlank()) Text("识别原文：\n${draft.rawText}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(rememberFood, { rememberFood = it }, enabled = !state.saving, modifier = Modifier.testTag("entry-remember"))
                            Text("记住常用信息", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (draft.itemId != 0L) EntryClearButton(state, enabled) { clearConfirm = true }
                            if (draft.itemId == 0L) TextButton(onClick = vm::newItem, enabled = enabled, modifier = Modifier.testTag("entry-add-another")) {
                                Icon(Icons.Outlined.Add, contentDescription = null)
                                Text("再加一件")
                            }
                        }
                    }
                }
            }
    }
    if (onBack != null || state.dirty || state.saving) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state.validationMessage.isNotBlank()) Text(state.validationMessage, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().testTag("entry-validation-error"))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (onBack != null) OutlinedButton(onBack, enabled = enabled, shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("entry-footer-cancel")) { Text("取消") }
                    Button(onClick = { focusManager.clearFocus(); vm.save(rememberFood) }, enabled = enabled && vm.hasUnsavedChanges(), shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("entry-save")) {
                        Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.saving) "正在保存" else "保存")
                    }
                }
            }
        }
    }
    }
    state.saveReview?.let { review ->
        AlertDialog(onDismissRequest = vm::cancelSaveReview,
            modifier = Modifier.testTag("entry-save-review"),
            title = { Text("确认当前内容后保存") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("以下内容尚未逐项确认。若当前填写值正确，确认后即可保存；需要调整可返回修改。")
                    review.drafts.filter { it.pendingCount > 0 }.forEach { item ->
                        Text(item.name, style = MaterialTheme.typography.titleSmall)
                        EntryField.entries.filter(item::needsConfirmation).forEach { field ->
                            val value = when (field) {
                                EntryField.NAME -> item.name
                                EntryField.QUANTITY -> item.quantity
                                EntryField.UNIT -> item.unit
                                EntryField.LOCATION -> item.location
                                EntryField.CATEGORY -> item.category
                                EntryField.DATE -> "${if (item.dateKind == "reminder") "计划食用" else "到期"} · ${DateUtils.format(item.expiryDate)}"
                            }
                            Text("${field.label}：$value", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = vm::confirmSaveReview, modifier = Modifier.testTag("entry-confirm-save")) { Text("确认并保存") } },
            dismissButton = { TextButton(onClick = vm::cancelSaveReview) { Text("返回修改") } })
    }
    when (sheet) {
        "common-food" -> CommonFoodSheet(foods, enabled, onPick = { food -> vm.useTemplate(food); sheet = "" }, onDismiss = { sheet = "" })
        "location" -> ChoiceSheet("常用位置", locations.map { it.name }, draft.location, { vm.edit(EntryField.LOCATION, it) }, { sheet = "" })
        "unit" -> ChoiceSheet("选择单位", Units.ALL + "箱", draft.unit, { vm.edit(EntryField.UNIT, it) }, { sheet = "" })
        "category" -> ChoiceSheet("食物大类", Categories.ALL, draft.category, { vm.edit(EntryField.CATEGORY, it) }, { sheet = "" })
        "date" -> DateSheet(draft, vm) { sheet = "" }
        "purchase", "production" -> FoodDatePickerDialog(if (sheet == "purchase") draft.purchaseDate else draft.productionDate ?: DateUtils.today(), { sheet = "" }, { date ->
            if (sheet == "purchase") vm.purchaseDate(date) else vm.productionDate(date); sheet = ""
        }, title = if (sheet == "purchase") "选择入库日期" else "选择生产日期")
    }
    candidateDate?.let { date -> AlertDialog(onDismissRequest = { candidateDate = null }, title = { Text(DateUtils.format(date)) }, text = { Text("请根据包装确认这是哪一种日期") },
        confirmButton = { TextButton(onClick = { vm.date(date, "expiry"); candidateDate = null }) { Text("这是到期日") } },
        dismissButton = { TextButton(onClick = { vm.productionDate(date); candidateDate = null; sheet = "date" }) { Text("这是生产日") } }) }
    if (clearConfirm) AlertDialog(onDismissRequest = { clearConfirm = false }, title = { Text(if (state.drafts.size > 1) "清空全部未保存内容？" else "清空本次录入？") }, text = { Text(if (state.drafts.size > 1) "所有尚未保存的内容会清空。只删除错误项，请使用“删除当前项”。" else "当前尚未保存的内容会清空。") },
        confirmButton = { TextButton(onClick = { focusManager.clearFocus(); vm.clear(); clearConfirm = false }, modifier = Modifier.testTag("entry-confirm-clear")) { Text("清空") } }, dismissButton = { TextButton(onClick = { clearConfirm = false }) { Text("继续填写") } })
}

@Composable
private fun EntryClearButton(state: EntryState, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled && state.dirty, modifier = Modifier.testTag("entry-clear")) {
        Text(if (state.drafts.size > 1) "清空全部" else "清空")
    }
}
