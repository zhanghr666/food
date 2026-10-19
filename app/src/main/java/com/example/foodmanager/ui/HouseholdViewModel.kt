package com.example.foodmanager.ui

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.foodmanager.data.*
import com.example.foodmanager.notify.ExpiryReminderWorker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class HouseholdState(val records: List<ManagedItem> = emptyList(), val photos: List<PhotoAttachment> = emptyList(), val loaded: Boolean = false,
    val reminders: List<ItemReminder> = emptyList(), val locations: List<StorageLocation> = emptyList()) {
    fun photoKeys(id: Long) = photos.filter { it.itemId == id }.sortedBy { it.position }.map { it.key }
}
class HouseholdViewModel(private val repository: ItemRepository) : ViewModel() {
    val state = combine(repository.records, repository.photos, repository.reminders, repository.locations) { records, photos, reminders, locations -> HouseholdState(records, photos, true, reminders, locations) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HouseholdState())
    private val _message = MutableStateFlow(""); val message = _message.asStateFlow()
    private val _busy = MutableStateFlow(false); val busy = _busy.asStateFlow()
    fun consumeMessage() { _message.value = "" }
    fun archive(item: ManagedItem) = perform { repository.archive(item.id, !item.archived); _message.value = if (item.archived) "已恢复库存" else "已归档"; reminders(item) }
    fun setArchived(item: ManagedItem, value: Boolean, done: () -> Unit) = perform { repository.archive(item.id, value); reminders(item); done() }
    fun quantity(item: ManagedItem, value: Double, done: (Result<Unit>) -> Unit) = quickChange(item, "数量已更新", done) { repository.updateQuantity(item.id, value) }
    fun location(item: ManagedItem, value: String, done: (Result<Unit>) -> Unit) = quickChange(item, "位置已更新", done) { repository.updateLocation(item.id, value) }
    fun date(item: ManagedItem, value: Long, done: (Result<Unit>) -> Unit) = quickChange(item, "日期已更新", done) { repository.updateFoodDate(item.id, value) }
    private fun quickChange(item: ManagedItem, success: String, done: (Result<Unit>) -> Unit, block: suspend () -> Unit) {
        if (_busy.value) { done(Result.failure(IllegalStateException("正在处理，请稍后重试"))); return }
        _busy.value = true
        viewModelScope.launch {
            try { block(); reminders(item); _message.value = success; done(Result.success(Unit)) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { done(Result.failure(e)) }
            finally { _busy.value = false }
        }
    }
    fun delete(item: ManagedItem, done: () -> Unit) = perform { repository.delete(item.id); reminders(item); _message.value = "已删除${item.name}"; done() }
    fun batch(ids: List<Long>, kind: String, archived: Boolean, action: String, location: String, done: (Result<Unit>) -> Unit) = quickAction(done) {
        repository.batch(ids, kind, archived, action, location)
        _message.value = "已${when (action) { "move" -> "移动"; "archive" -> "归档"; "restore" -> "恢复"; else -> "删除" }} ${ids.size} 条记录"
    }
    fun changePlace(source: String, target: String, merge: Boolean, done: (Result<Unit>) -> Unit) = quickAction(done) { repository.changePlace(source, target, merge) }
    fun aliases(name: String, value: String, done: (Result<Unit>) -> Unit) = quickAction(done) { repository.placeAliases(name, value) }
    fun pin(name: String, pinned: Boolean) = perform { repository.pinPlace(name, pinned); _message.value = if (pinned) "已置顶常用位置" else "已取消置顶" }
    fun reminder(id: Long, title: String, date: Long, done: (Result<Unit>) -> Unit) = quickAction(done) { repository.saveReminder(id, title, date) }
    fun complete(id: Long, value: Boolean) = perform { repository.completeReminder(id, value); ExpiryReminderWorker.runNow(repository.context) }
    fun snooze(id: Long, days: Int) = perform { repository.snoozeReminder(id, days); ExpiryReminderWorker.runNow(repository.context); _message.value = "已延后 $days 天提醒" }
    fun removeReminder(id: Long, done: (Result<Unit>) -> Unit) = quickAction(done) { repository.removeReminder(id) }
    private fun quickAction(done: (Result<Unit>) -> Unit, block: suspend () -> Unit) {
        if (_busy.value) { done(Result.failure(IllegalStateException("正在处理，请稍后重试"))); return }
        _busy.value = true
        viewModelScope.launch {
            try { block(); ExpiryReminderWorker.runNow(repository.context); done(Result.success(Unit)) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { done(Result.failure(e)) }
            finally { _busy.value = false }
        }
    }
    private fun reminders(item: ManagedItem) { runCatching { ExpiryReminderWorker.runNow(repository.context) } }
    private fun perform(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch { try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { _message.value = "操作失败：${e.message.orEmpty()}" } finally { _busy.value = false } }
    }
    companion object { fun factory(repository: ItemRepository): ViewModelProvider.Factory = viewModelFactory { initializer { HouseholdViewModel(repository) } } }
}
