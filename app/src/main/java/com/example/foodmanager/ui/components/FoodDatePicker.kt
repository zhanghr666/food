package com.example.foodmanager.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.foodmanager.core.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Material3 日期选择对话框。
 * 编辑页与「录入」页的手工录入卡片共用，保证两处日期选择行为一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodDatePickerDialog(
    initialDate: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val initial = remember(initialDate) { toUtcMidnight(initialDate) }
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = pickerState.selectedDateMillis
                if (millis != null) {
                    val utc = TimeZone.getTimeZone("UTC")
                    val text = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
                        .apply { timeZone = utc }
                        .format(Date(millis))
                    val parts = text.split('-')
                    runCatching {
                        DateUtils.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
                    }.onSuccess(onConfirm).onFailure { onDismiss() }
                } else {
                    onDismiss()
                }
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        DatePicker(state = pickerState)
    }
}

/** 本地日期（当天 0 点） -> UTC 当天 0 点，Material3 DatePicker 要求 UTC 毫秒 */
private fun toUtcMidnight(localDate: Long): Long {
    val text = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(localDate))
    val utc = TimeZone.getTimeZone("UTC")
    return SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).apply { timeZone = utc }
        .parse(text)?.time ?: localDate
}
