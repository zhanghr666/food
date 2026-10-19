package com.example.foodmanager.core

import com.example.foodmanager.data.FoodItem
import com.example.foodmanager.data.ManagedItem
import java.text.Collator
import java.util.Locale

/** 日期缺失置后；主排序相同时按编号排序，避免刷新导致条目跳动。 */
object InventoryOrder {
    val foodOptions = listOf("到期从近到远", "最近录入", "最近更新", "名称")
    val objectOptions = listOf("最近录入", "最近更新", "名称")

    fun foods(items: List<FoodItem>, order: String): List<FoodItem> {
        val names = Collator.getInstance(Locale.CHINA)
        val comparator = when (order) {
            "到期从近到远" -> compareBy<FoodItem> { it.expiryDate ?: Long.MAX_VALUE }
            "最近更新" -> compareByDescending { it.updatedAt }
            "名称" -> Comparator<FoodItem> { a, b -> names.compare(a.name, b.name) }
            else -> compareByDescending { it.createdAt }
        }
        return items.sortedWith(comparator.thenByDescending { it.id })
    }

    fun objects(items: List<ManagedItem>, order: String): List<ManagedItem> {
        val names = Collator.getInstance(Locale.CHINA)
        val comparator = when (order) {
            "最近更新" -> compareByDescending<ManagedItem> { it.updatedAt }
            "名称" -> Comparator<ManagedItem> { a, b -> names.compare(a.name, b.name) }
            else -> compareByDescending { it.createdAt }
        }
        return items.sortedWith(comparator.thenByDescending { it.id })
    }
}
