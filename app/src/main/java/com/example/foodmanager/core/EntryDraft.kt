package com.example.foodmanager.core

import com.example.foodmanager.data.FoodItem
import java.io.Serializable

enum class EntryField(val label: String) {
    NAME("食物名称"), QUANTITY("数量"), UNIT("单位"), LOCATION("存放位置"), DATE("日期 / 保质期"), CATEGORY("食物大类")
}
enum class FieldOrigin(val label: String) {
    VOICE("语音识别"), TEXT("文字解析"), IMAGE("照片识别"), DERIVED("推算 / 建议"), TEMPLATE("常用信息"), MANUAL("已填写")
}

/** 唯一录入模型。来源和人工保护独立于值，缺失项不填猜测的默认值。 */
data class EntryDraft(
    val key: Long = System.nanoTime(),
    val itemId: Long = 0,
    val entryToken: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val quantity: String = "",
    val unit: String = "",
    val location: String = "",
    val category: String = "",
    val expiryDate: Long? = null,
    val dateKind: String = "expiry",
    val productionDate: Long? = null,
    val shelfLifeAmount: Int? = null,
    val shelfLifeUnit: String = "天",
    val note: String = "",
    val purchaseDate: Long = DateUtils.today(),
    val purchaseDateSpecified: Boolean = false,
    val brand: String = "",
    val specification: String = "",
    val price: String = "",
    val rawText: String = "",
    val warnings: List<String> = emptyList(),
    val dateCandidates: List<Long> = emptyList(),
    val origins: Map<EntryField, FieldOrigin> = emptyMap(),
    val protected: Set<EntryField> = emptySet(),
    val fieldCandidates: Map<EntryField, List<String>> = emptyMap(),
    val fieldWarnings: Map<EntryField, List<String>> = emptyMap(),
) : Serializable {
    fun needsConfirmation(field: EntryField): Boolean = fieldCandidates[field].orEmpty().isNotEmpty() && field !in protected
    val pendingCount: Int get() = EntryField.entries.count(::needsConfirmation)
    val visibleWarnings: List<String> get() = (warnings + fieldWarnings.filterKeys { it !in protected }.values.flatten()).distinct()
    fun has(field: EntryField): Boolean = when (field) {
        EntryField.NAME -> name.isNotBlank()
        EntryField.QUANTITY -> quantity.toDoubleOrNull()?.let { it.isFinite() && it >= 0 } == true
        EntryField.UNIT -> unit.isNotBlank()
        EntryField.LOCATION -> location.isNotBlank()
        EntryField.DATE -> expiryDate != null
        EntryField.CATEGORY -> category.isNotBlank()
    }
    val completeCount: Int get() = EntryField.entries.count(::has)
    val recognizedCount: Int get() = origins.count { (field, origin) -> has(field) && !needsConfirmation(field) && origin in setOf(FieldOrigin.VOICE, FieldOrigin.TEXT, FieldOrigin.IMAGE) }
    val hasSuggestedQuantity: Boolean get() = listOf(EntryField.QUANTITY, EntryField.UNIT).all {
        needsConfirmation(it) && origins[it] == FieldOrigin.DERIVED && fieldCandidates[it] == listOf(value(it))
    }
    val isEmpty: Boolean get() = origins.isEmpty() && !purchaseDateSpecified && name.isBlank() && quantity.isBlank() && unit.isBlank() && category.isBlank() && expiryDate == null && productionDate == null && location.isBlank() && note.isBlank() && brand.isBlank() && specification.isBlank() && price.isBlank() && rawText.isBlank()

    fun edit(field: EntryField, value: String): EntryDraft = when (field) {
        EntryField.NAME -> copy(name = value)
        EntryField.QUANTITY -> copy(quantity = value)
        EntryField.UNIT -> copy(unit = value)
        EntryField.LOCATION -> copy(location = value)
        EntryField.CATEGORY -> copy(category = value)
        EntryField.DATE -> this
    }.copy(origins = origins + (field to FieldOrigin.MANUAL), protected = protected + field, fieldCandidates = fieldCandidates - field)

    fun setDate(date: Long?, kind: String = dateKind): EntryDraft = copy(
        expiryDate = date, dateKind = kind, origins = origins + (EntryField.DATE to FieldOrigin.MANUAL), protected = protected + EntryField.DATE, fieldCandidates = fieldCandidates - EntryField.DATE,
    )

    /** 仅主动选择食品候选时一并确认数量建议，直接输入名称不会替用户确认数量。 */
    fun acceptFoodSuggestion(name: String): EntryDraft {
        val named = edit(EntryField.NAME, name)
        return if (hasSuggestedQuantity) named.edit(EntryField.QUANTITY, quantity).edit(EntryField.UNIT, unit) else named
    }

    /** 用户在保存确认中接受当前填写值，不把它们伪装成手工输入或替换成候选值。 */
    fun acceptCurrentValues(): EntryDraft {
        val accepted = EntryField.entries.filter { needsConfirmation(it) && has(it) }.toSet()
        return copy(protected = protected + accepted, fieldCandidates = fieldCandidates - accepted)
    }

    /** 普通识别补空白；主动重解析更新自动字段。两者都保护人工编辑及清空。 */
    fun merge(other: EntryDraft, replaceRecognized: Boolean = false): EntryDraft {
        var result = this
        val conflicts = mutableMapOf<EntryField, List<String>>()
        val updated = mutableSetOf<EntryField>()
        for (field in EntryField.entries) {
            if (!other.has(field) || field in protected) continue
            if (replaceRecognized && field == EntryField.CATEGORY && EntryField.NAME in protected && other.has(EntryField.NAME) && name != other.name && other.origins[field] == FieldOrigin.DERIVED) continue
            if (has(field) && !replaceRecognized) {
                if (value(field) != other.value(field)) conflicts[field] = listOf("${field.label}有不同结果：${other.value(field)}；已保留当前内容")
                else if (other.origins[field] in setOf(FieldOrigin.VOICE, FieldOrigin.TEXT, FieldOrigin.IMAGE)) {
                    result = result.copy(origins = result.origins + (field to other.origins.getValue(field)))
                }
                if (value(field) == other.value(field) && other.needsConfirmation(field)) {
                    result = result.copy(fieldCandidates = result.fieldCandidates + (field to other.fieldCandidates.getValue(field)))
                }
                continue
            }
            updated += field
            if (replaceRecognized) result = result.copy(fieldCandidates = result.fieldCandidates - field)
            result = when (field) {
                EntryField.NAME -> result.copy(name = other.name)
                EntryField.QUANTITY -> result.copy(quantity = other.quantity)
                EntryField.UNIT -> result.copy(unit = other.unit)
                EntryField.LOCATION -> result.copy(location = other.location)
                EntryField.CATEGORY -> result.copy(category = other.category)
                EntryField.DATE -> result.copy(expiryDate = other.expiryDate, dateKind = other.dateKind)
            }
            other.origins[field]?.let { result = result.copy(origins = result.origins + (field to it)) }
            if (other.needsConfirmation(field)) result = result.copy(fieldCandidates = result.fieldCandidates + (field to other.fieldCandidates.getValue(field)))
        }
        if (replaceRecognized && EntryField.NAME in updated && name != other.name && !other.has(EntryField.CATEGORY) && EntryField.CATEGORY !in protected) {
            updated += EntryField.CATEGORY
            result = result.copy(category = "", origins = result.origins - EntryField.CATEGORY, fieldCandidates = result.fieldCandidates - EntryField.CATEGORY)
        }
        val previousWarnings = if (replaceRecognized) fieldWarnings - updated else fieldWarnings
        return result.copy(
            purchaseDate = if (purchaseDateSpecified) purchaseDate else other.purchaseDate,
            purchaseDateSpecified = purchaseDateSpecified || other.purchaseDateSpecified,
            brand = result.brand.ifBlank { other.brand }, specification = result.specification.ifBlank { other.specification },
            price = result.price.ifBlank { other.price },
            productionDate = result.productionDate ?: other.productionDate,
            shelfLifeAmount = result.shelfLifeAmount ?: other.shelfLifeAmount,
            shelfLifeUnit = if (result.shelfLifeAmount == null) other.shelfLifeUnit else result.shelfLifeUnit,
            rawText = if (replaceRecognized) other.rawText else listOf(rawText, other.rawText).filter(String::isNotBlank).distinct().joinToString("\n"),
            warnings = (if (replaceRecognized) other.warnings else warnings + other.warnings).distinct(),
            fieldWarnings = (previousWarnings.keys + other.fieldWarnings.keys + conflicts.keys).associateWith { field ->
                (previousWarnings[field].orEmpty() + other.fieldWarnings[field].orEmpty() + conflicts[field].orEmpty()).distinct()
            },
            dateCandidates = (if (replaceRecognized) other.dateCandidates else dateCandidates + other.dateCandidates).distinct(),
        )
    }
    private fun value(field: EntryField): String = when (field) {
        EntryField.NAME -> name; EntryField.QUANTITY -> quantity; EntryField.UNIT -> unit
        EntryField.LOCATION -> location; EntryField.CATEGORY -> category; EntryField.DATE -> "$dateKind:${DateUtils.format(expiryDate)}"
    }
    fun validationError(requireConfirmation: Boolean = true): String? = when {
        name.isBlank() -> "请填写食物名称"
        requireConfirmation && pendingCount > 0 -> "请确认${EntryField.entries.first(::needsConfirmation).label}，点选候选或保留原文"
        quantity.isBlank() -> "请填写数量"
        !has(EntryField.QUANTITY) -> "数量需要是大于或等于 0 的数字"
        unit.isBlank() -> "请选择单位"
        location.isBlank() -> "请填写存放位置"
        expiryDate == null -> "请设置到期日或计划食用日"
        category.isBlank() -> "请选择食物大类"
        price.isNotBlank() && price.toDoubleOrNull()?.let { it.isFinite() && it >= 0 } != true -> "单价可以留空，填写时需要是有效数字"
        dateKind == "expiry" && productionDate != null && expiryDate < productionDate -> "到期日期不能早于生产日期"
        else -> null
    }
    fun toItem(): FoodItem = FoodItem(
        id = itemId, entryToken = entryToken, name = name.trim(), category = category.trim(), quantity = quantity.toDoubleOrNull() ?: 0.0,
        quantitySpecified = quantity.isNotBlank(), unit = unit.trim(), location = location.trim(), expiryDate = expiryDate,
        dateKind = dateKind, productionDate = productionDate, shelfLifeAmount = shelfLifeAmount, shelfLifeUnit = shelfLifeUnit,
        note = note, purchaseDate = purchaseDate, brand = brand, specification = specification,
        price = price.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }, source = if (recognizedCount > 0) "smart" else "manual",
    )
    companion object {
        fun fromItem(item: FoodItem) = EntryDraft(
            itemId = item.id, entryToken = item.entryToken ?: java.util.UUID.randomUUID().toString(), name = item.name, category = item.category,
            quantity = if (item.quantitySpecified) formatAmount(item.quantity) else "", unit = item.unit, location = item.location,
            expiryDate = item.expiryDate, dateKind = item.dateKind, productionDate = item.productionDate,
            shelfLifeAmount = item.shelfLifeAmount, shelfLifeUnit = item.shelfLifeUnit, note = item.note,
            purchaseDate = item.purchaseDate, purchaseDateSpecified = true, brand = item.brand, specification = item.specification, price = item.price?.let(::formatAmount).orEmpty(),
            origins = EntryField.entries.associateWith { FieldOrigin.MANUAL }, protected = EntryField.entries.toSet(),
        )
    }
}
fun formatAmount(value: Double): String = java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
