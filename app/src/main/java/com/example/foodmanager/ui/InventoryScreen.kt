package com.example.foodmanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.foodmanager.data.FoodItem
import com.example.foodmanager.ui.components.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(viewModel: FoodViewModel, onAddManual: () -> Unit,
    onOpenScan: () -> Unit, onOpenItem: (Long) -> Unit) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val location by viewModel.locationFilter.collectAsStateWithLifecycle()
    val status by viewModel.statusFilter.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<FoodItem?>(null) }
    var quantityItem by remember { mutableStateOf<FoodItem?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun message(text: String) { scope.launch { snackbar.showSnackbar(text) } }
    fun archive(item: FoodItem) {
        if (busy) return
        busy = true
        viewModel.setArchived(item.id, true) { result ->
            busy = false
            if (result.isFailure) message("归档失败，请重试")
            else scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                if (snackbar.showSnackbar("已归档「${item.name}」", "撤销", withDismissAction = true,
                        duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
                    viewModel.setArchived(item.id, false) { if (it.isFailure) message("恢复失败，请在设置的归档中重试") }
                }
            }
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(if (query.isBlank() && location.isBlank() && status == null)
                        "库存（${items.size}）" else "库存（筛出 ${items.size}）") },
                    actions = { IconButton(onClick = onOpenScan) { Icon(Icons.Default.Add, "拍照录入") } },
                )
                OutlinedTextField(value = query, onValueChange = viewModel::onQueryChange,
                    placeholder = { Text("搜索名称、分类、位置、备注") }, singleLine = true,
                    trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(Icons.Default.Clear, "清除搜索") } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
                if (status != null) InputChip(selected = true, onClick = { viewModel.statusFilter.value = null },
                    label = { Text("${status!!.label} · 点击清除筛选") }, modifier = Modifier.padding(horizontal = 12.dp))
                if (locations.isNotEmpty()) {
                    androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item { FilterChip(selected = location.isBlank(), onClick = { viewModel.onLocationFilterChange("") }, label = { Text("全部位置") }) }
                        items(locations) { loc -> FilterChip(selected = location == loc,
                            onClick = { viewModel.onLocationFilterChange(loc) }, label = { Text(loc) }) }
                    }
                }
            }
        },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = onAddManual,
            modifier = Modifier.testTag("add-food"), icon = { Icon(Icons.Default.Add, null) }, text = { Text("添加食物") }) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 100.dp)) {
            if (items.isEmpty()) item { EmptyState("没有符合条件的记录", "添加食物，或清除搜索与筛选后查看。") }
            items(items, key = { it.id }) { item ->
                FoodCard(item, settings.warnDays, today, onClick = { onOpenItem(item.id) },
                    onQuantityChange = { if (!busy) quantityItem = item }, onEdit = { onOpenItem(item.id) },
                    onDelete = { if (!busy) pendingDelete = item }, onArchive = { archive(item) })
            }
        }
    }
    pendingDelete?.let { item ->
        AlertDialog(onDismissRequest = { if (!busy) pendingDelete = null }, title = { Text("永久删除记录？") },
            text = { Text("「${item.name}」删除后无法恢复。吃完或丢弃可以使用归档。") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true
                viewModel.delete(item.id) { result ->
                    busy = false
                    if (result.isSuccess) { pendingDelete = null; message("记录已删除") }
                    else message("删除失败，请重试")
                }
            }) { Text("永久删除") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { pendingDelete = null }) { Text("取消") } })
    }
    quantityItem?.let { item ->
        QuantityDialog(item, onDismiss = { quantityItem = null }, onSave = { value, done ->
            viewModel.setQuantity(item.id, value, done)
        }, onSaved = { zero ->
            quantityItem = null
            if (zero) scope.launch {
                if (snackbar.showSnackbar("剩余数量为 0，是否归档？", "归档", withDismissAction = true,
                        duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) archive(item)
            } else message("余量已保存")
        })
    }
}

@Composable
private fun QuantityDialog(item: FoodItem, onDismiss: () -> Unit,
    onSave: (Double?, (Result<Unit>) -> Unit) -> Unit, onSaved: (Boolean) -> Unit) {
    var text by rememberSaveable(item.id) { mutableStateOf(if (item.quantitySpecified) formatQuantity(item.quantity) else "") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val value = text.toDoubleOrNull()
    val valid = text.isBlank() || (value != null && value.isFinite() && value >= 0)
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text("修改剩余数量") },
        text = { Column {
            Text(item.name)
            OutlinedTextField(value = text, onValueChange = { text = it; error = "" }, enabled = !saving,
                label = { Text("剩余数量（${item.unit}）") }, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                isError = !valid || error.isNotBlank(), supportingText = { Text(error.ifBlank { "支持小数；留空表示未填写，0 表示已用完。" }) })
        } },
        confirmButton = { TextButton(enabled = valid && !saving, onClick = {
            saving = true
            onSave(if (text.isBlank()) null else value) {
                saving = false
                if (it.isSuccess) onSaved(value == 0.0 && text.isNotBlank()) else error = "保存失败，内容已保留，请重试"
            }
        }) { Text(if (saving) "保存中…" else "保存余量") } },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("取消") } })
}
