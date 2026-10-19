package com.example.foodmanager.data

import android.content.Context
import androidx.room.withTransaction
import com.example.foodmanager.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 食品和物品共享记录与附件提交，库存和附件要么一起成功，要么一起回滚。 */
class ItemRepository(val context: Context, private val db: AppDatabase) {
    private val dao = db.managedItemDao()
    val records = dao.observeAll()
    val photos = dao.observePhotos()
    val reminders = db.itemReminderDao().observeAll()
    val locations = db.storageLocationDao().observeAll()
    val photoStore = PhotoStore(context)
    private val entryPhotos = java.util.concurrent.ConcurrentHashMap<String, Set<String>>()
    fun retainEntryPhotos(owner: String, keys: List<String>) { entryPhotos[owner] = keys.toSet() }
    suspend fun get(id: Long) = dao.get(id)
    suspend fun photos(id: Long) = dao.photos(id).map { it.key }
    private suspend fun putPhotos(id: Long, keys: List<String>) {
        require(keys.distinct().size == keys.size) { "附件重复" }
        keys.forEach { require(photoStore.file(it).isFile) { "照片文件丢失，请移除后重新添加" } }
        dao.clearPhotos(id); dao.insertPhotos(keys.mapIndexed { index, key -> PhotoAttachment(key, id, index) })
    }
    suspend fun saveObject(item: ManagedItem, photos: List<String>): Long = db.withTransaction {
        val canonicalLocation = HouseholdSearch.resolvePlace(item.location, db.storageLocationDao().getAll())
        require(item.kind == "object" && item.name.isNotBlank() && item.location.isNotBlank())
        require(item.quantity.isFinite() && item.quantity >= 0)
        val existing = if (item.id != 0L) dao.get(item.id) ?: error("这件物品已经删除") else item.entryToken?.let { dao.byToken(it) }
        // 新建草稿令牌已提交时，只返回成功；不能覆盖入库后用户另行编辑的记录或照片。
        if (item.id == 0L && existing != null) { require(existing.kind == "object"); return@withTransaction existing.id }
        val id = if (existing != null) {
            require(existing.kind == "object")
            check(dao.update(item.copy(id = existing.id, location = canonicalLocation, createdAt = existing.createdAt, archived = existing.archived, updatedAt = System.currentTimeMillis())) == 1)
            existing.id
        } else dao.insert(item.copy(location = canonicalLocation)).also { check(it > 0) { "保存失败" } }
        putPhotos(id, photos); id
    }
    suspend fun saveFoods(items: List<FoodItem>, photos: List<List<String>>) = db.withTransaction {
        check(items.size == photos.size)
        require(items.all { it.quantity.isFinite() && it.quantity >= 0 && !it.entryToken.isNullOrBlank() })
        require(photos.flatten().distinct().size == photos.sumOf { it.size }) { "同一照片不能属于多条记录" }
        val foodDao = db.foodDao()
        val placeOptions = db.storageLocationDao().getAll()
        items.zip(photos).forEach { (item, keys) ->
            val location = HouseholdSearch.resolvePlace(item.location, placeOptions)
            val existing = if (item.id != 0L) foodDao.findById(item.id) ?: error("这件食物已经删除") else item.entryToken?.let { dao.byToken(it) }?.let { foodDao.findById(it.id) }
            if (item.id == 0L && existing != null) return@forEach
            val id = if (existing != null) {
                check(foodDao.update(item.copy(id = existing.id, location = location, createdAt = existing.createdAt, archived = existing.archived)) == 1); existing.id
            } else foodDao.insert(item.copy(location = location)).also { check(it > 0) { "保存失败" } }
            putPhotos(id, keys)
        }
    }
    suspend fun updateQuantity(id: Long, quantity: Double) = db.withTransaction {
        require(quantity.isFinite() && quantity >= 0) { "数量需要为有效的非负数" }
        check(dao.updateQuantity(id, quantity, System.currentTimeMillis()) == 1) { "记录已删除，无法修改" }
    }
    suspend fun updateLocation(id: Long, location: String) = db.withTransaction {
        require(location.isNotBlank()) { "请填写存放位置" }
        val canonical = HouseholdSearch.resolvePlace(location, db.storageLocationDao().getAll())
        check(dao.updateLocation(id, canonical, System.currentTimeMillis()) == 1) { "记录已删除，无法修改" }
    }
    suspend fun updateFoodDate(id: Long, date: Long) = db.withTransaction {
        val food = db.foodDao().findById(id) ?: error("食品记录已删除，无法修改")
        val day = DateUtils.startOfDay(date)
        require(food.dateKind == "reminder" || food.productionDate == null || day >= food.productionDate) { "到期日期不能早于生产日期" }
        // 读取最新记录后仅替换目标日期；包装保质期、照片、人工备注等继续保留。
        check(db.foodDao().update(food.copy(expiryDate = day, updatedAt = System.currentTimeMillis())) == 1) { "保存失败，请重试" }
    }
    suspend fun saveReminder(id: Long, title: String, date: Long) = db.withTransaction {
        val item = dao.get(id) ?: error("记录已删除")
        require(!item.archived) { "请先恢复库存再设置提醒" }
        require(item.kind == "object") { "食品提醒请修改食品日期" }
        require(title.trim().isNotBlank() && title.length <= 80) { "请填写不超过 80 字的提醒说明" }
        db.itemReminderDao().put(ItemReminder(id, title = title.trim(), targetDate = DateUtils.startOfDay(date)))
    }
    suspend fun completeReminder(id: Long, completed: Boolean) = db.withTransaction {
        val reminder = db.itemReminderDao().get(id) ?: error("提醒已移除")
        db.itemReminderDao().put(reminder.copy(completed = completed, snoozedUntil = null))
    }
    suspend fun snoozeReminder(id: Long, days: Int) = db.withTransaction {
        require(days in 1..365)
        val item = dao.get(id) ?: error("记录已删除")
        require(!item.archived) { "已归档记录不再提醒" }
        val reminder = db.itemReminderDao().get(id) ?: error("提醒已移除")
        db.itemReminderDao().put(reminder.copy(completed = false, snoozedUntil = DateUtils.todayPlus(days)))
    }
    suspend fun removeReminder(id: Long) = db.withTransaction {
        require(dao.get(id)?.kind == "object") { "食品提醒请修改日期或标为已处理" }
        db.itemReminderDao().delete(id)
    }

