package com.example.foodmanager.ui.entry

import android.net.Uri
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.foodmanager.FoodApp
import com.example.foodmanager.core.*
import com.example.foodmanager.data.*
import com.example.foodmanager.voice.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ObjectEntryState(val draft: ObjectDraft = ObjectDraft(), val busy: Boolean = false,
    val recording: Boolean = false, val preparing: Boolean = false, val saving: Boolean = false,
    val saved: Boolean = false, val message: String = "", val validationMessage: String = "", val loadFailed: Boolean = false)

class ObjectEntryViewModel(private val items: ItemRepository, food: FoodRepository) : ViewModel() {
    private val _state = MutableStateFlow(ObjectEntryState())
    private var baseline: ObjectDraft? = null
    val state = _state.asStateFlow()
    val locations = food.storageLocations.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val voice = FoodApp.from(items.context).voice
    private var active = false
    private var generation = 0
    init {
        if (!_state.value.draft.photosLoaded) retryPhotos()
        viewModelScope.launch { _state.map { it.draft }.distinctUntilChanged().collect { draft -> items.retainEntryPhotos("object", draft.photos) } }
        viewModelScope.launch {
            voice.state.collect { value ->
                if (!active) return@collect
                when(value) {
                    VoiceState.Preparing -> _state.update { it.copy(preparing = true, message = "正在准备离线语音") }
                    VoiceState.Listening -> _state.update { it.copy(preparing = false, message = "正在聆听，松开结束") }
                    is VoiceState.Partial -> _state.update { it.copy(draft = it.draft.copy(input = value.text)) }
                    VoiceState.Processing -> _state.update { it.copy(recording = false, busy = true) }
                    is VoiceState.Result -> { active = false; _state.update { it.copy(recording = false, preparing = false, busy = false, draft = it.draft.copy(input = value.text)) }; parse("voice") }
                    is VoiceState.Error -> { active = false; _state.update { it.copy(recording = false, preparing = false, busy = false, message = value.message) } }
                    else -> Unit
                }
            }
        }
    }
    private fun change(block: (ObjectDraft) -> ObjectDraft) { if (!_state.value.saving && !_state.value.busy && !_state.value.recording) _state.update { it.copy(draft = block(it.draft), message = "", validationMessage = "", saved = false) } }
    fun edit(field: String, value: String) = change { it.edit(field, value) }
    fun input(value: String) = change { it.copy(input = value) }
    fun mode(value: String) { cancelVoice(); change { it.copy(mode = value) } }
    fun message(value: String) { _state.update { it.copy(message = value) } }
    fun parse(source: String = "text") {
        val state = _state.value
        if (state.busy || state.saving || state.recording || state.draft.input.isBlank()) return
        val raw = state.draft.input; val session = generation
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val aliases = locations.value.flatMap { location -> location.alias.split(',', '，', ';', '；').filter(String::isNotBlank).map { it.trim() to location.name } }.toMap()
                val result = withContext(Dispatchers.Default) { ObjectTextParser.parse(raw, aliases) }
                if (session == generation) _state.update { it.copy(draft = it.draft.parsed(result, raw, source), message = "已整理，请核对名称和位置后保存") }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { message("整理失败，原文已保留") }
            finally { if (session == generation) _state.update { it.copy(busy = false) } }
        }
    }
    fun prepareVoice() = voice.warmUp()
    fun startVoice() {
        if (_state.value.busy || _state.value.saving || _state.value.recording) return
        active = true; _state.update { it.copy(recording = true, draft = it.draft.copy(input = ""), message = "正在启动麦克风") }; voice.start()
    }
    fun finishVoice() { if (_state.value.preparing) cancelVoice() else voice.finish() }
    fun cancelVoice() {
        if (!active) return
        active = false; voice.stop(); _state.update { it.copy(recording = false, preparing = false, busy = false) }
    }
    fun hasUnsavedChanges(): Boolean = baseline?.let { !_state.value.draft.sameContent(it) } ?: _state.value.draft.hasNewContent()
    fun beginNew(): Boolean {
        if (_state.value.saving || _state.value.busy) return false
        clear(); _state.value = ObjectEntryState(); return true
    }
    fun syncRecord(id: Long, field: String, value: String) {
        if (_state.value.draft.id == id) edit(field, value)
    }
    fun clear() {
        if (_state.value.saving || _state.value.busy) return
        generation++; cancelVoice(); baseline = null
        _state.value = ObjectEntryState(draft = ObjectDraft(mode = _state.value.draft.mode))
        items.retainEntryPhotos("object", emptyList())
    }
    fun beginEdit(item: ManagedItem) {
        if (_state.value.saving || _state.value.busy) return
        clear()
        val original = ObjectDraft.from(item, emptyList()).copy(photosLoaded = false)
        baseline = original; _state.value = ObjectEntryState(draft = original)
        retryPhotos()
    }
    fun retryPhotos() {
        val state = _state.value
        if (state.saving || state.busy || state.recording || state.draft.photosLoaded) return
        val session = generation; val id = state.draft.id
        _state.update { it.copy(busy = true, loadFailed = false) }
        viewModelScope.launch {
            try { val photos = items.photos(id); if (session == generation && baseline?.id == id) baseline = baseline?.copy(photos = photos, photosLoaded = true); if (session == generation) _state.update { it.copy(draft = it.draft.copy(photos = photos, photosLoaded = true), message = "") } }
            catch (e: CancellationException) { throw e } catch (e: Exception) { if (session == generation) _state.update { it.copy(loadFailed = true, message = "照片加载失败，请点重试；暂不能保存") } }
            finally { if (session == generation) _state.update { it.copy(busy = false) } }
        }
    }
    fun addPhotos(uris: List<Uri>) {
        if (_state.value.saving || _state.value.busy || _state.value.recording) return
        val session = generation; _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            var failures = 0
            for (uri in uris) try {
                val key = items.photoStore.import(uri)
                if (session == generation) _state.update { it.copy(draft = it.draft.copy(photos = it.draft.photos + key)) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { failures++ }
            if (session == generation) _state.update { it.copy(busy = false, message = if (failures == 0) "现场照片已添加" else "$failures 张照片未能导入，其他照片已保留") }
        }
    }
    fun removePhoto(key: String) = change { it.copy(photos = it.photos - key) }
    fun coverPhoto(key: String) = change { it.copy(photos = listOf(key) + (it.photos - key)) }
    fun movePhoto(key: String, offset: Int) = change {
        val index = it.photos.indexOf(key); val target = index + offset
        if (index < 0 || target !in it.photos.indices) it else it.copy(photos = it.photos.toMutableList().apply { java.util.Collections.swap(this, index, target) })
    }
    fun save() {
        val state = _state.value
        if (state.busy || state.saving || state.recording || state.loadFailed || !state.draft.photosLoaded) return
        state.draft.error()?.let { error -> _state.update { it.copy(validationMessage = error, message = "") }; return }
        _state.update { it.copy(saving = true, validationMessage = "") }
        viewModelScope.launch {
            try {
                items.saveObject(state.draft.item(), state.draft.photos)
                val empty = ObjectDraft(mode = state.draft.mode)
                baseline = null; items.retainEntryPhotos("object", emptyList())
                _state.value = ObjectEntryState(draft = empty, saved = true, message = "物品已保存入库")
            } catch (e: CancellationException) { throw e } catch (e: Exception) { _state.update { it.copy(saving = false, message = "保存失败，内容已保留：${e.message.orEmpty()}") } }
        }
    }
    fun consumeSaved() { _state.update { it.copy(saved = false, message = "") } }
    override fun onCleared() { items.retainEntryPhotos("object", emptyList()); cancelVoice() }
    companion object { fun factory(items: ItemRepository, food: FoodRepository): ViewModelProvider.Factory = viewModelFactory { initializer { ObjectEntryViewModel(items, food) } } }
}
