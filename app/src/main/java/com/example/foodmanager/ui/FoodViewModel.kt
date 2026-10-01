package com.example.foodmanager.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import android.os.Bundle
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.foodmanager.data.AppSettings
import com.example.foodmanager.data.CommonFood
import com.example.foodmanager.data.FoodItem
import com.example.foodmanager.data.FoodRepository
import com.example.foodmanager.data.FoodStats
import com.example.foodmanager.data.Locations
import com.example.foodmanager.data.StorageLocation
import com.example.foodmanager.notify.ExpiryReminderWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 全局共享的 ViewModel：库存列表、统计、提醒、设置 */
class FoodViewModel(
    private val repository: FoodRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    val today = repository.today
    val allItems = repository.allItems.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    @Suppress("DEPRECATION")
    val scanState = ScanEntryState.restore(
        savedState.get<Bundle>("scan")?.getSerializable("snapshot") as? ScanEntryState.Snapshot,
    )

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _locationFilter = MutableStateFlow("")
    val locationFilter: StateFlow<String> = _locationFilter.asStateFlow()

    val items: StateFlow<List<FoodItem>> =
        combine(repository.itemsWithStatus, _query, _locationFilter) { list, q, loc ->
            list.filter { item ->
                val matchQuery = q.isBlank() || listOf(item.name, item.category, item.location, item.note)
                    .any { it.contains(q, ignoreCase = true) }
                val matchLocation = loc.isBlank() || item.location == loc
                matchQuery && matchLocation
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stats: StateFlow<FoodStats> =
        repository.stats.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodStats())

    val alerts: StateFlow<List<FoodItem>> =
        repository.alertItems.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings.DEFAULT)

    val locations: StateFlow<List<String>> =
        repository.locations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 常用存放位置实体（设置页需要编辑别名，所以暴露完整对象） */
    val storageLocations: StateFlow<List<StorageLocation>> =
        repository.storageLocations
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 常用食品实体（同上） */
    val commonFoods: StateFlow<List<CommonFood>> =
        repository.commonFoods
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 常用存放位置的名字列表：**唯一来源**是 `storage_locations` 表。
     * 录入页/编辑页的位置选项、语音解析的位置词表都用它，保证各处一致。
     */
    val allLocations: StateFlow<List<String>> =
        repository.storageLocations
            .map { list -> list.map { it.name } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Locations.DEFAULTS)

    /** 语音识别用的常用位置（与 allLocations 同源） */
    val customLocations: StateFlow<List<String>> = allLocations

    /**
     * 语音别名映射：别名 → 正式名（位置与常用食品合并）。
     *
     * 例如把「冰箱」配上别名「冷藏」，用户说"放冷藏"也会归到"冰箱"。
     */
    val locationAliasMap: StateFlow<Map<String, String>> =
        repository.storageLocations
            .map { list ->
                buildMap {
                    list.forEach { loc -> loc.aliasList.forEach { alias -> put(alias, loc.name) } }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 常用食品的别名映射（同上） */
    val foodAliasMap: StateFlow<Map<String, String>> =
        repository.commonFoods
            .map { list ->
                buildMap {
                    list.forEach { food -> food.aliasList.forEach { alias -> put(alias, food.name) } }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 拍照识别是否"一张照片只解析一条"（默认 true，小票批量场景可关闭） */
    val ocrSingleItem: StateFlow<Boolean> =
        settings.map { it.ocrSingleItem }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** 用户维护的常用食品名（参与语音语法与同音字校正） */
    val customFoods: StateFlow<List<String>> =
        repository.commonFoods
            .map { list -> list.map { it.name } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val archived: StateFlow<List<FoodItem>> =
        repository.archivedItems.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        savedState.setSavedStateProvider("scan") {
            Bundle().apply { putSerializable("snapshot", scanState.snapshot()) }
        }
        viewModelScope.launch { repository.loadSettings() }
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onLocationFilterChange(value: String) {
        _locationFilter.value = value
    }

    fun refreshToday() = repository.refreshToday()

    suspend fun get(id: Long): FoodItem? = repository.get(id)

    /** 回调只有在数据库写入完成后触发；失败时保留所有草稿。 */
    fun addDrafts(items: List<FoodItem>, onDone: (Result<Int>) -> Unit) {
        if (scanState.saving || items.isEmpty()) return
        scanState.saving = true
        val snapshot = items.toList()
        viewModelScope.launch {
            val result = try {
                repository.addAll(snapshot)
                Result.success(snapshot.size)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            } finally {
                scanState.saving = false
            }
            onDone(result)
            if (result.isSuccess) {
                // 提醒调度不是写库成功的前置条件，不能导致用户重试重复入库。
                runCatching { ExpiryReminderWorker.runNow(repository.appContext()) }
                runCatching { repository.updateSettings { it.copy(lastUnit = snapshot.last().unit) } }
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id); ExpiryReminderWorker.runNow(repository.appContext()) }
    }

    fun setArchived(id: Long, archived: Boolean) {
        viewModelScope.launch { repository.setArchived(id, archived); ExpiryReminderWorker.runNow(repository.appContext()) }
    }

    fun changeQuantity(id: Long, delta: Double) {
        viewModelScope.launch { repository.changeQuantity(id, delta) }
    }

    fun updateSettings(block: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val updated = repository.updateSettings(block)
            val app = repository.appContext()
            if (updated.notifyEnabled) {
                ExpiryReminderWorker.schedule(app, updated.notifyHour)
            } else {
                ExpiryReminderWorker.cancel(app)
                com.example.foodmanager.notify.FoodNotifier.cancelAll(app)
            }
        }
    }

    // ---------------- 常用存放位置 ----------------

    /**
     * 新增一个常用存放位置。
     *
     * [onResult] 用于把"为什么没加上"告诉界面：
     * `true` = 已添加，`false` = 名称为空 / 已存在 / 已达上限。
     */
    fun addStorageLocation(name: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            onResult(repository.addStorageLocation(name))
        }
    }

    /**
     * 删除一个常用存放位置。
     *
     * **不会改动已有记录里的位置文字** —— 删掉的只是下拉可选项。
     */
    fun removeStorageLocation(id: Long) {
        viewModelScope.launch { repository.removeStorageLocation(id) }
    }

    /** 设置某个位置的语音别名（例如「冰箱」配「冷藏|冷藏室」） */
    fun updateStorageLocationAlias(id: Long, alias: String) {
        viewModelScope.launch { repository.updateStorageLocationAlias(id, alias) }
    }

    /** 有多少条**在库**记录正在使用这个位置（删除前提示用） */
    suspend fun storageLocationUsage(name: String): Int = repository.storageLocationUsage(name)

    // ---------------- 常用食品 ----------------

    fun addCommonFood(name: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            onResult(repository.addCommonFood(name))
        }
    }

    fun removeCommonFood(id: Long) {
        viewModelScope.launch { repository.removeCommonFood(id) }
    }

    /** 设置某个常用食品的语音别名（例如「车厘子」配「樱桃」） */
    fun updateCommonFoodAlias(id: Long, alias: String) {
        viewModelScope.launch { repository.updateCommonFoodAlias(id, alias) }
    }

    /** 记住「录入」页上次选择的方式：photo / voice / manual */
    fun saveEntryMode(mode: String) {
        updateSettings { s -> if (s.lastEntryMode == mode) s else s.copy(lastEntryMode = mode) }
    }

    fun clearAll(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.clearAll()
            com.example.foodmanager.notify.FoodNotifier.cancelAll(repository.appContext())
            onDone()
        }
    }

    companion object {
        /**
         * 自定义位置 / 常用食品的**条数上限**。
         *
         * 这两份列表都会进入离线语音的语法词表，条目过多既拖慢识别、
         * 又让设置页难以维护，所以限制在 20 条。
         */
        const val MAX_CUSTOM_ITEMS = 20

        fun factory(repository: FoodRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { FoodViewModel(repository, createSavedStateHandle()) }
        }
    }
}
