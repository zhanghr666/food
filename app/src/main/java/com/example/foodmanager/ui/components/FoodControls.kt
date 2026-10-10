package com.example.foodmanager.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** 固定数量的模式切换与可增长的选择列表分离。 */
@Composable
internal fun FoodSegments(options: List<Pair<String, String>>, value: String, onSelect: (String) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { (key, label) ->
                Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp),
                    color = if (value == key) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    contentColor = if (value == key) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) {
                    Box(Modifier.selectable(selected = value == key, enabled = enabled, role = Role.Tab, onClick = { onSelect(key) })
                        .heightIn(min = 44.dp).padding(horizontal = 4.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
internal fun FoodSearchField(value: String, onValue: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    TextField(value, onValue, modifier = modifier.fillMaxWidth(), singleLine = true,
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(22.dp)) },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onValue("") }) { Icon(Icons.Outlined.Close, "清空搜索") } },
        shape = RoundedCornerShape(12.dp), colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
}

@Composable
internal fun FoodTabs(options: List<String>, value: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        options.forEach { label ->
            Column(Modifier.weight(1f).selectable(value == label, role = Role.Tab, onClick = { onSelect(label) }),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelLarge,
                        color = if (value == label) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.fillMaxWidth().height(2.dp).background(if (value == label) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
            }
        }
    }
}
