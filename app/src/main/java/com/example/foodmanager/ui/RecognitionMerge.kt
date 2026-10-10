package com.example.foodmanager.ui

import com.example.foodmanager.core.FoodDraft
import com.example.foodmanager.core.FoodField
import com.example.foodmanager.data.FoodItem

/** 字段保护独立于 UI；手动清空也属于用户选择。 */
object RecognitionMerge {
    fun changed(before: FoodItem, after: FoodItem): Set<String> = buildSet {
        if (before.name != after.name) { add(FoodField.NAME); add(FoodField.CATEGORY) }
        if (before.category != after.category) add(FoodField.CATEGORY)
        if (before.quantity != after.quantity || before.quantitySpecified != after.quantitySpecified || before.unit != after.unit) {
            add(FoodField.QUANTITY); add(FoodField.UNIT)
        }
        if (before.purchaseDate != after.purchaseDate) add(FoodField.PURCHASE)
        if (before.productionDate != after.productionDate) add(FoodField.PRODUCTION)
        if (before.expiryDate != after.expiryDate) add(FoodField.EXPIRY)
        if (before.location != after.location) add(FoodField.LOCATION)
        if (before.note != after.note) add(FoodField.NOTE)
    }

    fun existing(item: FoodItem): Set<String> = buildSet {
        if (item.name.isNotBlank()) add(FoodField.NAME)
        if (item.category.isNotBlank()) add(FoodField.CATEGORY)
        if (item.quantitySpecified) { add(FoodField.QUANTITY); add(FoodField.UNIT) }
        add(FoodField.PURCHASE)
        if (item.productionDate != null) add(FoodField.PRODUCTION)
        if (item.expiryDate != null) add(FoodField.EXPIRY)
        if (item.location.isNotBlank()) add(FoodField.LOCATION)
        if (item.note.isNotBlank()) add(FoodField.NOTE)
    }

    fun apply(current: FoodItem, draft: FoodDraft, protected: Set<String>): FoodItem {
        fun allowed(field: String) = field !in protected && (!draft.dateOnly || field in setOf(FoodField.PRODUCTION, FoodField.EXPIRY))
        val production = if (allowed(FoodField.PRODUCTION)) draft.productionDate ?: current.productionDate else current.productionDate
        val expiry = draft.expiryDate.takeUnless {
            (draft.expiryDerivedFromProduction && production != draft.productionDate) ||
                (it != null && production != null && it < production)
        }
        return current.copy(
            name = if (allowed(FoodField.NAME) && draft.name.isNotBlank()) draft.name else current.name,
            category = if (allowed(FoodField.CATEGORY) && draft.category.isNotBlank()) draft.category else current.category,
            quantity = if (allowed(FoodField.QUANTITY) && draft.quantitySpecified) draft.quantity else current.quantity,
            quantitySpecified = current.quantitySpecified || (allowed(FoodField.QUANTITY) && draft.quantitySpecified),
            unit = if (allowed(FoodField.UNIT) && draft.quantitySpecified && draft.unit.isNotBlank()) draft.unit else current.unit,
            purchaseDate = if (allowed(FoodField.PURCHASE) && draft.purchaseDateSpecified) draft.purchaseDate else current.purchaseDate,
            productionDate = production,
            expiryDate = if (allowed(FoodField.EXPIRY)) expiry ?: current.expiryDate else current.expiryDate,
            location = if (allowed(FoodField.LOCATION) && draft.location.isNotBlank()) draft.location else current.location,
            note = if (allowed(FoodField.NOTE) && draft.note.isNotBlank()) draft.note else current.note,
        )
    }
}
