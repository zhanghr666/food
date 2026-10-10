package com.example.foodmanager.ui.entry

import android.net.Uri
import android.os.Bundle
import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.foodmanager.core.*
import com.example.foodmanager.data.*
import com.example.foodmanager.notify.ExpiryReminderWorker
import com.example.foodmanager.recognition.*
import com.example.foodmanager.voice.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.Serializable
import java.util.Calendar

data class EntrySaveReview(val drafts: List<EntryDraft>, val rememberFood: Boolean) : Serializable

data class EntryState(
    val drafts: List<EntryDraft> = listOf(EntryDraft()), val selected: Int = 0,
    val input: String = "", val mode: String = "voice", val message: String = "",
    val busy: Boolean = false, val recording: Boolean = false, val preparing: Boolean = false,
    val saving: Boolean = false, val saved: Boolean = false,
    val validationMessage: String = "",
    val saveReview: EntrySaveReview? = null,
    val lastParsedInput: String? = null, val lastParsedKey: Long? = null,
) : Serializable {
    val current: EntryDraft get() = drafts[selected.coerceIn(0, drafts.lastIndex)]
    val dirty: Boolean get() = drafts.any { !it.isEmpty } || input.isNotBlank()
    val hasParsedInput: Boolean get() = lastParsedInput != null && lastParsedKey == current.key
    val canParseInput: Boolean get() = input.isNotBlank() && (!hasParsedInput || input.trim() != lastParsedInput?.trim())

    fun withoutCurrent(): EntryState {
        val remaining = drafts.filterIndexed { i, _ -> i != selected }.ifEmpty { listOf(EntryDraft()) }
        return copy(drafts = remaining, selected = selected.coerceAtMost(remaining.lastIndex), input = "",
            validationMessage = "", saveReview = null, lastParsedInput = null, lastParsedKey = null, message = "已删除当前项，其他草稿已保留", saved = false)
    }

    /** 智能识别只能更新当前一件，其他草稿只能由用户主动添加。 */
    fun withRecognition(result: EntryDraft, parsedText: String? = null, replaceRecognized: Boolean = false): EntryState {
        val updated = drafts.mapIndexed { i, draft -> if (i == selected) {
            if (draft.isEmpty) result else draft.merge(result, replaceRecognized)
        } else draft }
        return copy(drafts = updated, message = "请核对识别内容；有疑问的项目可直接修改", busy = false, preparing = false,
            validationMessage = "", saveReview = null,
            lastParsedInput = parsedText ?: lastParsedInput, lastParsedKey = if (parsedText != null) updated[selected].key else lastParsedKey)
    }
}

class EntryViewModel(private val repository: FoodRepository, savedState: SavedStateHandle) : ViewModel() {
    private val restored: EntryState? = savedState.get<Bundle>("entry")?.let { bundle ->
        if (Build.VERSION.SDK_INT >= 33) bundle.getSerializable("snapshot", EntryState::class.java)
        else {
            @Suppress("DEPRECATION")
            (bundle.getSerializable("snapshot") as? EntryState)
        }
    }
    private val _state = MutableStateFlow(restored?.copy(busy = false, recording = false, preparing = false, saving = false, saved = false, saveReview = null,
        message = when {
            restored.saving -> "上次保存尚未确认，草稿已恢复；核对库存后可重试"
            restored.busy || restored.recording || restored.preparing -> "上次识别已中断，草稿已恢复，可重新识别或补填"
            else -> restored.message
        }) ?: EntryState())
    val state = _state.asStateFlow()
    val locations = repository.storageLocations.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val foods = repository.commonFoods.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val voice = VoiceRecognizer(repository.appContext())
    private var generation = 0
    private var voiceResultEnabled = false
    private var job: Job? = null

