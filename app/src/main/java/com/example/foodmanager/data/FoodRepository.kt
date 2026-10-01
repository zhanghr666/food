package com.example.foodmanager.data

import android.content.Context
import com.example.foodmanager.core.DateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** 首页统计信息 */
data class FoodStats(
    val total: Int = 0,
    val expired: Int = 0,
    val expiringSoon: Int = 0,
    val fresh: Int = 0,
    val noExpiry: Int = 0,
    val categories: Int = 0,
    val locations: Int = 0,
)

/** 食物数据仓库：App 内唯一的数据入口，全部数据保存在手机本地的 SQLite 中。 */
class FoodRepository(
    private val foodDao: FoodDao,
    private val settingsDao: SettingsDao,
    private val appContext: android.content.Context,
) {

    /** 用于调度后台提醒任务 */
    fun appContext(): android.content.Context = appContext

    val settings: Flow<AppSettings> = settingsDao.observe().map { it ?: AppSettings.DEFAULT }

    private val settingsCache = MutableStateFlow(AppSettings.DEFAULT)
    val currentSettings: StateFlow<AppSettings> = settingsCache.asStateFlow()

    /** 当天的 0 点，用于状态计算（跨天时由 UI 重新采集） */
    private val todayFlow = MutableStateFlow(DateUtils.today())

    fun refreshToday() {
        todayFlow.value = DateUtils.today()
    }

    val allItems: Flow<List<FoodItem>> = combine(foodDao.observeAll(), settings) { list, _ -> list }

    fun search(query: String, location: String = ""): Flow<List<FoodItem>> =
        foodDao.observeFiltered(query.trim(), location)

    val archivedItems: Flow<List<FoodItem>> = foodDao.observeArchived()

    /** 带实时状态的列表：随设置中的预警天数变化而重新计算 */
    val itemsWithStatus: Flow<List<FoodItem>> = combine(allItems, settings) { list, s ->
        list.sortedWith(statusComparator(s.warnDays))
    }

    val stats: Flow<FoodStats> = combine(allItems, settings) { list, s ->
        val today = DateUtils.today()
        var expired = 0
        var soon = 0
        var fresh = 0
        var noExpiry = 0
        for (item in list) {
            when (item.status(today, s.warnDays)) {
                FoodStatus.EXPIRED -> expired++
                FoodStatus.EXPIRING_SOON -> soon++
                FoodStatus.FRESH -> fresh++
                FoodStatus.NO_EXPIRY -> noExpiry++
            }
        }
        FoodStats(
            total = list.size,
            expired = expired,
            expiringSoon = soon,
            fresh = fresh,
            noExpiry = noExpiry,
            categories = list.map { it.category }.filter { it.isNotBlank() }.distinct().size,
            locations = list.map { it.location }.filter { it.isNotBlank() }.distinct().size,
        )
    }

    /** 首页提醒用：已过期 + 即将过期，按紧急程度排序 */
    val alertItems: Flow<List<FoodItem>> = combine(allItems, settings) { list, s ->
        val today = DateUtils.today()
        list.filter {
            val st = it.status(today, s.warnDays)
            st == FoodStatus.EXPIRED || st == FoodStatus.EXPIRING_SOON
        }.sortedWith(statusComparator(s.warnDays))
    }

    val locations: Flow<List<String>> = foodDao.observeLocations()

    suspend fun loadSettings(): AppSettings =
        settingsDao.get() ?: AppSettings.DEFAULT.also { settingsDao.upsert(it) }

    suspend fun saveSettings(settings: AppSettings) {
        settingsDao.upsert(settings)
        settingsCache.value = settings
    }

    suspend fun updateSettings(block: (AppSettings) -> AppSettings): AppSettings {
        val updated = block(settingsDao.get() ?: AppSettings.DEFAULT)
        saveSettings(updated)
        return updated
    }

    suspend fun get(id: Long): FoodItem? = foodDao.findById(id)

    suspend fun add(item: FoodItem): Long = foodDao.insert(item.copy(updatedAt = System.currentTimeMillis()))

    suspend fun addAll(items: List<FoodItem>): List<Long> =
        foodDao.insertAll(items.map { it.copy(updatedAt = System.currentTimeMillis()) })

    suspend fun update(item: FoodItem): FoodItem {
        val updated = item.copy(updatedAt = System.currentTimeMillis())
        foodDao.update(updated)
        return updated
    }

    suspend fun delete(id: Long) = foodDao.deleteById(id)

    suspend fun setArchived(id: Long, archived: Boolean) = foodDao.setArchived(id, archived)

    suspend fun changeQuantity(id: Long, delta: Double) {
        val item = foodDao.findById(id) ?: return
        val next = (item.quantity + delta).coerceAtLeast(0.0)
        foodDao.updateQuantity(id, next)
    }

    suspend fun clearAll() = foodDao.clearAll()

    /** 供后台任务使用：一次性取出需要提醒的条目 */
    suspend fun loadAlertItems(warnDays: Int): List<FoodItem> {
        val today = DateUtils.today()
        return foodDao.expiringBy(DateUtils.plusDays(today, warnDays))
            .sortedWith(statusComparator(warnDays))
    }

    private fun statusComparator(warnDays: Int): Comparator<FoodItem> =
        compareBy<FoodItem> { item ->
            // 已过期排最前，其次即将过期，再是无保质期
            when (item.status(DateUtils.today(), warnDays)) {
                FoodStatus.EXPIRED -> 0
                FoodStatus.EXPIRING_SOON -> 1
                FoodStatus.FRESH -> 2
                FoodStatus.NO_EXPIRY -> 3
            }
        }.thenBy { it.expiryDate ?: Long.MAX_VALUE }
            .thenBy { it.name }
}
