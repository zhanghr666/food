package com.example.foodmanager.core

/** 一条待录入的草稿（来自 OCR 或语音），可在 UI 中编辑后再入库。 */
data class FoodDraft(
    /**
     * 稳定标识：列表用它做 key。
     * 若用列表下标当身份，删除/入库后下标会错位，导致"删错一条"或卡片状态串到下一条上。
     */
    val draftId: Long = nextId(),
    val name: String = "",
    val category: String = "",
    val quantity: Double = 1.0,
    val unit: String = "个",
    val purchaseDate: Long = DateUtils.today(),
    val productionDate: Long? = null,
    val expiryDate: Long? = null,
    val location: String = "",
    val note: String = "",
    val source: String = "manual",
    /** 解析时命中的关键词/字段，用于在 UI 里给用户解释「这些信息是怎么识别出来的」 */
    val highlights: List<String> = emptyList(),
    /** 置信度 0..1，用于排序/提示 */
    val confidence: Float = 0.5f,
) {
    val isUsable: Boolean get() = name.isNotBlank() || expiryDate != null

    companion object {
        private val idGen = java.util.concurrent.atomic.AtomicLong(0L)

        /** 单调递增的草稿 id（进程内唯一即可，草稿不落库） */
        fun nextId(): Long = idGen.incrementAndGet()
    }
}

/** 字段名常量，用于提示与高亮 */
object FoodField {
    const val NAME = "名称"
    const val QUANTITY = "数量"
    const val UNIT = "单位"
    const val PRODUCTION = "生产日期"
    const val EXPIRY = "保质期至"
    const val LOCATION = "存放位置"
    const val CATEGORY = "分类"
}
