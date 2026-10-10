package com.example.foodmanager.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.foodmanager.data.FoodItem
import com.example.foodmanager.data.FoodRepository
import com.example.foodmanager.notify.ExpiryReminderWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 一个编辑会话一个状态源；系统重建恢复内容，显式退出后新建会话。 */
class EditorViewModel(
    private val repository: FoodRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    var sessionId by mutableStateOf(savedState.get<String>("session"))
        private set
    var item by mutableStateOf(savedState.get<FoodItem>("item") ?: FoodItem(name = ""))
        private set
    private var original = savedState.get<FoodItem>("original")
    private var editingId = savedState.get<Long>("editingId") ?: 0L
    private var protectedFields = savedState.get<ArrayList<String>>("protectedFields")?.toSet()
        ?: original?.let { RecognitionMerge.changed(it, item) + if (editingId != 0L) RecognitionMerge.existing(it) else emptySet() }.orEmpty()
    var loaded by mutableStateOf(original != null)
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set
    var savedId by mutableStateOf(savedState.get<Long>("savedId"))
        private set
    private var loadJob: Job? = null
    val isEditing: Boolean get() = editingId != 0L
    val hasChanges: Boolean get() = loaded && item != original

    fun beginSession(id: Long, session: String) {
        if (saving || (sessionId == session && (loaded || loadJob?.isActive == true))) return
        loadJob?.cancel()
        if (sessionId != session) {
            sessionId = session
            editingId = id
            savedState["session"] = session
            savedState["editingId"] = id
            savedState.remove<FoodItem>("item")
            savedState.remove<FoodItem>("original")
            savedState.remove<Long>("savedId")
            savedId = null
            original = null
            protectedFields = emptySet()
            savedState.remove<ArrayList<String>>("protectedFields")
            item = FoodItem(name = "")
        }
        loaded = false
        error = ""
        loadJob = viewModelScope.launch {
            try {
                val initial = if (id == 0L) {
                    ManualEntryState.blankItem(repository.loadSettings().lastUnit)
                } else {
                    repository.get(id) ?: error("这条记录已不存在，请返回库存")
                }
                item = initial
                original = initial
                protectedFields = if (id == 0L) emptySet() else RecognitionMerge.existing(initial)
                savedState["protectedFields"] = ArrayList(protectedFields)
                savedState["item"] = initial
                savedState["original"] = initial
                loaded = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "无法加载记录，请重试或返回库存"
            }
        }
    }

    fun retryLoad() = sessionId?.let { beginSession(editingId, it) }

    fun update(block: (FoodItem) -> FoodItem) {
        if (!loaded || saving || savedId != null) return
        val updated = block(item)
        protectedFields = protectedFields + RecognitionMerge.changed(item, updated)
        savedState["protectedFields"] = ArrayList(protectedFields)
        item = updated
        savedState["item"] = item
        error = ""
    }

    fun mergeRecognition(draft: com.example.foodmanager.core.FoodDraft) {
        if (!loaded || saving || savedId != null) return
        item = RecognitionMerge.apply(item, draft, protectedFields)
        savedState["item"] = item
        error = ""
    }

    fun save(archive: Boolean = false) {
        if (!loaded || saving || savedId != null || item.name.isBlank()) return
        val snapshot = item.copy(archived = archive || item.archived)
        saving = true
        error = ""
        viewModelScope.launch {
            try {
                val id = if (snapshot.id == 0L) repository.add(snapshot) else {
                    repository.update(snapshot)
                    snapshot.id
                }
                item = snapshot.copy(id = id)
                original = item
                savedState["item"] = item
                savedState["original"] = item
                savedState["savedId"] = id
                savedId = id
                runCatching { ExpiryReminderWorker.runNow(repository.appContext()) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "保存失败，内容已保留，请检查存储空间后重试"
            } finally {
                saving = false
            }
            if (savedId != null) {
                // 记住单位是附属操作；失败不撤销已经成功的食物写入，也不误报保存失败。
                runCatching {
                    repository.updateSettings {
                        it.copy(lastUnit = snapshot.unit, lastLocation = snapshot.location.ifBlank { it.lastLocation })
                    }
                }
            }
        }
    }

    fun endSession() {
        if (saving) return
        loadJob?.cancel()
        listOf("session", "editingId", "item", "original", "savedId", "protectedFields").forEach { savedState.remove<Any>(it) }
        protectedFields = emptySet()
        sessionId = null
        loaded = false
        savedId = null
        original = null
    }

    companion object {
        fun factory(repository: FoodRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { EditorViewModel(repository, createSavedStateHandle()) }
        }
    }
}
