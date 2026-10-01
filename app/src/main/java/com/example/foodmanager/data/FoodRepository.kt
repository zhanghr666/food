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
    private val storageLocationDao: StorageLocationDao,
    private val commonFoodDao: CommonFoodDao,
    private val appContext: android.content.Context,
) {

    /** 用于调度后台提醒任务 */
    fun appContext(): android.content.Context = appContext

    val settings: Flow<AppSettings> = settingsDao.observe().map { it ?: AppSettings.DEFAULT }

    /** 常用存放位置（按用户排序） */
    val storageLocations: Flow<List<StorageLocation>> = storageLocationDao.observeAll()

    /** 常用食品（按用户排序） */
    val commonFoods: Flow<List<CommonFood>> = commonFoodDao.observeAll()

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

    suspend fun loadSettings(): AppSettings {
        val stored = settingsDao.get()
        if (stored == null) {
            // 全新安装：先播种默认位置，再写设置行（并把初始化标记置上）
            seedDefaultsIfNeeded(initialized = false)
            return AppSettings.DEFAULT.copy(locationsInitialized = true)
                .also { settingsDao.upsert(it) }
        }
        if (!stored.locationsInitialized) {
            // 从 v7 及更早升级上来：位置表已由迁移填充，这里只补标记
            seedDefaultsIfNeeded(initialized = false)
            val migrated = stored.copy(locationsInitialized = true)
            settingsDao.upsert(migrated)
            return migrated
        }
        return stored
    }

    /**
     * 只在"从未初始化过"时播种默认常用位置。
     *
     * [initialized] 为 true 表示位置表以前就生效过 —— 那时表空意味着**用户主动删光了**，
     * 不能再把默认值塞回来。
     */
    private suspend fun seedDefaultsIfNeeded(initialized: Boolean) {
        if (initialized) return
        if (storageLocationDao.count() > 0) return
        storageLocationDao.insertAllIgnore(Locations.DEFAULTS.toStorageLocations())
        commonFoodDao.insertAllIgnore(CommonFoods.DEFAULTS.toCommonFoods())
    }

    // ---------------- 常用存放位置 ----------------

    /** 新增位置（去重、限长、限总量），返回是否真的加进去了 */
    suspend fun addStorageLocation(name: String): Boolean {
        val clean = name.sanitizeName() ?: return false
        if (storageLocationDao.count() >= MAX_CUSTOM_ITEMS) return false
        val next = storageLocationDao.getAll().size
        val id = storageLocationDao.insertIgnore(
            StorageLocation(name = clean, sortOrder = next),
        )
        return id != -1L
    }

    suspend fun removeStorageLocation(id: Long) = storageLocationDao.deleteById(id)

    suspend fun updateStorageLocationAlias(id: Long, alias: String) =
        storageLocationDao.updateAlias(id, alias.sanitizeAlias())

    /** 有多少条在库记录正在使用这个位置（删除前提示用） */
    suspend fun storageLocationUsage(name: String): Int = storageLocationDao.usageCount(name)

    // ---------------- 常用食品 ----------------

    /** 新增常用食品（去重、限长、限总量），返回是否真的加进去了 */
    suspend fun addCommonFood(name: String): Boolean {
        val clean = name.sanitizeName() ?: return false
        if (commonFoodDao.count() >= MAX_CUSTOM_ITEMS) return false
        val next = commonFoodDao.getAll().size
        val id = commonFoodDao.insertIgnore(
            CommonFood(name = clean, sortOrder = next),
        )
        return id != -1L
    }

    suspend fun removeCommonFood(id: Long) = commonFoodDao.deleteById(id)

    suspend fun updateCommonFoodAlias(id: Long, alias: String) =
        commonFoodDao.updateAlias(id, alias.sanitizeAlias())

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

    companion object {
        /**
         * 常用位置 / 常用食品的**条数上限**。
         *
         * 这两份列表都会进入离线语音语法词表，条目过多会拖慢识别、设置页也难维护。
         */
        const val MAX_CUSTOM_ITEMS = 20

        private const val MAX_NAME_LENGTH = 12

        /**
         * 清洗用户输入的名字：去空格、限长、**把 `|` 换成全角**。
         *
         * `|` 是别名与历史数据的保留分隔符，出现在名字里会破坏解析。
         */
        internal fun String.sanitizeName(): String? {
            val clean = trim()
                .replace('|', '｜')
                .replace(',', '，')
                .take(MAX_NAME_LENGTH)
            return clean.ifBlank { null }
        }

        /** 清洗别名串：统一分隔符、去重、限长 */
        internal fun String.sanitizeAlias(): String =
            trim()
                .replace(',', '|')
                .replace('，', '|')
                .replace('、', '|')
                .replace('｜', '|')
                .split('|')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString("|")
                .take(60)
    }
}
