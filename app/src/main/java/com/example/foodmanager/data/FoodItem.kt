package com.example.foodmanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.foodmanager.core.DateUtils
import java.util.concurrent.TimeUnit

/**
 * 食物记录。数量统一用 Double 存储，单位单独保存，便于统计与排序。
 * 所有时间字段均为「当地时区当日 0 点」对应的 epoch 毫秒，方便直接做天数差计算。
 */
@Entity(tableName = "food_items")
data class FoodItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** 食物名称，例如：牛奶、鸡蛋、全麦面包 */
    val name: String,

    /** 分类，如：乳制品 / 蔬菜 / 肉类，可为空 */
    val category: String = "",

    /** 数量 */
    val quantity: Double = 1.0,

    /** 单位，如：瓶、盒、袋、个、克 */
    val unit: String = "个",

    /** 买入 / 入库时间 */
    val purchaseDate: Long = DateUtils.today(),

    /** 生产日期，可为空 */
    val productionDate: Long? = null,

    /** 保质期截止日期，可为空（无保质期则永不提醒） */
    val expiryDate: Long? = null,

    /** 存放位置，如：冰箱冷藏 / 冰箱冷冻 / 常温储物柜 */
    val location: String = "常温储物柜",

    /** 备注 */
    val note: String = "",

    /** 录入方式：manual / ocr / voice */
    val source: String = "manual",

    /** 是否已用完 / 已丢弃（归档后不再提醒） */
    val archived: Boolean = false,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    /** 距离过期还有多少天：正数=还剩，0=今天到期，负数=已过期；无保质期返回 null */
    fun daysToExpiry(today: Long = DateUtils.today()): Long? {
        val expiry = expiryDate ?: return null
        return TimeUnit.MILLISECONDS.toDays(expiry - today)
    }

    /** 保质期总天数（生产日期 -> 过期日期），信息不足返回 null */
    val shelfLifeDays: Long?
        get() {
            val p = productionDate ?: return null
            val e = expiryDate ?: return null
            return TimeUnit.MILLISECONDS.toDays(e - p)
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
    FRESH("新鲜"),
    NO_EXPIRY("无保质期"),
}

/** 常用单位 */
object Units {
    val ALL = listOf("个", "袋", "盒", "瓶", "罐", "包", "斤", "克", "千克", "毫升", "升", "把", "块", "份")
    const val DEFAULT = "个"
}

/** 常用存放位置 */
object Locations {
    val ALL = listOf(
        "冰箱冷藏",
        "冰箱冷冻",
        "冰箱变温室",
        "常温储物柜",
        "厨房台面",
        "阳台",
        "酒柜",
    )
    const val DEFAULT = "常温储物柜"
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
