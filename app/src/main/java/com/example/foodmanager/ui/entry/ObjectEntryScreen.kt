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
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.foodmanager.FoodApp
import com.example.foodmanager.core.ObjectTextParser
import com.example.foodmanager.ui.components.*

@Composable
fun ObjectEntryScreen(vm: ObjectEntryViewModel, focusPhotos: Boolean = false, onBack: () -> Unit) {
    val state by vm.state.collectAsState(); val locations by vm.locations.collectAsState()
    val draft = state.draft; val context = LocalContext.current; val focus = LocalFocusManager.current
    val enabled = !state.busy && !state.recording && !state.saving
    val photoFocus = remember { BringIntoViewRequester() }
    LaunchedEffect(focusPhotos, draft.id) { if (focusPhotos) { withFrameNanos { }; photoFocus.bringIntoView() } }
    var sheet by remember { mutableStateOf("") }; var clear by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.message(if (it) "麦克风已允许，请重新按住说话" else "未开启麦克风，可以用文字录入") }
    LaunchedEffect(draft.mode) { if (draft.mode == "voice") vm.prepareVoice() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) vm.cancelVoice() }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer); vm.cancelVoice() }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack, enabled = !state.saving && (!state.busy || state.recording)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回物品") }
                Text(if (draft.id == 0L) "录入物品" else "编辑物品", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            FoodSegments(listOf("voice" to "语音", "text" to "文字"), draft.mode, vm::mode, enabled = enabled)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (draft.mode == "voice") HoldToTalkButton(state.recording, state.preparing, !state.saving && (!state.busy || state.recording), {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { vm.startVoice(); true }
                        else { permission.launch(Manifest.permission.RECORD_AUDIO); false }
                    }, vm::finishVoice, vm::cancelVoice)
                    OutlinedTextField(draft.input, vm::input, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        label = { Text(if (draft.mode == "voice") "识别原文，可修改" else "一句话录入") },
                        placeholder = { Text("三节五号电池放在客厅抽屉里", style = MaterialTheme.typography.bodySmall) }, minLines = 1, maxLines = 4)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (draft.input.isNotBlank() && draft.input != draft.lastParsed) TextButton({ focus.clearFocus(); vm.parse() }, enabled = enabled, modifier = Modifier.weight(1f)) {
                            Text(if (draft.lastParsed != null) "按修改后的文字重新识别" else "按文字识别")
                        } else Text("修改文字后可重新识别", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        TextButton({ clear = true }, enabled = enabled && draft.dirty) { Text("清空") }
                    }
                }
            }
            if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            if (!draft.photosLoaded && !state.busy) TextButton(vm::retryPhotos) { Text("重试照片加载") }
            if (state.busy || state.preparing) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("核对信息", style = MaterialTheme.typography.titleMedium)
            Text("名称和位置必填；其他信息可用默认值或留空", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    ObjectFieldRow("物品名称 *", draft.name, { vm.edit("name", it) }, "物品名称", enabled)
                    HorizontalDivider()
                    ObjectFieldRow("存放位置 *", draft.location, { vm.edit("location", it) }, "直接填写位置", enabled)
                    if (locations.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton({ sheet = "location" }, enabled = enabled) { Text("选择常用位置") } }
                    HorizontalDivider()
                    ObjectFieldRow("数量", draft.quantity, { vm.edit("quantity", it) }, "数量", enabled, KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Text(if ("quantity" in draft.recognized) "已识别数量" else if ("quantity" !in draft.protected) "默认 1 件，可修改" else "已填写数量", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ObjectFieldRow("单位", draft.unit, { vm.edit("unit", it) }, "默认件", enabled)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton({ sheet = "unit" }, enabled = enabled) { Text("常用单位") } }
                    HorizontalDivider()
                    ObjectFieldRow("物品分类", draft.category, { vm.edit("category", it) }, "未分类", enabled)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton({ sheet = "category" }, enabled = enabled) { Text("选择分类") } }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.bringIntoViewRequester(photoFocus)) {
                        ItemPhotosEditor(draft.photos, FoodApp.from(context).items.photoStore, enabled, vm::addPhotos, vm::removePhoto, vm::coverPhoto, vm::movePhoto, vm::message)
                    }
                    HorizontalDivider()
                    Text("其他信息（可选）", style = MaterialTheme.typography.titleSmall)
                    ObjectFieldRow("品牌", draft.brand, { vm.edit("brand", it) }, "未填写", enabled)
                    ObjectFieldRow("型号 / 规格", draft.specification, { vm.edit("specification", it) }, "未填写", enabled)
                    ObjectFieldRow("价格", draft.price, { vm.edit("price", it) }, "未填写", enabled, KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(draft.note, { vm.edit("note", it) }, label = { Text("备注") }, enabled = enabled, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5)
                }
            }
        }
        Surface(shadowElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state.validationMessage.isNotBlank()) Text(state.validationMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onBack, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("entry-footer-cancel"), shape = RoundedCornerShape(12.dp)) { Text("取消") }
                Button({ focus.clearFocus(); vm.save() }, enabled = enabled && !state.loadFailed && vm.hasUnsavedChanges(), modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("entry-save"), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Outlined.Save, null, Modifier.size(20.dp)); Text(if (state.saving) " 正在保存" else " 保存")
                }
            }
            }
        }
    }
    when(sheet) {
        "location" -> ChoiceSheet("常用位置", locations.map { it.name }, draft.location, { vm.edit("location", it) }, { sheet = "" })
        "unit" -> ChoiceSheet("物品单位", ObjectTextParser.units, draft.unit, { vm.edit("unit", it) }, { sheet = "" })
        "category" -> ChoiceSheet("物品分类", listOf("未分类", "日用品", "工具", "数码电器", "衣物", "文具", "证件", "药品", "其他"), draft.category, { vm.edit("category", it) }, { sheet = "" })
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("清空这次物品录入？") }, text = { Text("仅清空本次录入，已保存库存不受影响。") },
        confirmButton = { TextButton({ vm.clear(); clear = false }) { Text("清空") } }, dismissButton = { TextButton({ clear = false }) { Text("继续填写") } })
}

@Composable
private fun ObjectFieldRow(label: String, value: String, onChange: (String) -> Unit, placeholder: String, enabled: Boolean, keyboard: KeyboardOptions = KeyboardOptions.Default) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(88.dp))
        Box(Modifier.weight(1f)) { EntryEditableValue(value, onChange, placeholder, enabled, keyboardOptions = keyboard) }
    }
}
