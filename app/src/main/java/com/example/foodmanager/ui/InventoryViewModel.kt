package com.example.foodmanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.foodmanager.data.*
import com.example.foodmanager.notify.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class InventoryState(val items: List<FoodItem> = emptyList(), val archived: List<FoodItem> = emptyList(),
    val locations: List<StorageLocation> = emptyList(), val settings: AppSettings = AppSettings.DEFAULT, val loaded: Boolean = false)

class InventoryViewModel(private val repository: FoodRepository) : ViewModel() {
    val state = combine(repository.itemsWithStatus, repository.archivedItems, repository.storageLocations, repository.settings) { items, archived, locations, settings ->
        InventoryState(items, archived, locations, settings, true)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, InventoryState())
    val today = repository.today
    val commonFoods = repository.commonFoods.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _message = MutableStateFlow("")
    val message = _message.asStateFlow()
    init { viewModelScope.launch { repository.loadSettings() } }
    fun refreshToday() = repository.refreshToday()
    fun consumeMessage() { _message.value = "" }
    fun archive(item: FoodItem, value: Boolean, onDone: () -> Unit = {}) = perform {
        repository.setArchived(item.id, value); onDone(); runCatching { ExpiryReminderWorker.runNow(repository.appContext()) }
    }
    fun quantity(item: FoodItem, value: Double) = perform { repository.setQuantity(item.id, value) }
    fun delete(item: FoodItem) = perform { repository.delete(item.id); runCatching { ExpiryReminderWorker.runNow(repository.appContext()) } }
    fun addLocation(name: String) = perform { check(repository.addStorageLocation(name)) { "名称重复、为空或已达到 20 个位置" } }
    fun removeLocation(id: Long) = perform { repository.removeStorageLocation(id) }
    fun alias(id: Long, value: String) = perform { repository.updateStorageLocationAlias(id, value) }
    fun foodAlias(id: Long, value: String) = perform { repository.updateCommonFoodAlias(id, value) }
    fun removeCommonFood(id: Long) = perform { repository.removeCommonFood(id) }
    fun settings(block: (AppSettings) -> AppSettings) = perform {
        val updated = repository.updateSettings(block)
        if (updated.notifyEnabled) ExpiryReminderWorker.schedule(repository.appContext(), updated.notifyHour)
        else { ExpiryReminderWorker.cancel(repository.appContext()); FoodNotifier.cancelAll(repository.appContext()) }
    }
    private fun perform(block: suspend () -> Unit) { viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { _message.value = e.message ?: "操作失败，请重试" }
    } }
    companion object { fun factory(repository: FoodRepository): ViewModelProvider.Factory = viewModelFactory { initializer { InventoryViewModel(repository) } } }
}
