package com.example.foodmanager.recognition

import com.example.foodmanager.core.*
import com.example.foodmanager.data.Categories
import com.example.foodmanager.data.CommonFood
import com.example.foodmanager.data.Locations
import com.example.foodmanager.data.StorageLocation
import com.example.foodmanager.ocr.OcrTextParser
import com.example.foodmanager.voice.VoiceTextParser
import com.example.foodmanager.voice.VoicePhoneticCorrector
import com.example.foodmanager.voice.VoiceUnitSuggestion

object SmartTextParser {
    fun voice(raw: String, alternatives: List<String>, locations: List<StorageLocation> = emptyList(), foods: List<CommonFood> = emptyList()): EntryDraft {
        val primary = parse(raw, FieldOrigin.VOICE, locations, foods).single()
        val hypotheses = alternatives.filter { it.isNotBlank() && it != raw }.distinct().take(4)
            .map { parse(it, FieldOrigin.VOICE, locations, foods).single() }
        return RecognitionAlternatives.reconcile(primary, hypotheses)
    }
    fun parse(raw: String, origin: FieldOrigin, locations: List<StorageLocation> = emptyList(), foods: List<CommonFood> = emptyList()): List<EntryDraft> {
        val locNames = (locations.map { it.name } + Locations.DEFAULTS + listOf("冰箱", "窗台", "餐桌", "桌子", "茶几", "床下", "客厅", "卧室", "阳台", "玄关", "办公室")).distinct()
        val aliases = locations.flatMap { loc -> loc.aliasList.map { it to loc.name } }.toMap()
        val foodAliases = foods.flatMap { food -> food.aliasList.map { it to food.name } }.toMap()
        // 一次只整理一种食品。整句解析，避免“还有两年”被切成另一条食品。
        val drafts = listOfNotNull(VoiceTextParser.parse(raw, customLocations = locNames, customFoods = foods.map { it.name }, locationAliases = aliases, foodAliases = foodAliases,
            repairVoiceUnits = origin == FieldOrigin.VOICE))
        // 日期或地点补充句即便没有名称也能进入当前草稿。
        return (drafts.ifEmpty { listOf(FoodDraft(name = "", quantitySpecified = false, rawText = raw)) }).map { old ->
            val text = old.rawText.ifBlank { raw }
            val nameText = (locNames + aliases.keys + listOf(old.location)).filter(String::isNotBlank)
                .sortedByDescending(String::length).fold(text) { value, place -> value.replace(place, " ") }
            val foodMatches = OcrTextParser.foodKeywordNames(foods.flatMap { listOf(it.name) + it.aliasList })
                .filter { it !in setOf("冷冻", "速冻") && nameText.contains(it) }.distinct()
            val foodOptions = foodMatches.filter { word -> foodMatches.none { it != word && it.contains(word) } }
                .map { foodAliases[it] ?: it }.distinct()
            val multipleFoods = foodOptions.size > 1
            val planText = text.replace(Regex("(明天|后天|今天|[0-9一二两三四五六七八九十]+天)(?:内|后)?(?:吃掉|吃完|食用)"), "$1到期")
                .replace(Regex("计划食用(?:日|日期)?[:：]?"), "到期日期：")
            val personalPlan = planText != text
            val dates = RecognitionDates.parse(planText, voice = true, repairVoiceUnits = origin == FieldOrigin.VOICE)
            val qty = if (multipleFoods) RecognitionQuantity.Result() else RecognitionQuantity.parse(text, voice = true)
            val category = Categories.ALL.firstOrNull { text.contains(Regex("(?:分类|类别|属于)[:：是为]*$it")) } ?: OcrTextParser.categoryOf(old.name)
            val nameSuggestions = VoicePhoneticCorrector.nameSuggestions(text, foods.flatMap { listOf(it.name) + it.aliasList })
                .filter { it.heard == old.name }
            val damagedQuantity = nameSuggestions.singleOrNull()?.takeIf { it.suggestedQuantity != null && qty.amount == null && !multipleFoods }
            val amount = qty.amount ?: damagedQuantity?.suggestedQuantity
            val canonicalName = nameSuggestions.singleOrNull()?.candidate ?: old.name
            val unitSuggestion = if (origin == FieldOrigin.VOICE && !multipleFoods && qty.amount != null)
                VoiceUnitSuggestion.suggest(text, old.name, canonicalName, foods.firstOrNull { it.name == canonicalName }?.unit.orEmpty()) else null
            val unit = unitSuggestion ?: qty.unit ?: damagedQuantity?.suggestedUnit
            val origins = buildMap {
                if (old.name.isNotBlank()) put(EntryField.NAME, origin)
                if (amount != null) put(EntryField.QUANTITY, if (damagedQuantity != null) FieldOrigin.DERIVED else origin)
                if (unit != null) put(EntryField.UNIT, if (damagedQuantity != null || unitSuggestion != null) FieldOrigin.DERIVED else origin)
                if (!multipleFoods && old.location.isNotBlank()) put(EntryField.LOCATION, origin)
                if (!multipleFoods && dates.expiry != null) put(EntryField.DATE, if (dates.expiryDerivedFromProduction || dates.expirySuggested) FieldOrigin.DERIVED else origin)
                if (category.isNotBlank()) put(EntryField.CATEGORY, if (category != "其他" && text.contains(Regex("(?:分类|类别|属于)"))) origin else FieldOrigin.DERIVED)
            }
            val shelf = shelf(text)
            val nameCandidates = nameSuggestions.map { it.candidate }.distinct()
            val choices = (nameCandidates + if (multipleFoods) foodOptions.filter { it != old.name } else emptyList()).distinct()
            EntryDraft(name = old.name, quantity = amount?.let(::formatAmount).orEmpty(), unit = unit.orEmpty(),
                location = old.location.takeUnless { multipleFoods }.orEmpty(), category = category.takeUnless { it == "其他" }.orEmpty(), expiryDate = dates.expiry.takeUnless { multipleFoods },
                dateKind = if (personalPlan) "reminder" else "expiry", productionDate = dates.production.takeUnless { multipleFoods }, shelfLifeAmount = shelf?.first.takeUnless { multipleFoods }, shelfLifeUnit = shelf?.second ?: "天",
                rawText = text, origins = origins,
                fieldWarnings = buildMap {
                    val dateWarnings = dates.warnings + if (damagedQuantity != null) listOf("同句数量和名称存在转写歧义，请核对日期中的天数") else emptyList()
                    if (dateWarnings.isNotEmpty()) put(EntryField.DATE, dateWarnings)
                    if (qty.notes.isNotEmpty()) put(EntryField.QUANTITY, qty.notes)
                    if (unitSuggestion != null) put(EntryField.UNIT, listOf("数量里的“个人”可能是量词转写错误，建议单位“$unitSuggestion”，请核对"))
                    if (multipleFoods) put(EntryField.NAME, listOf("一次请描述一种食物；请确认名称，再填写这件食物的数量、位置和日期"))
                },
                dateCandidates = candidates(text),
                fieldCandidates = buildMap {
                    if (dates.expirySuggested && !multipleFoods) dates.expiry?.let { put(EntryField.DATE, listOf(DateUtils.format(it))) }
                    if (choices.isNotEmpty()) put(EntryField.NAME, choices)
                    if (unitSuggestion != null) put(EntryField.UNIT, listOf(unitSuggestion))
                    if (damagedQuantity != null) {
                        put(EntryField.QUANTITY, listOf(formatAmount(damagedQuantity.suggestedQuantity!!)))
                        put(EntryField.UNIT, listOf(damagedQuantity.suggestedUnit!!))
                        // 同句已有明显转写错误时，日期可见但需单独确认，绝不猜测把十天改成三天。
                        dates.expiry?.let { put(EntryField.DATE, listOf(DateUtils.format(it))) }
                    }
                },
                note = old.note.split("；").filterNot { it.startsWith("名称待确认：原文“") }.joinToString("；"),
                purchaseDate = dates.purchase ?: DateUtils.today(), purchaseDateSpecified = dates.purchase != null,
                brand = extra(text, "品牌"), specification = extra(text, "净含量|规格|净重"), price = price(text),
            )
        }
    }

