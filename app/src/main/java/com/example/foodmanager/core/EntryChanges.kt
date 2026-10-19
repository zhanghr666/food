package com.example.foodmanager.core

/** 比较实际填写内容；来源标记、输入模式及照片加载状态不算修改。 */
fun EntryDraft.sameContent(other: EntryDraft): Boolean =
    copy(key = other.key, entryToken = other.entryToken, origins = emptyMap(), protected = emptySet(),
        warnings = emptyList(), dateCandidates = emptyList(), fieldCandidates = emptyMap(), fieldWarnings = emptyMap(), photosLoaded = true,
        quantity = quantity.toDoubleOrNull()?.toString() ?: quantity) ==
    other.copy(origins = emptyMap(), protected = emptySet(), warnings = emptyList(), dateCandidates = emptyList(),
        fieldCandidates = emptyMap(), fieldWarnings = emptyMap(), photosLoaded = true,
        quantity = other.quantity.toDoubleOrNull()?.toString() ?: other.quantity)

fun ObjectDraft.sameContent(other: ObjectDraft): Boolean =
    copy(token = other.token, protected = emptySet(), recognized = emptySet(), lastParsed = null, mode = "", source = "", photosLoaded = true,
        quantity = quantity.toDoubleOrNull()?.toString() ?: quantity) ==
    other.copy(protected = emptySet(), recognized = emptySet(), lastParsed = null, mode = "", source = "", photosLoaded = true,
        quantity = other.quantity.toDoubleOrNull()?.toString() ?: other.quantity)

fun EntryDraft.hasNewContent(): Boolean = !copy(origins = emptyMap()).isEmpty
fun ObjectDraft.hasNewContent(): Boolean = !sameContent(ObjectDraft(token = token))
