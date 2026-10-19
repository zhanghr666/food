package com.example.foodmanager.data

import androidx.room.DatabaseView
import com.example.foodmanager.core.DateUtils
import java.util.concurrent.TimeUnit

/**
 * 食物记录。数量统一用 Double 存储，单位单独保存，便于统计与排序。
 * 所有时间字段均为「当地时区当日 0 点」对应的 epoch 毫秒，方便直接做天数差计算。
 */
@DatabaseView(value = FOOD_VIEW, viewName = "food_items")
data class FoodItem(
    val id: Long = 0L,
    /** 同一草稿恢复后重试保存，不会再次入库。旧辅助调用可以不提供。 */
    val entryToken: String? = null,

    /** 食物名称，例如：牛奶、鸡蛋、全麦面包 */
    val name: String,

    /** 分类，如：乳制品 / 蔬菜 / 肉类，可为空 */
    val category: String = "",

    /** 数量 */
    val quantity: Double = 1.0,

    /** 区分未填写与明确填写的零；历史零数量迁移为未填写。 */
    val quantitySpecified: Boolean = quantity > 0,

    /** 单位，如：瓶、盒、袋、个、克 */
    val unit: String = "个",

    /** 买入 / 入库时间 */
    val purchaseDate: Long = DateUtils.today(),

    /** 生产日期，可为空 */
    val productionDate: Long? = null,

    /** 保质期截止日期，可为空（未设置到期日则永不提醒） */
    val expiryDate: Long? = null,

    /** expiry = 包装到期日；reminder = 用户计划食用日，不代表食品已过期。 */
    val dateKind: String = "expiry",
    val shelfLifeAmount: Int? = null,
    val shelfLifeUnit: String = "天",

    /** 存放位置，如：冰箱冷藏 / 冰箱冷冻 / 常温储物柜 */
    /** 存放位置（不设默认值，留空表示用户尚未指定） */
    val location: String = "",

    /** 备注 */
    val note: String = "",
    val brand: String = "",
    val specification: String = "",
    val price: Double? = null,

    /** 录入方式：manual / ocr / voice */
    val source: String = "manual",

    /** 是否已用完 / 已丢弃（归档后不再提醒） */
    val archived: Boolean = false,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) : java.io.Serializable {
    /** 距离过期还有多少天：正数=还剩，0=今天到期，负数=已过期；未设置到期日返回 null */
    fun daysToExpiry(today: Long = DateUtils.today()): Long? {
        val expiry = expiryDate ?: return null
        return DateUtils.daysBetween(today, expiry)
    }

    /** 保质期总天数（生产日期 -> 过期日期），信息不足返回 null */
    val shelfLifeDays: Long?
        get() {
            val p = productionDate ?: return null
            val e = expiryDate ?: return null
            return DateUtils.daysBetween(p, e)
        }

    fun status(today: Long = DateUtils.today(), warnDays: Int = DEFAULT_WARN_DAYS): FoodStatus {
        val days = daysToExpiry(today) ?: return FoodStatus.NO_EXPIRY
        return when {
            days < 0 -> FoodStatus.EXPIRED
            days <= warnDays -> FoodStatus.EXPIRING_SOON
            else -> FoodStatus.FRESH
        }
    }

    companion object {
        const val DEFAULT_WARN_DAYS = 3
    }
}

/** 食物状态 */
enum class FoodStatus(val label: String) {
    EXPIRED("已过期"),
    EXPIRING_SOON("即将过期"),
    FRESH("未临期"),
    NO_EXPIRY("未设置到期日"),
}

/** 常用单位 */
object Units {
    val ALL = listOf("个", "袋", "盒", "瓶", "罐", "包", "斤", "克", "千克", "毫升", "升", "把", "块", "份")
    const val DEFAULT = "个"
}

/**
 * 存放位置。
 *
 * **单一数据源**：位置的唯一来源是 `storage_locations` 表
 * （用户在「设置 - 常用存放地点」里维护）。
 * 这里只提供新安装时的默认值，不再维护第二份内置列表 ——
 * 否则录入页下拉与设置页会不一致。
 */
object Locations {
    /** 新安装时的默认常用位置（用户可在设置里增删） */
    val DEFAULTS = listOf("冰箱冷藏", "冰箱冷冻", "厨房", "储物柜", "茶几")

    /** 存放位置不设默认值：新建食物时留空，由用户自己选或填 */
    const val DEFAULT = ""
}

/** 常用食品的新装默认值（空 = 由用户自己添加） */
object CommonFoods {
    val DEFAULTS: List<String> = emptyList()
}

/** 常用分类 */
object Categories {
    val ALL = listOf(
        "乳制品",
        "蛋类",
        "肉类",
        "水产",
        "蔬菜",
        "水果",
        "主食",
        "零食",
        "饮料",
        "调味品",
        "冷冻食品",
        "熟食剩菜",
        "干货",
        "其他",
    )
}
