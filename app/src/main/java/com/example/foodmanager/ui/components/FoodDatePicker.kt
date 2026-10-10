package com.example.foodmanager.ui.components

import android.os.Build
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.foodmanager.core.CalendarDate
import com.example.foodmanager.core.DateUtils

/** 三列控件共用于到期、计划食用、入库与生产日期，不要求逐月翻页。 */
@Composable
fun FoodDateSelector(selected: CalendarDate, onChange: (CalendarDate) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(DateUtils.formatCn(selected.toMillis()), style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("date-preview"))
        val distance = DateUtils.daysBetween(DateUtils.today(), selected.toMillis())
        Text(when {
            distance == 0L -> "今天"
            distance > 0 -> "距今天 $distance 天"
            else -> "${-distance} 天前"
        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateWheel("年", "date-year", selected.year, minOf(1900, selected.year), maxOf(2100, selected.year), Modifier.weight(1.3f)) { onChange(selected.withYear(it)) }
            DateWheel("月", "date-month", selected.month, 1, 12, Modifier.weight(1f), wrap = true) { onChange(selected.withMonth(it)) }
            DateWheel("日", "date-day", selected.day, 1, selected.maximumDay, Modifier.weight(1f), wrap = true) { onChange(selected.withDay(it)) }
        }
        Text("滑动年、月、日选择日期", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DateWheel(label: String, tag: String, value: Int, minimum: Int, maximum: Int, modifier: Modifier, wrap: Boolean = false, onChange: (Int) -> Unit) {
    val dark = isSystemInDarkTheme()
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        AndroidView(
            factory = { context -> NumberPicker(ContextThemeWrapper(context, if (dark) android.R.style.Theme_Material else android.R.style.Theme_Material_Light)).apply {
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                wrapSelectorWheel = false
                this.tag = tag
                setOnLongPressUpdateInterval(100)
            } },
            update = { picker ->
                picker.minValue = minimum
                picker.maxValue = maximum
                picker.wrapSelectorWheel = wrap
                picker.contentDescription = "$label：$value"
                if (picker.value != value) picker.value = value
                if (Build.VERSION.SDK_INT >= 29) picker.textColor = textColor
                picker.setOnValueChangedListener { _, _, next -> onChange(next) }
            }, modifier = Modifier.fillMaxWidth().height(156.dp).testTag(tag))
    }
}

@Composable
fun FoodDatePickerDialog(initialDate: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit, title: String = "选择年月日") {
    var selected by rememberSaveable(initialDate) { mutableStateOf(CalendarDate.fromMillis(initialDate)) }
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.testTag("food-date-dialog"),
        title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { FoodDateSelector(selected) { selected = it } } },
        confirmButton = { TextButton(onClick = { onConfirm(selected.toMillis()) }, modifier = Modifier.testTag("date-confirm")) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
