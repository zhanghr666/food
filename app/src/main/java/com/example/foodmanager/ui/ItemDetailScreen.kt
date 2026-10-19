package com.example.foodmanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.foodmanager.core.*
import com.example.foodmanager.data.*
import com.example.foodmanager.ui.components.*
import kotlinx.coroutines.launch

/** 阅读与修改分开：首屏摘要，字段级快捷更新，照片不要求存在。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemDetailScreen(item: ManagedItem?, food: FoodItem?, photos: List<String>, store: PhotoStore, today: Long, busy: Boolean,
    onBack: () -> Unit, onEdit: () -> Unit, onArchive: () -> Unit, onDelete: () -> Unit,
    onSameLocation: () -> Unit, onPhotos: () -> Unit, locations: List<String>, warnDays: Int,
    onQuantity: (Double, (Result<Unit>) -> Unit) -> Unit,
    onLocation: (String, (Result<Unit>) -> Unit) -> Unit,
    onDate: (Long, (Result<Unit>) -> Unit) -> Unit, loading: Boolean = false, reminderContent: @Composable () -> Unit = {}) {
    var deleting by rememberSaveable(item?.id) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var sheet by rememberSaveable(item?.id) { mutableStateOf("") }
    var zeroArchive by rememberSaveable(item?.id) { mutableStateOf(false) }
    var sourceExpanded by rememberSaveable(item?.id) { mutableStateOf(false) }
    var viewer by rememberSaveable(item?.id) { mutableStateOf<Int?>(null) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    fun copyText(value: String) { scope.launch { clipboard.setClipEntry(ClipEntry(android.content.ClipData.newPlainText("食物管家", value))) } }
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().testTag("item-detail")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack, enabled = !busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
            Text(if (item?.kind == "food") "食品详情" else "物品详情", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (item != null) Box {
                IconButton({ menu = true }, enabled = !busy) { Icon(Icons.Outlined.MoreHoriz, "更多操作") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text(if (item.archived) "恢复库存" else "归档") }, { menu = false; onArchive() })
                    DropdownMenuItem({ Text("删除记录", color = MaterialTheme.colorScheme.error) }, { menu = false; deleting = true })
                }
            }
        }
        if (item == null) {
            Column(Modifier.fillMaxWidth().weight(1f).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (loading) { CircularProgressIndicator(); Spacer(Modifier.height(16.dp)); Text("正在加载详情…") }
                else { Text("这条记录已删除或暂不可用"); TextButton(onBack) { Text("返回列表") } }
            }
            return@Column
        }
        val notes = remember(item.note) { DetailNotes.from(item.note) }
        val days = food?.daysToExpiry(today)
        val dateColor = when {
            item.archived || food?.dateKind == "reminder" || days == null -> MaterialTheme.colorScheme.onSurfaceVariant
            days < 0 -> MaterialTheme.colorScheme.error
            days <= warnDays -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.onSurface
        }
        val status = when {
            item.archived -> "已归档"
            food?.dateKind == "reminder" -> "食用计划"
            days == null -> "在库"
            days < 0 -> "已过期"
            days <= warnDays -> "临期"
            else -> "在库"
        }
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailCard {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(item.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(item.category.ifBlank { "未分类" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(color = dateColor.copy(alpha = .08f), shape = RoundedCornerShape(6.dp)) {
                        Text(status, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, color = dateColor)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.fillMaxWidth().clickable(enabled = item.location.isNotBlank(), onClick = onSameLocation).testTag("detail-location"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Place, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(" 存放位置", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        if (item.location.isNotBlank()) { Text("查看同位置", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary); Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) }
                    }
                    Text(item.location.ifBlank { "尚未填写位置" }, style = MaterialTheme.typography.titleMedium)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("剩余数量", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (item.kind == "food" && !item.quantitySpecified) "待补齐" else "${formatAmount(item.quantity)} ${item.unit}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        if (item.kind != "food" && !item.quantitySpecified) Text("默认数量", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (item.kind == "food") Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (food?.dateKind == "reminder") "计划食用" else "到期时间", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(food?.dateDescription(today) ?: "正在加载…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = dateColor)
                        food?.expiryDate?.let { Text(DateUtils.formatShort(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.weight(1f)) { TextButton({ sheet = "quantity" }, enabled = !busy, contentPadding = PaddingValues(0.dp)) { Text("修改数量") } }
                    if (item.kind == "food") Box(Modifier.weight(1f)) { TextButton({ sheet = "date" }, enabled = !busy && food != null, contentPadding = PaddingValues(0.dp)) { Text("修改日期") } }
                }
            }
            DetailCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (photos.isEmpty()) "现场照片" else "现场照片 · ${photos.size} 张", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onPhotos, enabled = !busy, contentPadding = PaddingValues(0.dp)) { Text(if (photos.isEmpty()) "添加照片" else "管理照片") }
                }
                if (photos.isEmpty()) Text("拍下物品或摆放现场，下次更好找", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { itemsIndexed(photos, key = { _, key -> key }) { index, key ->
                        Surface(shape = RoundedCornerShape(8.dp), modifier = Modifier.size(96.dp).clickable { viewer = index }) { LocalPhoto(store, key, Modifier.fillMaxSize(), true) }
                    } }
                    TextButton({ viewer = 0 }, contentPadding = PaddingValues(0.dp)) { Text("查看全部照片"); Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp)) }
                }
            }
            reminderContent()
            if (food != null) DetailCard {
                Text("日期信息", style = MaterialTheme.typography.titleSmall)
                DetailValue(if (food.dateKind == "reminder") "计划食用日" else "到期日期", food.expiryDate?.let(DateUtils::formatCn) ?: "未设置", { sheet = "date" }, !busy)
                food.productionDate?.let { DetailValue("生产日期", DateUtils.formatCn(it)) }
                food.shelfLifeAmount?.let { DetailValue("包装保质期", "$it${food.shelfLifeUnit}") }
                if (notes.dateWarnings.isNotEmpty()) Text("录入原文曾有日期提示，可在下方来源中核对", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            DetailCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("其他信息", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onEdit, enabled = !busy, contentPadding = PaddingValues(0.dp)) { Text("补充信息") }
                }
                DetailValue("入库日期", DateUtils.formatCn(item.purchaseDate))
                if (item.brand.isNotBlank()) DetailValue("品牌", item.brand)
                if (item.specification.isNotBlank()) DetailValue("型号 / 规格", item.specification)
                item.price?.let { DetailValue(if (item.kind == "food") "单价" else "价格", "¥${formatAmount(it)}") }
            }
            if (notes.user.isNotBlank()) DetailCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("备注", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton({ copyText(notes.user) }, contentPadding = PaddingValues(0.dp)) { Text("复制") }
                }
                Text(notes.user, style = MaterialTheme.typography.bodyMedium)
            }
            DetailCard {
                Row(Modifier.fillMaxWidth().clickable { sourceExpanded = !sourceExpanded }, verticalAlignment = Alignment.CenterVertically) {
                    Text("录入来源", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(when(item.source) { "voice" -> "语音录入"; "text" -> "文字录入"; "ocr", "image", "photo" -> "照片识别"; "smart" -> "智能录入"; "manual" -> "手动录入"; else -> "已保存记录" }, style = MaterialTheme.typography.bodySmall)
                    Icon(if (sourceExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(20.dp))
                }
                if (sourceExpanded) {
                    DetailValue("最近更新", DateUtils.formatDateTime(item.updatedAt))
                    notes.dateWarnings.forEach { Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall) }
                    if (notes.source.isNotBlank()) { Text("识别原文", style = MaterialTheme.typography.labelMedium); Text(notes.source, style = MaterialTheme.typography.bodySmall)
                        TextButton({ copyText(notes.source) }, contentPadding = PaddingValues(0.dp)) { Text("复制原文") }
                    }
                }
            }
        }
        Surface(shadowElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton({ sheet = "location" }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp)) { Text("修改位置") }
                Button(onEdit, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp)) { Text("编辑信息") }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (sheet.isNotBlank()) QuickDetailEditor(item, food, sheet, locations, busy, { sheet = "" }) { value, done ->
            when(sheet) {
                "quantity" -> onQuantity(value.toDouble(), { result -> done(result); if (result.isSuccess && value.toDouble() == 0.0 && !item.archived) zeroArchive = true })
                "location" -> onLocation(value, done)
                "date" -> onDate(value.toLong(), done)
            }
        }
        viewer?.let { if (photos.isNotEmpty()) ItemPhotoViewer(photos, it, store) { viewer = null } }
        if (zeroArchive) AlertDialog(onDismissRequest = { zeroArchive = false }, title = { Text("数量已改为 0") }, text = { Text("是否将「${item.name}」归档？归档后仍可恢复。") },
            confirmButton = { TextButton({ zeroArchive = false; onArchive() }, enabled = !busy) { Text("归档") } }, dismissButton = { TextButton({ zeroArchive = false }) { Text("保留在库") } })
        if (deleting) AlertDialog(onDismissRequest = { if (!busy) deleting = false }, title = { Text("删除${item.name}？") }, text = { Text("记录和照片引用将永久删除，无法恢复。暂时不用可以选择归档。") },
            confirmButton = { TextButton({ onDelete() }, enabled = !busy) { Text("永久删除", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton({ deleting = false }, enabled = !busy) { Text("取消") } })
    }
}

@Composable
private fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun DetailValue(label: String, value: String, onClick: (() -> Unit)? = null, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick).heightIn(min = 44.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(84.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (onClick != null) Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickDetailEditor(item: ManagedItem, food: FoodItem?, field: String, locations: List<String>, busy: Boolean,
    onClose: () -> Unit, save: (String, (Result<Unit>) -> Unit) -> Unit) {
    var value by rememberSaveable(item.id, field) { mutableStateOf(if (field == "quantity") if (item.kind == "food" && !item.quantitySpecified) "" else formatAmount(item.quantity) else item.location) }
    var date by rememberSaveable(item.id, field) { mutableStateOf(CalendarDate.fromMillis(food?.expiryDate ?: DateUtils.today())) }
    var error by rememberSaveable(item.id, field) { mutableStateOf("") }
    val valid = when(field) { "quantity" -> value.toDoubleOrNull()?.let { it.isFinite() && it >= 0 } == true; "location" -> value.isNotBlank(); else -> true }
    FoodBottomSheet({ if (!busy) onClose() }, Modifier.testTag("detail-quick-editor")) {
        Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(when(field) { "quantity" -> "修改剩余数量"; "location" -> "修改存放位置"; else -> if (food?.dateKind == "reminder") "修改计划食用日" else "修改到期日期" }, style = MaterialTheme.typography.titleLarge)
            Text(item.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (field == "date") {
                FoodDateSelector(date) { if (!busy) { date = it; error = "" } }
                if (food?.shelfLifeAmount != null) Text("手动日期优先；包装保质期信息保留", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                OutlinedTextField(value, { value = it; error = "" }, modifier = Modifier.fillMaxWidth().testTag("detail-quick-value"), enabled = !busy, singleLine = field == "quantity",
                    label = { Text(if (field == "quantity") "数量（${item.unit}）" else "直接填写位置") }, isError = error.isNotBlank(),
                    keyboardOptions = KeyboardOptions(keyboardType = if (field == "quantity") KeyboardType.Decimal else KeyboardType.Text))
                if (field == "location" && locations.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    locations.distinct().take(5).forEach { place -> SuggestionChip({ value = place; error = "" }, label = { Text(place) }, enabled = !busy) }
                }
                if (field == "quantity") Text("支持小数，0 表示已用完；修改不会改变单位", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (error.isNotBlank()) Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClose, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("detail-quick-cancel"), shape = RoundedCornerShape(12.dp)) { Text("取消") }
        Button({
            val next = if (field == "date") date.toMillis().toString() else value.trim()
            save(next) { result -> if (result.isSuccess) onClose() else error = result.exceptionOrNull()?.message ?: "保存失败，内容已保留，请重试" }
        }, enabled = valid && !busy, modifier = Modifier.weight(2f).heightIn(min = 48.dp).testTag("detail-quick-save"), shape = RoundedCornerShape(12.dp)) { Text(if (busy) "正在保存…" else "保存修改") }
        }
    }
}
