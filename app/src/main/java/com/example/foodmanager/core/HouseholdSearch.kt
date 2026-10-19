package com.example.foodmanager.core

import com.example.foodmanager.data.ManagedItem
import com.example.foodmanager.data.StorageLocation
import java.text.Normalizer
import java.util.Locale

/** 仅使用显式别名；冰箱里/外面、柜子上/下等方位不被自动删改。 */
object HouseholdSearch {
    fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(Regex("\\s+"), "")

    fun resolvePlace(value: String, locations: List<StorageLocation>): String {
        val key = normalize(value)
        val exact = locations.filter { normalize(it.name) == key }
        if (exact.size == 1) return exact.single().name
        val matches = locations.filter { it.aliasList.any { alias -> normalize(alias) == key } }
        return if (matches.size == 1) matches.single().name else value.trim()
    }

    fun placeMatches(actual: String, selected: String, locations: List<StorageLocation>) =
        normalize(resolvePlace(actual, locations)) == normalize(resolvePlace(selected, locations))

    fun rank(item: ManagedItem, query: String, locations: List<StorageLocation>): Int? {
        if (query.isBlank()) return 0
        val key = normalize(query)
        val name = normalize(item.name)
        if (name == key) return 0
        if (name.contains(key)) return 1
        val place = resolvePlace(item.location, locations)
        val aliases = locations.firstOrNull { it.name == place }?.aliasList.orEmpty()
        val fields = (listOf(item.name, item.location, place, item.category, item.brand, item.specification, DetailNotes.from(item.note).user) + aliases).map(::normalize)
        if (fields.any { it.contains(key) }) return 2
        val terms = query.trim().split(Regex("\\s+")).map(::normalize).filter(String::isNotBlank)
        return if (terms.isNotEmpty() && terms.all { term -> fields.any { it.contains(term) } }) 3 else null
    }

    fun matches(item: ManagedItem, query: String, locations: List<StorageLocation> = emptyList()) = rank(item, query, locations) != null
    fun find(items: List<ManagedItem>, query: String, locations: List<StorageLocation>): List<ManagedItem> = items
        .mapNotNull { item -> rank(item, query, locations)?.let { item to it } }
        .sortedWith(compareBy<Pair<ManagedItem, Int>> { it.second }.thenByDescending { it.first.updatedAt }.thenByDescending { it.first.id })
        .map { it.first }
}
