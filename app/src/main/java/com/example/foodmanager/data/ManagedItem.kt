package com.example.foodmanager.data

import androidx.room.*
import java.io.Serializable
import kotlinx.coroutines.flow.Flow

const val FOOD_VIEW = "SELECT m.id, m.entryToken, m.name, m.category, m.quantity, m.quantitySpecified, m.unit, m.purchaseDate, f.productionDate, f.expiryDate, f.dateKind, f.shelfLifeAmount, f.shelfLifeUnit, m.location, m.note, m.brand, m.specification, m.price, m.source, m.archived, m.createdAt, m.updatedAt FROM managed_items AS m INNER JOIN food_details AS f ON m.id = f.itemId WHERE m.kind = 'food'"

/** 所有家庭库存的共同记录；日期规则由各业务明细负责。 */
@Entity(tableName = "managed_items", indices = [Index(value = ["entryToken"], unique = true), Index(value = ["kind", "archived"])])
data class ManagedItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryToken: String? = null,
    val kind: String = "object",
    val name: String,
    val category: String = "未分类",
    val quantity: Double = 1.0,
    @ColumnInfo(defaultValue = "0") val quantitySpecified: Boolean = false,
    val unit: String = "件",
    val purchaseDate: Long = com.example.foodmanager.core.DateUtils.today(),
    val location: String = "",
    val note: String = "",
    val brand: String = "",
    val specification: String = "",
    val price: Double? = null,
    val source: String = "manual",
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) : Serializable

@Entity(tableName = "food_details", foreignKeys = [ForeignKey(entity = ManagedItem::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)])
data class FoodDetails(
    @PrimaryKey val itemId: Long,
    val productionDate: Long? = null, val expiryDate: Long? = null,
    val dateKind: String = "expiry", val shelfLifeAmount: Int? = null, val shelfLifeUnit: String = "天",
)

@Entity(tableName = "photo_attachments", foreignKeys = [ForeignKey(entity = ManagedItem::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)], indices = [Index("itemId")])
data class PhotoAttachment(@PrimaryKey val key: String, val itemId: Long, val position: Int)

fun FoodItem.common() = ManagedItem(id, entryToken, "food", name, category, quantity, quantitySpecified, unit, purchaseDate, location, note, brand, specification, price, source, archived, createdAt, updatedAt)
fun FoodItem.details(itemId: Long = id) = FoodDetails(itemId, productionDate, expiryDate, dateKind, shelfLifeAmount, shelfLifeUnit)

@Dao
interface ManagedItemDao {
    @Query("SELECT * FROM managed_items ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<ManagedItem>>
    @Query("SELECT * FROM managed_items WHERE id = :id") suspend fun get(id: Long): ManagedItem?
    @Query("SELECT * FROM managed_items WHERE entryToken = :token") suspend fun byToken(token: String): ManagedItem?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(item: ManagedItem): Long
    @Update suspend fun update(item: ManagedItem): Int
    @Query("DELETE FROM managed_items WHERE id = :id") suspend fun delete(id: Long)
    @Query("UPDATE managed_items SET archived = :archived, updatedAt = :now WHERE id = :id") suspend fun archive(id: Long, archived: Boolean, now: Long): Int
    @Query("UPDATE managed_items SET quantity = :quantity, quantitySpecified = 1, updatedAt = :now WHERE id = :id")
    suspend fun updateQuantity(id: Long, quantity: Double, now: Long): Int
    @Query("UPDATE managed_items SET location = :location, updatedAt = :now WHERE id = :id")
    suspend fun updateLocation(id: Long, location: String, now: Long): Int
    @Query("SELECT * FROM photo_attachments ORDER BY position ASC") fun observePhotos(): Flow<List<PhotoAttachment>>
    @Query("SELECT * FROM photo_attachments WHERE itemId = :id ORDER BY position ASC") suspend fun photos(id: Long): List<PhotoAttachment>
    @Query("SELECT `key` FROM photo_attachments") suspend fun photoKeys(): List<String>
    @Query("DELETE FROM photo_attachments WHERE itemId = :id") suspend fun clearPhotos(id: Long)
    @Insert suspend fun insertPhotos(photos: List<PhotoAttachment>)
}
