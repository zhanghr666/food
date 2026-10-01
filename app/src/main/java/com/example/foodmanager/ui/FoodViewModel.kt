package com.example.foodmanager.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.foodmanager.data.AppSettings
import com.example.foodmanager.data.FoodItem
import com.example.foodmanager.data.FoodRepository
import com.example.foodmanager.data.FoodStats
import com.example.foodmanager.data.Locations
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
class FoodViewModel(private val repository: FoodRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _locationFilter = MutableStateFlow("")
    val locationFilter: StateFlow<String> = _locationFilter.asStateFlow()

    val items: StateFlow<List<FoodItem>> =
        combine(repository.allItems, _query, _locationFilter) { list, q, loc ->
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

    /** 可供选择的存放位置 = 内置位置 + 用户自定义位置（去重保序） */
    val allLocations: StateFlow<List<String>> =
        settings.map { s ->
            (Locations.ALL + s.customLocationList).distinct()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Locations.ALL)

    /** 语音识别用的自定义位置 */
    val customLocations: StateFlow<List<String>> =
        settings.map { it.customLocationList }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 用户维护的常用食品（参与语音语法与同音字校正） */
    val customFoods: StateFlow<List<String>> =
        settings.map { it.customFoodList }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val archived: StateFlow<List<FoodItem>> =
        repository.archivedItems.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { repository.saveSettings(repository.loadSettings()) }
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onLocationFilterChange(value: String) {
        _locationFilter.value = value
    }

    fun refreshToday() = repository.refreshToday()

    suspend fun get(id: Long): FoodItem? = repository.get(id)

    fun save(item: FoodItem, onDone: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val settings = repository.loadSettings()
            val prepared = if (settings.reuseLastLocation && item.location.isNotBlank()) {
                repository.saveSettings(settings.copy(lastLocation = item.location, lastUnit = item.unit))
                item
            } else {
                item
            }
            if (prepared.id == 0L) {
                val id = repository.add(prepared)
                onDone(id)
            } else {
                repository.update(prepared)
                onDone(prepared.id)
            }
            ExpiryReminderWorker.runNow(repository.appContext())
        }
    }

    fun addDrafts(items: List<FoodItem>, onDone: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val settings = repository.loadSettings()
            val prepared = if (settings.reuseLastLocation) {
                val lastLoc = items.firstOrNull { it.location.isNotBlank() }?.location ?: settings.lastLocation
                val lastUnit = items.firstOrNull()?.unit ?: settings.lastUnit
                repository.saveSettings(settings.copy(lastLocation = lastLoc, lastUnit = lastUnit))
                items
            } else {
                items
            }
            repository.addAll(prepared)
            onDone(prepared.size)
            ExpiryReminderWorker.runNow(repository.appContext())
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun setArchived(id: Long, archived: Boolean) {
        viewModelScope.launch { repository.setArchived(id, archived) }
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
            }
        }
    }

    /** 新增一个自定义存放位置（自动去重、限长） */
    fun addCustomLocation(name: String) {
        val clean = name.trim().take(12)
        if (clean.isBlank()) return
        updateSettings { s ->
            if (s.customLocationList.contains(clean)) return@updateSettings s
            val next = s.customLocationList + clean
            s.copy(customLocations = next.joinToString("|"))
        }
    }

    /** 删除一个自定义存放位置 */
    fun removeCustomLocation(name: String) {
        updateSettings { s ->
            val next = s.customLocationList.filter { it != name }
            s.copy(customLocations = next.joinToString("|"))
        }
    }

    /** 新增一个常用食品（会参与语音语法词表与同音字校正） */
    fun addCustomFood(name: String) {
        val clean = name.trim().take(12)
        if (clean.isBlank()) return
        updateSettings { s ->
            if (s.customFoodList.contains(clean)) return@updateSettings s
            val next = s.customFoodList + clean
            s.copy(customFoods = next.joinToString("|"))
        }
    }

    /** 删除一个常用食品 */
    fun removeCustomFood(name: String) {
        updateSettings { s ->
            val next = s.customFoodList.filter { it != name }
            s.copy(customFoods = next.joinToString("|"))
        }
    }

    /** 记住「录入」页上次选择的方式：photo / voice / manual */
    fun saveEntryMode(mode: String) {
        updateSettings { s -> if (s.lastEntryMode == mode) s else s.copy(lastEntryMode = mode) }
    }

    fun clearAll(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.clearAll()
            onDone()
        }
    }

    companion object {
        fun factory(repository: FoodRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    FoodViewModel(repository) as T
            }
    }
}

/** 单条记录编辑（新增 / 修改） */
class EditorViewModel(
    private val repository: FoodRepository,
    private val editingId: Long,
) : ViewModel() {

    var item: FoodItem by mutableStateOf(FoodItem(name = ""))
        private set

    var loaded: Boolean by mutableStateOf(editingId == 0L)
        private set

    val isEditing: Boolean get() = editingId != 0L

    init {
        viewModelScope.launch {
            if (editingId != 0L) {
                repository.get(editingId)?.let { item = it }
            } else {
                val settings = repository.loadSettings()
                item = FoodItem(
                    name = "",
                    location = settings.lastLocation.ifBlank { com.example.foodmanager.data.Locations.DEFAULT },
                    unit = settings.lastUnit.ifBlank { com.example.foodmanager.data.Units.DEFAULT },
                )
            }
            loaded = true
        }
    }

    fun update(block: (FoodItem) -> FoodItem) {
        item = block(item)
    }

    fun save(onDone: (Long) -> Unit) {
        viewModelScope.launch {
            val settings = repository.loadSettings()
            repository.saveSettings(
                settings.copy(
                    lastLocation = item.location.ifBlank { settings.lastLocation },
                    lastUnit = item.unit.ifBlank { settings.lastUnit },
                )
            )
            val id = if (item.id == 0L) repository.add(item) else {
                repository.update(item)
                item.id
            }
            onDone(id)
        }
    }

    companion object {
        fun factory(repository: FoodRepository, editingId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    EditorViewModel(repository, editingId) as T
            }
    }
}
