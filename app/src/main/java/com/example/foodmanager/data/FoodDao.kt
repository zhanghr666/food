package com.example.foodmanager.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {

    @Query("SELECT * FROM food_items WHERE archived = 0 ORDER BY (expiryDate IS NULL), expiryDate ASC, name ASC")
    fun observeAll(): Flow<List<FoodItem>>

    @Query("SELECT * FROM food_items WHERE archived = 1 ORDER BY updatedAt DESC")
    fun observeArchived(): Flow<List<FoodItem>>

    @Query("SELECT * FROM food_items WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): FoodItem?

    @Query("SELECT * FROM food_items WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<FoodItem?>

    @Query(
        """
        SELECT * FROM food_items
        WHERE archived = 0
          AND (:query = '' OR name LIKE '%' || :query || '%'
               OR category LIKE '%' || :query || '%'
               OR location LIKE '%' || :query || '%'
               OR note LIKE '%' || :query || '%')
          AND (:location = '' OR location = :location)
        ORDER BY (expiryDate IS NULL), expiryDate ASC, name ASC
        """
    )
    fun observeFiltered(query: String, location: String): Flow<List<FoodItem>>

    @Query("SELECT * FROM food_items WHERE archived = 0")
    suspend fun allActive(): List<FoodItem>

    @Query("SELECT * FROM food_items WHERE archived = 0 AND expiryDate IS NOT NULL AND expiryDate <= :until ORDER BY expiryDate ASC")
    suspend fun expiringBy(until: Long): List<FoodItem>

    @Query("SELECT * FROM food_items WHERE archived = 0 AND expiryDate IS NOT NULL AND expiryDate < :today ORDER BY expiryDate ASC")
    suspend fun expiredBefore(today: Long): List<FoodItem>

    @Query("SELECT COUNT(*) FROM food_items WHERE archived = 0")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT DISTINCT location FROM food_items WHERE archived = 0 AND location != '' ORDER BY location")
    fun observeLocations(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: FoodItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FoodItem>): List<Long>

    @Update
    suspend fun update(item: FoodItem)

    @Upsert
    suspend fun upsert(item: FoodItem): Long

    @Delete
    suspend fun delete(item: FoodItem)

    @Query("DELETE FROM food_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE food_items SET archived = :archived, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE food_items SET quantity = :quantity, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateQuantity(id: Long, quantity: Double, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM food_items")
    suspend fun clearAll()
}

/** 应用设置，单行表（id 恒为 1） */
@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,

    /** 提前多少天提醒（默认 3 天） */
    @ColumnInfo(defaultValue = "3") val warnDays: Int = 3,

    /** 是否开启系统通知提醒 */
    @ColumnInfo(defaultValue = "1") val notifyEnabled: Boolean = true,

    /** 是否开启首页提醒横幅 */
    @ColumnInfo(defaultValue = "1") val homeBannerEnabled: Boolean = true,

    /** 每天提醒的时间（小时，0-23） */
    @ColumnInfo(defaultValue = "9") val notifyHour: Int = 9,

    /** 语音/OCR 录入后自动使用最近一次的存放位置 */
    @ColumnInfo(defaultValue = "1") val reuseLastLocation: Boolean = true,

    /** 最近一次使用的存放位置 */
    @ColumnInfo(defaultValue = "") val lastLocation: String = "",

    /** 最近一次使用的单位 */
    @ColumnInfo(defaultValue = "个") val lastUnit: String = "个",

    /**
     * 常用存放位置，用 `|` 分隔（例如「厨房|冰箱|客厅储藏室|储物柜|茶几」）。
     *
     * **这是位置列表的唯一来源**：录入页/编辑页的下拉、语音识别的位置词表都用它。
     *
     * 注意：列默认值保持空串 —— 改列默认值等于改 schema（会影响 Room 的身份校验），
     * 默认位置由 [AppSettings] 的 Kotlin 默认值与
     * [FoodRepository.loadSettings] 的兜底逻辑提供。
     */
    @ColumnInfo(defaultValue = "") val customLocations: String = "",

    /**
     * 用户自定义的常用食品，用 `|` 分隔（例如「西兰花|车厘子|螺蛳粉」）。
     * 这些食品会：① 出现在语音语法词表与同音字校正词表；② 供 OCR 名称识别优先匹配。
     */
    @ColumnInfo(defaultValue = "") val customFoods: String = "",

    /** 上次在「录入」页选择的方式：photo / voice / manual */
    @ColumnInfo(defaultValue = "manual") val lastEntryMode: String = "manual",
) {
    /** 自定义位置列表 */
    val customLocationList: List<String>
        get() = customLocations.split('|').map { it.trim() }.filter { it.isNotBlank() }

    /** 自定义常用食品列表 */
    val customFoodList: List<String>
        get() = customFoods.split('|').map { it.trim() }.filter { it.isNotBlank() }

    companion object {
        val DEFAULT = AppSettings()
    }
}

@Dao
interface SettingsDao {

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun observe(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun get(): AppSettings?

    @Upsert
    suspend fun upsert(settings: AppSettings)
}
