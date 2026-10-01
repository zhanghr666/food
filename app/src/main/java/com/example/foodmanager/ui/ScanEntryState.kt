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

    /**
     * 手工录入表单状态：与编辑页**共用同一个状态机** [ManualEntryState]。
     * 这样两处的"位置为空怎么处理""保存后表单怎么办"不会再各写一套。
     */
    val manual = ManualEntryState()

    /** 手工录入表单内容（转发到 [manual]，位置不预填） */
    var manualDraft: FoodItem
        get() = manual.item
        set(value) = manual.update(value)

    /** 父级每次重置表单时 +1，用于让卡片内部状态（数量框、下拉）一并复位 */
    val manualResetToken: Int get() = manual.resetToken

    /** 表单内的状态提示（成功 / 被拦下） */
    var manualStatus: String
        get() = manual.status
        set(value) {
            manual.setStatus(value, manual.statusIsError)
        }

    /** 状态提示是否为"提醒"样式 */
    var manualStatusIsError: Boolean
        get() = manual.statusIsError
        set(value) {
            manual.setStatus(manual.status, value)
        }

    /** 拍照识别原文（用于"查看识别原文"） */
    var rawText by mutableStateOf("")

    /**
     * 诊断信息：上次拍照识别的**原始条数**与解析条数。
     *
     * 用于区分「识别出多条」和「解析出多条」——
     * 用户反馈"一张照片出现两条"时，这两个数字能直接定位问题在哪一步。
     */
    var lastParsedCount by mutableIntStateOf(-1)
    var lastSingleItemMode by mutableStateOf(true)

    fun addDrafts(newDrafts: List<FoodDraft>) {
        if (newDrafts.isNotEmpty()) drafts.addAll(newDrafts)
    }

    /** 记录一次拍照识别的结果，供设置页展示诊断信息 */
    fun recordOcrResult(parsedCount: Int, singleItemMode: Boolean) {
        lastParsedCount = parsedCount
        lastSingleItemMode = singleItemMode
    }

    /** 手工录入保存后复位：清空全部字段，并把卡片内部状态一起复位 */
    fun resetManual(unit: String) {
        manual.resetAfterSave(unit)
    }

    /** 把一段文本带进名称框（解析失败时的兜底） */
    fun prefillName(text: String, unit: String) {
        manual.prefillName(text, unit)
    }

    companion object {
        fun blankItem(unit: String) = ManualEntryState.blankItem(unit)
    }
}