    fun photoText(text: String, foods: List<CommonFood> = emptyList()): EntryDraft {
        val dates = RecognitionDates.parse(text)
        val quantity = RecognitionQuantity.parse(text)
        // 配料、营养表里的食品词不能直接当成商品名。
        val ingredientLabel = Regex("^\\s*(?:配料(?:表)?|原料|(?:营养)?成分(?:表)?)\\s*[:：]?")
        val nameText = text.lines().filterNot { ingredientLabel.containsMatchIn(it) }.joinToString("\n")
        val matches = OcrTextParser.foodKeywordNames(foods.flatMap { listOf(it.name) + it.aliasList })
            .filter { nameText.contains(it) }.distinct()
        val nameOptions = matches.filter { word -> matches.none { it != word && it.contains(word) } }
            .map { hit -> foods.firstOrNull { hit == it.name || hit in it.aliasList }?.name ?: hit }.distinct()
        val name = (foods.map { it.name } + foods.flatMap { it.aliasList }).sortedByDescending(String::length)
            .firstOrNull { nameText.contains(it) }?.let { hit -> foods.firstOrNull { hit == it.name || hit in it.aliasList }?.name }
            ?: OcrTextParser.longestFoodKeyword(nameText).orEmpty()
        val category = OcrTextParser.categoryOf(name).takeUnless { it == "其他" }.orEmpty()
        val shelf = shelf(text)
        return EntryDraft(name = name, quantity = quantity.amount?.let(::formatAmount).orEmpty(), unit = quantity.unit.orEmpty(),
            category = category, expiryDate = dates.expiry, productionDate = dates.production,
            shelfLifeAmount = shelf?.first, shelfLifeUnit = shelf?.second ?: "天", rawText = text,
            fieldWarnings = buildMap {
                if (dates.warnings.isNotEmpty()) put(EntryField.DATE, dates.warnings)
                if (quantity.notes.isNotEmpty()) put(EntryField.QUANTITY, quantity.notes)
            }, dateCandidates = candidates(text),
            purchaseDate = dates.purchase ?: DateUtils.today(), purchaseDateSpecified = dates.purchase != null,
            brand = extra(text, "品牌"), specification = extra(text, "净含量|规格|净重"), price = price(text),
            fieldCandidates = if (nameOptions.size > 1) mapOf(EntryField.NAME to nameOptions.filter { it != name }.take(4)) else emptyMap(),
            origins = buildMap {
                if (name.isNotBlank()) put(EntryField.NAME, FieldOrigin.IMAGE)
                if (quantity.amount != null) put(EntryField.QUANTITY, FieldOrigin.IMAGE)
                if (quantity.unit != null) put(EntryField.UNIT, FieldOrigin.IMAGE)
                if (category.isNotBlank()) put(EntryField.CATEGORY, FieldOrigin.DERIVED)
                if (dates.expiry != null) put(EntryField.DATE, if (dates.expiryDerivedFromProduction) FieldOrigin.DERIVED else FieldOrigin.IMAGE)
            })
    }
    fun template(food: CommonFood) = EntryDraft(name = food.name, category = food.category, unit = food.unit, location = food.location,
        origins = mapOf(EntryField.NAME to FieldOrigin.TEMPLATE, EntryField.CATEGORY to FieldOrigin.TEMPLATE, EntryField.UNIT to FieldOrigin.TEMPLATE, EntryField.LOCATION to FieldOrigin.TEMPLATE))

    private fun shelf(text: String): Pair<Int, String>? {
        val hit = Regex("保质期[:：是为\\s]*([0-9零〇一二两三四五六七八九十百]+)(天|日|周|个?月|年)").find(text) ?: return null
        val count = NumberParser.parse(hit.groupValues[1])?.toInt()?.takeIf { it in 1..36500 } ?: return null
        return count to hit.groupValues[2].replace("个", "").replace("日", "天")
    }
    private fun extra(text: String, labels: String): String = Regex("(?:$labels)[:：是为\\s]*([^\\n，,；;]{1,30})").find(text)?.groupValues?.get(1)?.trim().orEmpty()
    private fun price(text: String): String = Regex("单价[:：是为\\s]*([0-9]+(?:\\.[0-9]+)?)元?").find(text)?.groupValues?.get(1).orEmpty()
    private fun candidates(text: String): List<Long> = Regex("[12][0-9]{3}[-/.年][0-9]{1,2}[-/.月][0-9]{1,2}日?|(?<![0-9])[12][0-9]{7}(?![0-9])")
        .findAll(text).mapNotNull { match ->
            val normalized = match.value.replace("年", "-").replace("月", "-").replace("日", "")
            DateUtils.parse(normalized)
        }.distinct().toList()
}
