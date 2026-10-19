package com.example.foodmanager.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.example.foodmanager.core.DateUtils

/** 每条记录一项提醒：食品随日期同步，物品为单次任务。 */
@Entity(tableName = "item_reminders", foreignKeys = [ForeignKey(entity = ManagedItem::class,
    parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)])
data class ItemReminder(@PrimaryKey val itemId: Long, val kind: String = "task", val title: String = "检查物品",
    val targetDate: Long, val leadDays: Int = 0, val completed: Boolean = false, val snoozedUntil: Long? = null) {
    fun checkDate(warnDays: Int): Long = snoozedUntil ?: DateUtils.plusDays(targetDate, -(if (leadDays < 0) warnDays else leadDays))
    fun due(today: Long, warnDays: Int, archived: Boolean = false) = !completed && !archived && checkDate(warnDays) <= today
}

@Dao
interface ItemReminderDao {
    @Query("SELECT * FROM item_reminders") fun observeAll(): Flow<List<ItemReminder>>
    @Query("SELECT * FROM item_reminders") suspend fun getAll(): List<ItemReminder>
    @Query("SELECT * FROM item_reminders WHERE itemId = :id") suspend fun get(id: Long): ItemReminder?
    @Upsert suspend fun put(value: ItemReminder)
    @Query("DELETE FROM item_reminders WHERE itemId = :id") suspend fun delete(id: Long)
}

val ReminderMigration = object : androidx.room.migration.Migration(2, 3) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS item_reminders (itemId INTEGER NOT NULL, kind TEXT NOT NULL, title TEXT NOT NULL, targetDate INTEGER NOT NULL, leadDays INTEGER NOT NULL, completed INTEGER NOT NULL, snoozedUntil INTEGER, PRIMARY KEY(itemId), FOREIGN KEY(itemId) REFERENCES managed_items(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("INSERT INTO item_reminders (itemId,kind,title,targetDate,leadDays,completed,snoozedUntil) SELECT itemId, CASE WHEN dateKind = 'reminder' THEN 'plan' ELSE 'expiry' END, CASE WHEN dateKind = 'reminder' THEN '计划食用' ELSE '食品到期' END, expiryDate, CASE WHEN dateKind = 'reminder' THEN 0 ELSE -1 END, 0, NULL FROM food_details WHERE expiryDate IS NOT NULL")
    }
}
