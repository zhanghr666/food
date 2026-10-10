package com.example.foodmanager.recognition

import com.example.foodmanager.core.*

/** 比较多个识别假设：不同值变成字段候选，不以“填得多”选择真值。 */
object RecognitionAlternatives {
    fun reconcile(primary: EntryDraft, alternatives: List<EntryDraft>): EntryDraft {
        var result = primary
        val choices = primary.fieldCandidates.toMutableMap()
        for (field in EntryField.entries.filter { it != EntryField.CATEGORY }) {
            val current = value(primary, field)
            val possible = alternatives.flatMap { listOf(value(it, field)) + it.fieldCandidates[field].orEmpty() }
                .filter(String::isNotBlank).distinct()
            val different = possible.filter { it != current }
            if (different.isEmpty()) continue
            if (!primary.has(field)) result = suggest(result, field, different.first())
            choices[field] = (choices[field].orEmpty() + different).distinct().take(4)
        }
        return result.copy(fieldCandidates = choices)
    }

    private fun value(draft: EntryDraft, field: EntryField): String = when (field) {
        EntryField.NAME -> draft.name; EntryField.QUANTITY -> draft.quantity; EntryField.UNIT -> draft.unit
        EntryField.LOCATION -> draft.location; EntryField.DATE -> draft.expiryDate?.let(DateUtils::format).orEmpty()
        EntryField.CATEGORY -> draft.category
    }

    private fun suggest(draft: EntryDraft, field: EntryField, value: String): EntryDraft {
        val updated = when (field) {
            EntryField.NAME -> draft.copy(name = value)
            EntryField.QUANTITY -> draft.copy(quantity = value)
            EntryField.UNIT -> draft.copy(unit = value)
            EntryField.LOCATION -> draft.copy(location = value)
            EntryField.DATE -> draft.copy(expiryDate = DateUtils.parse(value))
            EntryField.CATEGORY -> draft.copy(category = value)
        }
        return updated.copy(origins = updated.origins + (field to FieldOrigin.DERIVED))
    }
}
