package com.example.foodmanager.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.foodmanager.core.DateUtils
import com.example.foodmanager.data.FoodItem
import com.example.foodmanager.data.Units

/** 录入页手工表单状态；仅在实际写库成功后复位。编辑页使用 EditorViewModel。 */
class ManualEntryState(
    initialUnit: String = Units.DEFAULT,
    initialItem: FoodItem? = null,
) {

    /** 表单当前内容。位置**不预填**，由用户自己选或填。 */
    var item by mutableStateOf(initialItem ?: blankItem(initialUnit))
        private set
    var protectedFields: Set<String> = initialItem?.let { RecognitionMerge.existing(it) }.orEmpty()
        private set

    /** 每次保存成功后 +1，用于让卡片内部状态（数量框、下拉）一并复位 */
    var resetToken by mutableIntStateOf(0)
        private set

    /** 表单内的状态提示 */
    var status by mutableStateOf("")
        private set

    /** 提示是否为"提醒"样式（橙色） */
    var statusIsError by mutableStateOf(false)
        private set

    fun update(newItem: FoodItem) {
        protectedFields = protectedFields + RecognitionMerge.changed(item, newItem)
        item = newItem
    }

    fun restore(value: FoodItem, fields: Set<String>) {
        item = value
        protectedFields = fields
    }

    /** 用识别/解析结果填充表单（只覆盖非空字段） */
    fun mergeFromDraft(
        draft: com.example.foodmanager.core.FoodDraft,
        source: String,
    ) {
        item = RecognitionMerge.apply(item, draft, protectedFields).copy(source = source)
    }

    /** 把一段文本带进名称框（识别失败时的兜底） */
    fun prefillName(text: String, unit: String) {
        item = blankItem(unit).copy(name = text.trim().take(20))
        protectedFields = emptySet()
        resetToken++
    }

    /** 保存成功后复位：清空全部字段、关闭提示、并复位卡片内部状态 */
    fun resetAfterSave(unit: String) {
        item = blankItem(unit)
        protectedFields = emptySet()
        resetToken++
        // 顺序：先清提示，再由调用方写入成功文案（避免被这里覆盖）
        status = ""
        statusIsError = false
    }

    fun setStatus(message: String, isError: Boolean = false) {
        status = message
        statusIsError = isError
    }

    companion object {
        fun blankItem(unit: String) = FoodItem(
            name = "",
            unit = unit.ifBlank { Units.DEFAULT },
            location = "",
            purchaseDate = DateUtils.today(),
            source = "manual",
        )
    }
}

/** 位置为空时弹窗确认的对话框（仅编辑页使用） */
@Composable
fun LocationConfirmDialog(
    itemName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("还没有填写存放位置") },
        text = {
            Text(
                "填写存放位置后，可以按位置快速查找食物，语音录入时也更容易自动识别。\n\n" +
                    "现在「${itemName.ifBlank { "该食物" }}」的位置是空的，确定要这样保存吗？",
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("回去填写") }
        },
        dismissButton = {
            TextButton(onClick = onConfirm) { Text("仍要保存") }
        },
    )
}

