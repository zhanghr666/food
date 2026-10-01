package com.example.foodmanager.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.foodmanager.core.DateUtils
import com.example.foodmanager.core.FoodDraft
import com.example.foodmanager.data.FoodItem
import com.example.foodmanager.data.Locations
import com.example.foodmanager.data.Units

/**
 * 「录入」页的界面状态。
 *
 * 之所以单独抽一个 ViewModel：它在 `FoodsApp` 层创建，生命周期比标签页长，
 * 所以切到首页再切回来时**待入库列表和手工录入表单都不会丢**（用户要求"自动保存"）。
 *
 * 注意这里只放**界面状态**，真正的入库动作仍然通过 [FoodViewModel.addDrafts] 完成。
 */
class ScanViewModel : ViewModel() {

    /** 当前展开的录入方式：photo / voice / manual */
    var entryMode by mutableStateOf(MANUAL)
        private set

    /** 是否已经用设置里记住的方式初始化过 */
    var modeInitialized by mutableStateOf(false)
        private set

    /** 待入库草稿（拍照 / 语音产生，手工录入不经过这里） */
    val drafts = mutableStateListOf<FoodDraft>()

    /** 手工录入的表单内容 */
    var manualDraft by mutableStateOf(blankItem())
        private set

    /**
     * 表单重置计数。父级每重置一次表单就 +1，
     * 让表单内部的输入框（如数量）知道该清空了。
     */
    var manualResetToken by mutableIntStateOf(0)
        private set

    /** 是否已经用外部设置初始化过默认位置/单位 */
    private var initialized = false

    /** 拍照卡片的独立状态 */
    var photoStatus by mutableStateOf("")
    var photoRawText by mutableStateOf("")

    /** 语音卡片的独立状态 */
    var voiceStatus by mutableStateOf("")

    /** 手工录入卡片的独立状态 */
    var manualStatus by mutableStateOf("")

    /** 语音/拍照解析失败时暂存的原文，供「改用手工补填」使用 */
    var pendingFallbackText by mutableStateOf("")

    val effectiveMode: String get() = entryMode

    /**
     * 切换展开的录入方式（photo / voice / manual）。
     *
     * 调用过即视为初始化完成，避免设置异步加载回来时覆盖用户刚做出的选择。
     */
    fun setMode(mode: String) {
        entryMode = when (mode) {
            PHOTO, VOICE, MANUAL -> mode
            else -> MANUAL
        }
        modeInitialized = true
    }

    /**
     * 首次进入时初始化表单。
     *
     * **存放位置不设默认值**（留空，强制用户自己选或自己填）；
     * 单位沿用设置里上次用过的值。
     */
    fun initDefaults(unit: String) {
        if (initialized) return
        initialized = true
        manualDraft = blankItem(unit)
    }

    // ---------------- 待入库池 ----------------

    fun addDrafts(newDrafts: List<FoodDraft>) {
        if (newDrafts.isEmpty()) return
        drafts.addAll(newDrafts)
    }

    fun updateDraft(index: Int, draft: FoodDraft) {
        if (index in drafts.indices) drafts[index] = draft
    }

    fun removeDraft(index: Int) {
        if (index in drafts.indices) drafts.removeAt(index)
    }

    fun clearDrafts() = drafts.clear()

    /** 取走并剔除某几条（用于"单条入库"） */
    fun takeDrafts(indexes: List<Int>): List<FoodDraft> {
        val taken = indexes.filter { it in drafts.indices }.map { drafts[it] }
        indexes.sortedDescending().forEach { if (it in drafts.indices) drafts.removeAt(it) }
        return taken
    }

    // ---------------- 手工录入 ----------------

    fun updateManual(item: FoodItem) {
        manualDraft = item
    }

    /** 手工录入保存后：所有字段清空（位置也清空，不预填） */
    fun resetManual(unit: String) {
        manualDraft = blankItem(unit)
        manualResetToken++
    }

    /** 把一段文本带进手工表单（解析失败时兜底） */
    fun prefillManualFrom(text: String, unit: String) {
        manualDraft = blankItem(unit).copy(name = text.trim().take(20))
        manualResetToken++
        entryMode = MANUAL
        pendingFallbackText = ""
    }

    fun consumeFallbackText(): String {
        val text = pendingFallbackText
        pendingFallbackText = ""
        return text
    }

    companion object {
        const val PHOTO = "photo"
        const val VOICE = "voice"
        const val MANUAL = "manual"

        /**
         * 空白表单。
         * **存放位置刻意留空**（不设默认值），让用户主动选择或填写。
         */
        fun blankItem(unit: String = Units.DEFAULT) = FoodItem(
            name = "",
            unit = unit.ifBlank { Units.DEFAULT },
            location = "",
            purchaseDate = DateUtils.today(),
            source = "manual",
        )
    }
}
