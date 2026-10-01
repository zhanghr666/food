package com.example.foodmanager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.foodmanager.core.DateUtils
import com.example.foodmanager.data.FoodItem
import com.example.foodmanager.ui.components.EmptyState
import com.example.foodmanager.ui.components.FoodCard

/** 库存列表：搜索、按位置筛选、增删改查 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    viewModel: FoodViewModel,
    onAddManual: () -> Unit,
    onOpenScan: () -> Unit,
    onOpenItem: (Long) -> Unit,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val locationFilter by viewModel.locationFilter.collectAsStateWithLifecycle()
    val today = remember { DateUtils.today() }

    var pendingDelete by remember { mutableStateOf<FoodItem?>(null) }
    // 「标记已用完」会从库存移除，先确认一下（可撤销路径：设置 → 查看归档 → 恢复到库存）
    var pendingArchive by remember { mutableStateOf<FoodItem?>(null) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    // 有筛选时把总数也标出来，避免「库存（1）」看起来像"库存只剩 1 件"
                    title = {
                        Text(
                            if (query.isBlank() && locationFilter.isBlank()) {
                                "库存（${items.size}）"
                            } else {
                                "库存（筛出 ${items.size}）"
                            },
                        )
                    },
                    actions = {
                        IconButton(onClick = onOpenScan) {
                            Icon(Icons.Filled.Add, contentDescription = "录入")
                        }
                    },
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = { Text("搜索名称、分类、位置、备注") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "清除")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                )
                if (locations.isNotEmpty()) {
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip(
                            selected = locationFilter.isBlank(),
                            onClick = { viewModel.onLocationFilterChange("") },
                            label = { Text("全部", style = MaterialTheme.typography.labelSmall) },
                        )
                        locations.forEach { loc ->
                            FilterChip(
                                selected = locationFilter == loc,
                                onClick = { viewModel.onLocationFilterChange(loc) },
                                label = { Text(loc, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddManual,
                icon = {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                },
                text = {
                    Text("添加食物", style = MaterialTheme.typography.titleMedium)
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmptyState(
                    title = if (query.isBlank() && locationFilter.isBlank()) "库存是空的" else "没有找到匹配的食物",
                    description = if (query.isBlank() && locationFilter.isBlank()) {
                        "点击右下角「添加食物」手动录入，或用「智能录入」拍照/语音批量添加"
                    } else {
                        "试试换个关键词，或清除位置筛选"
                    },
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
        ) {
            items(items, key = { it.id }) { item ->
                FoodCard(
                    item = item,
                    warnDays = settings.warnDays,
                    today = today,
                    onClick = { onOpenItem(item.id) },
                    onQuantityChange = { delta -> viewModel.changeQuantity(item.id, delta) },
                    onEdit = { onOpenItem(item.id) },
                    onDelete = { pendingDelete = item },
                    onArchive = { pendingArchive = item },
                )
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这条记录？") },
            text = { Text("「${target.name}」将被永久删除，无法恢复。如果只是吃完了，建议用「标记已用完」。") },
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

    pendingArchive?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingArchive = null },
            title = { Text("标记为已用完？") },
            text = {
                Text("「${target.name}」会从库存移到「已用完 / 已丢弃」，需要时可在设置里的归档中恢复。")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setArchived(target.id, true)
                    pendingArchive = null
                }) { Text("标记已用完") }
            },
            dismissButton = {
                TextButton(onClick = { pendingArchive = null }) { Text("取消") }
            },
        )
    }
}
