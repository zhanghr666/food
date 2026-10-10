package com.example.foodmanager.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 常用存放位置（**独立表**，取代原先 `AppSettings.customLocations` 里的竖线分隔字符串）。
 *
 * 为什么要拆表：字符串只能表达"名字的序列"，无法存放**列表项自身的属性**。
 * 拆开后即可支持：
 * - [sortOrder]：下拉与设置页的排序（用户可调）
 * - [alias]：语音同义词，例如「冰箱」配「冷藏｜冷藏室」
 *
 * 注意：[FoodItem.location] 仍然存**字符串**，不与该表建立外键。
 * 原因：用户手填的位置、语音识别出的非标准位置都会落在记录上，
 * 强行改外键需要回填历史数据且匹配不上的情况无法兜底。
 * 因此**删除一个位置条目不会影响已有记录的位置文字**。
 */
@Entity(
    tableName = "storage_locations",
    indices = [Index(value = ["name"], unique = true)],
)
data class StorageLocation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** 位置名，例如「冰箱」 */
    val name: String,

    /**
     * 语音同义词，用 `|` 分隔（例如「冷藏|冷藏室」）。
     *
     * 语音里说到其中任意一个词，都会被归一化成 [name]。
     */
    @ColumnInfo(defaultValue = "") val alias: String = "",

    /** 排序权重，越小越靠前 */
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,

    /** 创建时间，便于以后做"最近添加"之类的能力 */
    @ColumnInfo(defaultValue = "0") val createdAt: Long = System.currentTimeMillis(),
) {
    /** 别名列表（已去空、去重；自动排除与正式名相同的项） */
    val aliasList: List<String>
        get() = alias.split('|', '｜', ',', '，', '、')
            .map { it.trim() }
            .filter { it.isNotBlank() && it != name }
            .distinct()
}

/**
 * 常用食品（**独立表**，取代原先 `AppSettings.customFoods` 里的竖线分隔字符串）。
 *
 * 这些食品会参与：① 离线语音语法词表；② 同音字校正；③ OCR 名称识别优先匹配。
 */
@Entity(
    tableName = "common_foods",
    indices = [Index(value = ["name"], unique = true)],
)
data class CommonFood(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** 食品名，例如「车厘子」 */
    val name: String,

    /** 语音同义词，用 `|` 分隔（例如「樱桃」） */
    @ColumnInfo(defaultValue = "") val alias: String = "",

    /** 排序权重，越小越靠前 */
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,

    @ColumnInfo(defaultValue = "0") val createdAt: Long = System.currentTimeMillis(),
) {
    /** 别名列表（已去空、去重；自动排除与正式名相同的项） */
    val aliasList: List<String>
        get() = alias.split('|', '｜', ',', '，', '、')
            .map { it.trim() }
            .filter { it.isNotBlank() && it != name }
            .distinct()
}

/** 把一串名字转成"排序权重"递增的实体列表（用于首次播种） */
internal fun List<String>.toStorageLocations(): List<StorageLocation> =
    mapIndexed { index, name -> StorageLocation(name = name, sortOrder = index) }

/** 同上的常用食品版本 */
internal fun List<String>.toCommonFoods(): List<CommonFood> =
    mapIndexed { index, name -> CommonFood(name = name, sortOrder = index) }