    /** 重命名/合并覆盖在库、归档、常用模板；别名只在用户明确确认后保留。 */
    suspend fun changePlace(source: String, destination: String, merge: Boolean) = db.withTransaction {
        val target = destination.trim()
        require(source.isNotBlank() && target.isNotBlank() && target.length <= 80 && source != target) { "请填写不同的新位置，不超过 80 字" }
        val places = db.storageLocationDao().getAll()
        require(places.filter { it.name != source && it.name != target }.none { place ->
            HouseholdSearch.normalize(place.name) == HouseholdSearch.normalize(target) || place.aliasList.any { HouseholdSearch.normalize(it) == HouseholdSearch.normalize(target) }
        }) { "目标名称与其他位置或别名冲突" }
        val old = places.firstOrNull { it.name == source }
        val dest = places.firstOrNull { it.name == target }
        val inUse = db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM managed_items WHERE location = ?", arrayOf(target)).use { it.moveToFirst(); it.getInt(0) > 0 }
        require(merge || dest == null && !inUse) { "目标位置已存在，请使用合并" }
        require(!merge || dest != null || inUse) { "合并目标不存在" }
        val aliases = ((old?.aliasList.orEmpty() + dest?.aliasList.orEmpty()) + source).distinct().filter { it != target }
        require(places.filter { it.name != source && it.name != target }.none { place ->
            aliases.any { alias -> HouseholdSearch.normalize(alias) == HouseholdSearch.normalize(place.name) || place.aliasList.any { HouseholdSearch.normalize(it) == HouseholdSearch.normalize(alias) } }
        }) { "旧名称或别名与其他位置冲突，请先整理别名" }
        val sql = db.openHelper.writableDatabase
        sql.execSQL("UPDATE managed_items SET location = ?, updatedAt = ? WHERE location = ?", arrayOf<Any?>(target, System.currentTimeMillis(), source))
        sql.execSQL("UPDATE common_foods SET location = ? WHERE location = ?", arrayOf(target, source))
        sql.execSQL("UPDATE app_settings SET lastLocation = ? WHERE lastLocation = ?", arrayOf(target, source))
        if (dest != null) {
            db.storageLocationDao().updateAlias(dest.id, aliases.joinToString("|"))
            if (old != null) db.storageLocationDao().deleteById(old.id)
        } else if (old != null) sql.execSQL("UPDATE storage_locations SET name = ?, alias = ? WHERE id = ?", arrayOf<Any?>(target, aliases.joinToString("|"), old.id))
        else db.storageLocationDao().insertIgnore(StorageLocation(name = target, alias = aliases.joinToString("|"), sortOrder = places.size))
    }

