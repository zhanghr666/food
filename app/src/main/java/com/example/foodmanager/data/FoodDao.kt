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

    /** 全部记录数（含已归档）：用于判断"用户是否已经用过这个 App" */
    @Query("SELECT COUNT(*) FROM food_items")
    suspend fun countAll(): Int

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

    /**
     * 拍照识别时，**一张照片默认只解析出一条记录**。
     *
     * 默认为 true：单件商品的包装文案（如「脉动 / 减糖 / 蓝莓汁 1L」）按行拆分会被切碎成多条。
     * 关闭后按"一行一件商品"处理，适合拍购物小票批量录入。
     */
    @ColumnInfo(defaultValue = "1") val ocrSingleItem: Boolean = true,

    /** 最近一次使用的存放位置（只作为记忆展示，不会自动套用到新录入上） */
    @ColumnInfo(defaultValue = "") val lastLocation: String = "",

    /** 最近一次使用的单位 */
    @ColumnInfo(defaultValue = "个") val lastUnit: String = "个",

    /** 上次在「录入」页选择的方式：photo / voice / manual */
    @ColumnInfo(defaultValue = "manual") val lastEntryMode: String = "manual",

    /**
     * 常用位置表是否已经初始化过（v7→v8 拆表时置为 1）。
     *
     * 用途：区分"从没初始化过"和"用户主动把位置删光了"。
     * 只有前者才补种默认位置，否则用户删空后重启又被塞回来。
     */
    @ColumnInfo(defaultValue = "0") val locationsInitialized: Boolean = false,
) {
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

/** 常用存放位置（独立表）的读写 */
@Dao
interface StorageLocationDao {

    /** 按排序权重、其次按创建顺序返回；下拉列表与设置页共用 */
    @Query("SELECT * FROM storage_locations ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<StorageLocation>>

    @Query("SELECT * FROM storage_locations ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<StorageLocation>

    @Query("SELECT COUNT(*) FROM storage_locations")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(item: StorageLocation): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(items: List<StorageLocation>): List<Long>

    @Query("UPDATE storage_locations SET alias = :alias WHERE id = :id")
    suspend fun updateAlias(id: Long, alias: String)

    @Query("DELETE FROM storage_locations WHERE name = :name")
    suspend fun deleteByName(name: String)

    @Query("DELETE FROM storage_locations WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 有多少条**在库**记录正在使用这个位置（用于删除前提示） */
    @Query("SELECT COUNT(*) FROM food_items WHERE location = :name")
    suspend fun usageCount(name: String): Int
}

/** 常用食品（独立表）的读写 */
@Dao
interface CommonFoodDao {

    @Query("SELECT * FROM common_foods ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<CommonFood>>

    @Query("SELECT * FROM common_foods ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<CommonFood>

    @Query("SELECT COUNT(*) FROM common_foods")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(item: CommonFood): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(items: List<CommonFood>): List<Long>

    @Query("UPDATE common_foods SET alias = :alias WHERE id = :id")
    suspend fun updateAlias(id: Long, alias: String)

    @Query("DELETE FROM common_foods WHERE id = :id")
    suspend fun deleteById(id: Long)
}
