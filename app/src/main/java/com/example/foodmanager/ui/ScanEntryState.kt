package com.example.foodmanager.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.foodmanager.core.DateUtils
import com.example.foodmanager.core.FoodDraft
import com.example.foodmanager.data.FoodItem
import com.example.foodmanager.data.Units

/**
 * 「录入」页的界面状态。
 *
 * **挂在 FoodsApp 层级**（比底部标签页活得久），因此：
 * 切到「首页 / 库存 / 设置」再回到「录入」时，
 * 手工录入填了一半的表单、以及「待入库」的若干草稿**都还在**。
 *
 * 为什么不用 `remember` 放在 ScanScreen 里：那样一离开标签页，
 * 组合被销毁，用户填的内容和刚识别出的待入库记录会全部丢失。
 *
 * 注意：这里只保存**界面状态**，真正的入库动作仍由 FoodViewModel 完成。
 * 进程被系统回收后状态仍会丢失（这是"记住到磁盘"的范畴，暂不处理）。
 */
class ScanEntryState {

    /** 待入库草稿（拍照 / 语音产生；手工录入不经过这里） */
    val drafts = mutableStateListOf<FoodDraft>()

    /** 手工录入表单内容。位置不预填，由用户自己选或填。 */
    var manualDraft by mutableStateOf(blankItem(Units.DEFAULT))

    /** 父级每次重置表单时 +1，用于让卡片内部状态（数量框、下拉）一并复位 */
    var manualResetToken by mutableIntStateOf(0)

    /** 表单内的状态提示（成功 / 被拦下） */
    var manualStatus by mutableStateOf("")

    /** 状态提示是否为"提醒"样式 */
    var manualStatusIsError by mutableStateOf(false)

    /** 拍照识别原文（用于"查看识别原文"） */
    var rawText by mutableStateOf("")

    fun addDrafts(newDrafts: List<FoodDraft>) {
        if (newDrafts.isNotEmpty()) drafts.addAll(newDrafts)
    }

    /** 手工录入保存后复位：清空全部字段，并把卡片内部状态一起复位 */
    fun resetManual(unit: String) {
        manualDraft = blankItem(unit)
        manualResetToken++
    }

    /** 把一段文本带进名称框（解析失败时的兜底） */
    fun prefillName(text: String, unit: String) {
        manualDraft = blankItem(unit).copy(name = text.trim().take(20))
        manualResetToken++
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