    suspend fun pinPlace(name: String, pinned: Boolean) = db.withTransaction {
        require(name.isNotBlank())
        val places = db.storageLocationDao().getAll()
        var id = places.firstOrNull { it.name == name }?.id
        if (id == null) id = db.storageLocationDao().insertIgnore(StorageLocation(name = name.trim(), sortOrder = places.size))
        val order = if (pinned) minOf(0, places.minOfOrNull { it.sortOrder } ?: 0) - 1 else places.size
        db.openHelper.writableDatabase.execSQL("UPDATE storage_locations SET sortOrder = ? WHERE id = ?", arrayOf<Any?>(order, id))
    }

    suspend fun placeAliases(name: String, value: String) = db.withTransaction {
        val places = db.storageLocationDao().getAll()
        val aliases = value.split('|', '｜', ',', '，', '、').map(String::trim).filter { it.isNotBlank() && it != name }.distinct()
        require(aliases.size <= 20 && aliases.all { it.length <= 80 }) { "别名过多或过长" }
        require(places.filter { it.name != name }.none { place -> aliases.any { alias ->
            HouseholdSearch.normalize(alias) == HouseholdSearch.normalize(place.name) || place.aliasList.any { HouseholdSearch.normalize(it) == HouseholdSearch.normalize(alias) }
        } }) { "别名与其他位置冲突，请使用不同名称" }
        val existing = places.firstOrNull { it.name == name }
        val id = existing?.id ?: db.storageLocationDao().insertIgnore(StorageLocation(name = name, sortOrder = places.size))
        db.storageLocationDao().updateAlias(id, aliases.joinToString("|"))
    }
    /** 先验证全部目标再整体提交，防止并发删除/归档造成部分成功。 */
    suspend fun batch(ids: List<Long>, kind: String, archived: Boolean, action: String, location: String) {
        db.withTransaction {
            val items = ids.mapNotNull { dao.get(it) }
            val plan = InventoryBatch.prepare(ids, items, kind, archived, action, location, db.storageLocationDao().getAll())
            val now = System.currentTimeMillis()
            plan.ids.forEach { id -> when (plan.action) {
                "move" -> check(dao.updateLocation(id, plan.target, now) == 1)
                "archive" -> check(dao.archive(id, true, now) == 1)
                "restore" -> check(dao.archive(id, false, now) == 1)
                "delete" -> dao.delete(id)
            } }
        }
        if (action == "delete") runCatching { cleanup() }
    }
    suspend fun archive(id: Long, value: Boolean) { check(dao.archive(id, value, System.currentTimeMillis()) == 1) { "记录已删除，无法归档" } }
    suspend fun delete(id: Long) { dao.delete(id); runCatching { cleanup() } }
    suspend fun cleanup() = withContext(Dispatchers.IO) {
        photoStore.cleanup((dao.photoKeys() + entryPhotos.values.flatten()).toSet())
    }
}
