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
    val quantitySpecified: Boolean = quantity > 0,
    val unit: String = "个",
    val purchaseDate: Long = DateUtils.today(),
    val purchaseDateSpecified: Boolean = false,
    val productionDate: Long? = null,
    val expiryDate: Long? = null,
    val expiryDerivedFromProduction: Boolean = false,
    val location: String = "",
    val note: String = "",
    val source: String = "manual",
    /** 只用于识别审核，不写入库存数据库。 */
    val rawText: String = "",
    val dateOnly: Boolean = false,
    val protectedFields: Set<String> = emptySet(),
    /** 解析时命中的关键词/字段，用于在 UI 里给用户解释「这些信息是怎么识别出来的」 */
    val highlights: List<String> = emptyList(),
    /** 规则完整度 0..1，仅供内部排序，不代表经过校准的正确概率。 */
    val confidence: Float = 0.5f,
) : java.io.Serializable {
    val isUsable: Boolean get() = name.isNotBlank() || expiryDate != null || productionDate != null

    companion object {
        private val idGen = java.util.concurrent.atomic.AtomicLong(0L)

        /** 单调递增的草稿 id（进程内唯一即可，草稿不落库） */
        fun nextId(): Long = idGen.incrementAndGet()

        fun reserveIdsThrough(id: Long) {
            idGen.updateAndGet { maxOf(it, id) }
        }
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
    const val PURCHASE = "购买日期"
    const val NOTE = "备注"
}
