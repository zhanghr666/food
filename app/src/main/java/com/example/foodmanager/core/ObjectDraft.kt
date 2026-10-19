package com.example.foodmanager.core

import com.example.foodmanager.data.ManagedItem
import java.util.UUID

data class ObjectDraft(
    val id: Long = 0, val token: String = UUID.randomUUID().toString(),
    val name: String = "", val quantity: String = "1", val unit: String = "件",
    val location: String = "", val category: String = "未分类",
    val note: String = "", val brand: String = "", val specification: String = "", val price: String = "",
    val photos: List<String> = emptyList(), val photosLoaded: Boolean = true, val protected: Set<String> = emptySet(),
    val recognized: Set<String> = emptySet(), val input: String = "", val lastParsed: String? = null,
    val mode: String = "voice", val source: String = "manual",
) : PhotoDraftSnapshot {
    override val retainedPhotoKeys: List<String> get() = photos
    val dirty get() = id != 0L || name.isNotBlank() || location.isNotBlank() || input.isNotBlank() || photos.isNotEmpty() ||
        protected.isNotEmpty() || note.isNotBlank() || brand.isNotBlank() || specification.isNotBlank() || price.isNotBlank()
    fun edit(field: String, value: String) = when(field) {
        "name" -> copy(name = value); "quantity" -> copy(quantity = value); "unit" -> copy(unit = value)
        "location" -> copy(location = value); "category" -> copy(category = value); "brand" -> copy(brand = value)
        "specification" -> copy(specification = value); "price" -> copy(price = value); else -> copy(note = value)
    }.copy(protected = protected + field, recognized = recognized - field)
    fun value(field: String): String = when(field) {
        "name" -> name; "quantity" -> quantity; "unit" -> unit; "location" -> location; "category" -> category
        "brand" -> brand; "specification" -> specification; "price" -> price; else -> note
    }
    fun parsed(result: Map<String, String>, raw: String, source: String): ObjectDraft {
        var next = this
        val fields = setOf("name", "quantity", "unit", "location", "category", "brand", "specification", "price", "note")
        for (field in fields - protected) {
            // 重新解析时移除旧自动值，但保留人工输入和默认值。
            val fallback = when(field) { "quantity" -> "1"; "unit" -> "件"; "category" -> "未分类"; else -> "" }
            next = next.edit(field, result[field] ?: fallback)
        }
        return next.copy(protected = protected, recognized = result.keys - protected, input = raw, lastParsed = raw, source = source)
    }
    fun error(): String? = when {
        name.isBlank() -> "请填写物品名称"; location.isBlank() -> "请填写存放位置"
        quantity.isNotBlank() && quantity.toDoubleOrNull()?.let { it.isFinite() && it >= 0 } != true -> "数量需要为有效的非负数"
        price.isNotBlank() && price.toDoubleOrNull()?.let { it.isFinite() && it >= 0 } != true -> "价格需要为有效的非负数"
        else -> null
    }
    fun item() = ManagedItem(id = id, entryToken = token, name = name.trim(), location = location.trim(),
        quantity = quantity.toDoubleOrNull() ?: 1.0, quantitySpecified = quantity.isNotBlank() && ("quantity" in protected || "quantity" in recognized),
        unit = unit.trim().ifBlank { "件" }, category = category.trim().ifBlank { "未分类" }, note = note.trim(),
        brand = brand.trim(), specification = specification.trim(), price = price.toDoubleOrNull(), source = source)
    companion object {
        fun from(item: ManagedItem, photos: List<String>) = ObjectDraft(id = item.id, token = item.entryToken ?: UUID.randomUUID().toString(), name = item.name,
            quantity = formatAmount(item.quantity), unit = item.unit, location = item.location, category = item.category,
            note = item.note, brand = item.brand, specification = item.specification, price = item.price?.toString().orEmpty(), photos = photos,
            protected = setOf("name", "quantity", "unit", "location", "category", "note", "brand", "specification", "price") - if (!item.quantitySpecified) setOf("quantity") else emptySet(), mode = "text")
    }
}

/** 无名称词表、无食品纠音。量词是结构语法；型号数字保留在名称/规格中。 */
object ObjectTextParser {
    val units = listOf("件", "个", "台", "套", "本", "张", "节", "对", "辆", "支", "根", "把", "条", "块", "盒", "袋", "包", "瓶", "罐", "卷", "箱")
    fun parse(raw: String, aliases: Map<String, String> = emptyMap()): Map<String, String> {
        val text = raw.replace(Regex("(?<=[\\p{IsHan}0-9]) +(?=[\\p{IsHan}0-9])"), "").trim()
        val values = linkedMapOf<String, String>()
        var remaining = text
        SpokenLocation.find(text)?.let { loc ->
            values["location"] = aliases[loc.place] ?: loc.place
            remaining = remaining.replace(loc.expression, " ")
        }
        for ((field, label) in listOf("note" to "备注|说明", "category" to "分类|类别", "brand" to "品牌", "specification" to "型号|规格")) {
            Regex("(?:$label)[:：是为]?\\s*([^，,。；;\\n]+?)(?=(?:备注|说明|分类|类别|品牌|型号|规格|价格)[:：是为]?|[，,。；;\\n]|$)").find(remaining)?.let {
                values[field] = it.groupValues[1].trim(); remaining = remaining.replace(it.value, " ")
            }
        }
        Regex("(?:价格|购入价|买入价|花了)[:：是为]?([0-9]+(?:\\.[0-9]+)?|[零〇一二两三四五六七八九十百千万点]+)(?:元|块)?").find(remaining)?.let {
            NumberParser.parse(it.groupValues[1])?.let { price -> values["price"] = formatAmount(price); remaining = remaining.replace(it.value, " ") }
        }
        Regex("(?:下个?月|下周|明天|后天|[0-9零一二两三四五六七八九十]+(?:天|月|年)(?:后)?)[^，,。；;]*?(?:提醒|检查|更换|保养|到期|过期)[^，,。；;]*").find(remaining)?.let {
            values["note"] = listOfNotNull(values["note"], "待设置提醒：${it.value}").joinToString("；")
            remaining = remaining.replace(it.value, " ")
        }
        remaining = remaining.replace(Regex("^(?:请|帮我)?(?:记录一下|记一下|添加|录入|新增)?(?:我有|有|买了|一共|总共)?"), "").trim(' ', '，', ',', '。')
        val quantity = Regex("([0-9]+(?:\\.[0-9]+)?|[零〇一二两三四五六七八九十百千万半点]+)\\s*(${units.joinToString("|")})(?!号)")
        // 只移除名称前/后的数量槽，避免「五号电池」「三合一工具」受影响。
        quantity.findAll(remaining).firstOrNull { it.range.first == 0 || remaining.substring(it.range.last + 1).trim(' ', '，', ',', '。').isEmpty() }?.let {
            NumberParser.parse(it.groupValues[1])?.let { amount ->
                values["quantity"] = formatAmount(amount); values["unit"] = it.groupValues[2]
                remaining = remaining.removeRange(it.range).replace(Regex("(?:数量[:：是为]?|一共|总共|还有|有|共)\\s*$"), "")
            }
        }
        val name = remaining.trim(' ', '，', ',', '。', '；', ';').replace(Regex("^(?:物品|名称)[:：是]?"), "").trim()
        if (name.isNotBlank()) values["name"] = name
        return values
    }
}