    init {
        savedState.setSavedStateProvider("entry") { Bundle().apply { putSerializable("snapshot", _state.value) } }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            voice.state.collect { result ->
                if (!voiceResultEnabled) return@collect
                if (!_state.value.recording && result !is VoiceState.Result && result !is VoiceState.Error) return@collect
                when (result) {
                    VoiceState.Preparing -> _state.update { it.copy(preparing = true, message = "首次使用正在准备离线语音，请稍候") }
                    VoiceState.Listening -> _state.update { it.copy(preparing = false, message = "正在聆听，松开结束，移出按钮取消") }
                    is VoiceState.Partial -> _state.update { it.copy(input = result.text) }
                    VoiceState.Processing -> _state.update { it.copy(recording = false, busy = true, message = "正在整理这句话") }
                    is VoiceState.Result -> { voiceResultEnabled = false; _state.update { it.copy(recording = false, busy = false, input = result.text) }; parse(result.text, FieldOrigin.VOICE, result.alternatives) }
                    is VoiceState.Error -> _state.update { it.copy(recording = false, preparing = false, busy = false, message = result.message) }
                    else -> Unit
                }
            }
        }
    }
    fun setMode(mode: String) { if (!_state.value.saving) { cancelVoice(); _state.update { it.copy(mode = mode, message = "") } } }
    fun setInput(value: String) { if (!_state.value.saving) _state.update { it.copy(input = value, saveReview = null) } }
    fun select(index: Int) { if (!_state.value.saving && !_state.value.busy) _state.update { it.copy(selected = index.coerceIn(0, it.drafts.lastIndex)) } }
    fun edit(field: EntryField, value: String) = change { current ->
        val edited = current.edit(field, value)
        if (field == EntryField.NAME && EntryField.CATEGORY !in current.protected) {
            val category = com.example.foodmanager.ocr.OcrTextParser.categoryOf(value).takeUnless { it == "其他" }.orEmpty()
            edited.copy(category = category, origins = edited.origins + (EntryField.CATEGORY to FieldOrigin.DERIVED))
        } else edited
    }
    fun acceptFoodSuggestion(value: String) = change { current ->
        val edited = current.acceptFoodSuggestion(value)
        if (EntryField.CATEGORY in current.protected) edited else {
            val category = com.example.foodmanager.ocr.OcrTextParser.categoryOf(value).takeUnless { it == "其他" }.orEmpty()
            edited.copy(category = category, origins = edited.origins + (EntryField.CATEGORY to FieldOrigin.DERIVED))
        }
    }
    fun confirmCandidate(field: EntryField, value: String) {
        if (field == EntryField.DATE) DateUtils.parse(value)?.let { date(it) } else edit(field, value)
    }
    fun date(value: Long?, kind: String? = null) = change { it.setDate(value, kind ?: it.dateKind) }
    fun note(value: String) = change { it.copy(note = value) }
    fun extra(field: String, value: String) = change { when (field) {
        "brand" -> it.copy(brand = value); "specification" -> it.copy(specification = value); "price" -> it.copy(price = value); else -> it.copy(note = value)
    } }
    fun purchaseDate(date: Long) = change { it.copy(purchaseDate = date, purchaseDateSpecified = true) }
    fun productionDate(date: Long) = change { it.copy(productionDate = date) }
    fun message(value: String) { _state.update { it.copy(message = value) } }
    private fun change(block: (EntryDraft) -> EntryDraft) {
        if (_state.value.saving) return
        _state.update { state -> state.copy(drafts = state.drafts.mapIndexed { i, draft -> if (i == state.selected) block(draft) else draft }, saved = false, validationMessage = "", saveReview = null) }
    }
    fun newItem() {
        if (_state.value.saving || _state.value.busy) return
        _state.update { it.copy(drafts = it.drafts + EntryDraft(), selected = it.drafts.size, input = "", message = "", saveReview = null, lastParsedInput = null, lastParsedKey = null) }
    }
    fun removeCurrent() {
        if (_state.value.saving || _state.value.busy || _state.value.recording) return
        _state.update(EntryState::withoutCurrent)
    }
    fun clear() {
        if (_state.value.saving) return
        generation++; job?.cancel(); voiceResultEnabled = false; voice.stop(); _state.value = EntryState(mode = _state.value.mode)
    }
    fun beginEdit(item: FoodItem) { clear(); _state.value = EntryState(drafts = listOf(EntryDraft.fromItem(item)), mode = "manual") }
    fun useTemplate(food: CommonFood) {
        change { current -> if (current.isEmpty) SmartTextParser.template(food) else current.merge(SmartTextParser.template(food)) }
    }
    fun startVoice() {
        if (_state.value.busy || _state.value.saving || _state.value.recording) return
        voiceResultEnabled = true
        _state.update { it.copy(recording = true, input = "", message = "正在启动麦克风", saveReview = null) }; voice.start()
    }
    fun prepareVoice() = voice.warmUp()
    fun finishVoice() { if (_state.value.preparing) cancelVoice() else voice.finish() }
    fun cancelVoice() {
        val wasVoice = voiceResultEnabled
        voiceResultEnabled = false
        voice.stop(); _state.update { it.copy(recording = false, preparing = false, busy = if (it.recording || wasVoice) false else it.busy,
            message = if (wasVoice) { if (it.input.isNotBlank()) "录音已停止，已听到的文字保留，可修改后整理" else "语音已停止，可以重新开始或直接填写" } else it.message) }
    }
    fun parseInput() {
        if (_state.value.canParseInput) parse(_state.value.input, FieldOrigin.TEXT, replaceRecognized = true)
    }
    private fun parse(text: String, origin: FieldOrigin, alternatives: List<String> = emptyList(), replaceRecognized: Boolean = false) {
        if (text.isBlank() || _state.value.saving || _state.value.busy || _state.value.recording) return
        val session = generation
        _state.update { it.copy(busy = true, saveReview = null) }
        job = viewModelScope.launch {
            try {
                val results = withContext(Dispatchers.Default) {
                    if (origin == FieldOrigin.VOICE) listOf(SmartTextParser.voice(text, alternatives, locations.value, foods.value))
                    else SmartTextParser.parse(text, origin, locations.value, foods.value)
                }
                if (session == generation) accept(results, text, replaceRecognized)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message("没有整理成功，请修改原文后重试") }
            finally { if (session == generation) _state.update { it.copy(busy = false) } }
        }
    }
    fun photo(uri: Uri) {
        if (_state.value.busy || _state.value.saving || _state.value.recording) return
        val session = generation
        _state.update { it.copy(busy = true, message = "正在本机识物并读取包装日期", saveReview = null) }
        job = viewModelScope.launch {
            try {
                val results = FoodVisionEngine(repository.appContext()).recognize(uri, foods.value)
                if (session == generation) accept(results)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message("照片未能识别，请重拍或直接填写：${e.message.orEmpty().take(60)}") }
            finally { if (session == generation) _state.update { it.copy(busy = false) } }
        }
    }
    private fun accept(results: List<EntryDraft>, parsedText: String? = null, replaceRecognized: Boolean = false) {
        if (results.isEmpty()) { message("没有识别到信息，请直接补填"); return }
        _state.update { it.withRecognition(results.first(), parsedText, replaceRecognized) }
    }
    fun shelfLife(production: Long, amount: Int, unit: String) {
        if (amount !in 1..36500) { message("保质期需要为 1 至 36500"); return }
        val expiry = Calendar.getInstance().apply {
            timeInMillis = production
            when (unit) { "月" -> add(Calendar.MONTH, amount); "年" -> add(Calendar.YEAR, amount); "周" -> add(Calendar.DAY_OF_MONTH, amount * 7); else -> add(Calendar.DAY_OF_MONTH, amount) }
        }.timeInMillis
        change { it.setDate(expiry, "expiry").copy(productionDate = production, shelfLifeAmount = amount, shelfLifeUnit = unit) }
    }
    fun save(remember: Boolean) {
        val state = _state.value
        if (state.saving || state.busy || state.recording || state.saved) return
        val active = state.drafts.filter { !it.isEmpty }
        val invalidIndex = state.drafts.indexOfFirst { !it.isEmpty && it.validationError(requireConfirmation = false) != null }
        val error = state.drafts.getOrNull(invalidIndex)?.validationError(requireConfirmation = false)
        if (active.isEmpty() || error != null) {
            _state.update { it.copy(selected = if (invalidIndex >= 0) invalidIndex else it.selected, validationMessage = error ?: "请先录入食物", message = "", saveReview = null) }
            return
        }
        if (active.any { it.pendingCount > 0 }) {
            _state.update { it.copy(saveReview = EntrySaveReview(state.drafts, remember), validationMessage = "", message = "") }
            return
        }
        _state.update { it.copy(saving = true, message = "正在保存") }
        viewModelScope.launch {
            try {
                val items = active.map(EntryDraft::toItem)
                if (items.any { it.id != 0L }) {
                    require(items.size == 1) { "编辑时请一次保存一件食物" }
                    val original = repository.get(items.first().id) ?: error("这件食物已经删除")
                    repository.update(items.first().copy(createdAt = original.createdAt, archived = original.archived))
                } else repository.saveDraftBatchOnce(items)
                // 库存已保存后，模板和提醒失败不得让用户重复写库。
                if (remember) items.forEach { runCatching { repository.rememberFood(it) } }
                runCatching { ExpiryReminderWorker.runNow(repository.appContext()) }
                generation++
                _state.value = EntryState(mode = state.mode, message = "已保存 ${items.size} 件食物", saved = true)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(saving = false, message = "保存失败，内容已保留：${e.message.orEmpty()}") } }
        }
    }
    fun cancelSaveReview() { _state.update { it.copy(saveReview = null) } }
    fun confirmSaveReview() {
        val state = _state.value
        val review = state.saveReview ?: return
        if (state.saving || state.busy || state.recording || state.saved) return
        if (state.drafts != review.drafts) {
            _state.update { it.copy(saveReview = null, message = "内容已更新，请重新点保存核对") }
            return
        }
        _state.update { it.copy(drafts = it.drafts.map(EntryDraft::acceptCurrentValues), saveReview = null) }
        save(review.rememberFood)
    }
    fun consumeSaved() { _state.update { it.copy(saved = false, message = "") } }
    override fun onCleared() { voice.releaseModel() }
    companion object {
        fun factory(repository: FoodRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { EntryViewModel(repository, createSavedStateHandle()) }
        }
    }
}
