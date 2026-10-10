package com.example.foodmanager.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.foodmanager.data.CommonFood

/** 常买食品不在录入页平铺；完整记录在有界、可搜索的列表内选择。 */
@Composable
internal fun CommonFoodSheet(foods: List<CommonFood>, enabled: Boolean, onPick: (CommonFood) -> Unit, onDismiss: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    val query = search.trim()
    val matching = foods.filter { food -> query.isBlank() ||
        (listOf(food.name, food.category, food.location) + food.aliasList).any { it.contains(query, ignoreCase = true) } }
    val listState = rememberLazyListState()
    LaunchedEffect(query) { listState.scrollToItem(0) }
    FoodBottomSheet(onDismiss = onDismiss) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("常买食品", style = MaterialTheme.typography.titleLarge)
                Text("数量和日期需要重新填写", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "关闭常买食品") }
        }
        Spacer(Modifier.height(16.dp))
        FoodSearchField(search, { search = it }, "搜索名称、分类或位置")
        Text(if (query.isBlank()) "已记住 ${foods.size} 种食品" else "找到 ${matching.size} 种食品",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 420.dp), state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 4.dp)) {
            if (matching.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("没有找到这个食品", style = MaterialTheme.typography.bodyMedium)
                    Text("换个关键词，或返回直接录入", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
            items(matching, key = { it.id }) { food ->
                Card(onClick = { onPick(food) }, enabled = enabled, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(food.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val details = listOf(food.category, food.location, food.unit).filter(String::isNotBlank).joinToString(" · ")
                            Text(details.ifBlank { "沿用名称，其他信息可补充" }, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
