package com.example.foodmanager.voice

import com.example.foodmanager.core.DateUtils
import com.example.foodmanager.core.FoodDraft
import com.example.foodmanager.core.FoodField
import com.example.foodmanager.ocr.OcrTextParser

/**
 * 语音文本解析：把「牛奶两瓶放冰箱冷藏，六月一号过期」这类自然语言拆成结构化字段。
 * 支持的表达方式：
 *  - 数量：「两瓶」「3个」「半斤」「一箱」
 *  - 位置：「放冰箱」「冷藏」「冷冻」「常温」
 *  - 时间：「今天买的」「明天到期」「6月1日过期」「保质期一年」
 *  - 分类：「这是乳制品」
 *  - 备注：「备注：给宝宝喝的」
 */
object VoiceTextParser {

    private val FILLER_PATTERNS = listOf(
        Regex("^(帮我|请|麻烦)?(记一下|记录一下|添加|新增|加上|录入|记|加)"),
        Regex("(帮我|请|麻烦)?(记一下|记录一下|录入|添加|新增|加上)一下?"),
        Regex("^我的?|^我家"),
        Regex("[，,。.；;]+$"),
    )

    private val LOCATION_RULES: List<Pair<List<String>, String>> = listOf(
        listOf("冷冻", "速冻", "冻起", "冰柜", "冷冻室") to "冰箱冷冻",
        listOf("变温室", "软冷冻", "微冻") to "冰箱变温室",
        listOf("冷藏", "保鲜", "冰箱", "冷鲜") to "冰箱冷藏",
        listOf("酒柜") to "酒柜",
        listOf("阳台") to "阳台",
        listOf("厨房", "台面", "灶台", "料理台") to "厨房台面",
        // 茶几/桌子/架子等下方位（口语常说"在茶几底下"）
        listOf("茶几", "茶几下", "茶几底下", "茶杯") to "厨房台面",
        listOf("桌子", "桌下", "桌子底下", "餐桌", "书桌") to "厨房台面",
        listOf("架子", "货架", "置物架", "层架") to "常温储物柜",
        listOf("抽屉", "柜子", "橱柜", "储物柜", "碗柜") to "常温储物柜",
        listOf("窗台", "窗下", "窗边", "飘窗") to "常温储物柜",
        listOf("床下", "沙发", "沙发下", "地上", "地面") to "常温储物柜",
        listOf("常温", "室温", "阴凉", "通风", "避光") to "常温储物柜",
    )

    /** 单位归一化：每个量词各自独立，不能把「箱」并到「盒」——那是两种不同的包装 */
    private val UNIT_RULES: List<Pair<List<String>, String>> = listOf(
        listOf("袋", "包子") to "袋",
        listOf("包") to "包",
        listOf("盒") to "盒",
        listOf("箱") to "箱",
        listOf("瓶") to "瓶",
        listOf("支") to "支",
        listOf("罐") to "罐",
        listOf("听") to "听",
        listOf("桶") to "桶",
        listOf("个", "只", "枚", "颗", "粒", "头") to "个",
        listOf("根") to "根",
        listOf("条") to "条",
        listOf("斤") to "斤",
        listOf("千克", "公斤", "kg", "KG") to "千克",
        listOf("克", "g", "G") to "克",
        listOf("毫升", "ml", "ML") to "毫升",
        listOf("升", "L", "l") to "升",
        listOf("把") to "把",
        listOf("捆") to "捆",
        listOf("扎") to "扎",
        listOf("块") to "块",
        listOf("份") to "份",
        listOf("片") to "片",
    )

    fun parse(
        raw: String,
        defaultSource: String = "voice",
        customLocations: List<String> = emptyList(),
        customFoods: List<String> = emptyList(),
        /** 位置别名 → 正式名（例如「冷藏」→「冰箱」） */
        locationAliases: Map<String, String> = emptyMap(),
        /** 食品别名 → 正式名（例如「樱桃」→「车厘子」） */
        foodAliases: Map<String, String> = emptyMap(),
        repairVoiceUnits: Boolean = true,
    ): FoodDraft? {
        if (raw.isBlank()) return null
        // 先做同音字校正（西瓜被听成吸管这类问题在这里兜住），用户自定义食品也参与校正
        var text = VoicePhoneticCorrector.correct(raw, customFoods + foodAliases.keys).replace(Regex("(?<=[\\p{IsHan}0-9]) +(?=[\\p{IsHan}0-9])"), "").trim()
        if (repairVoiceUnits) text = com.example.foodmanager.core.VoiceTimeRepair.correct(text).text
        for (f in FILLER_PATTERNS) text = f.replace(text, "").trim()
        if (text.isBlank()) return null

        val highlights = mutableListOf<String>()
        var confidence = 0.5f

        val dates = com.example.foodmanager.core.RecognitionDates.parse(text, voice = true)
        val production = dates.production
        val expiry = dates.expiry
        val purchaseDate = dates.purchase ?: DateUtils.today()
        if (production != null) highlights += FoodField.PRODUCTION
        if (expiry != null) highlights += FoodField.EXPIRY
        if (dates.purchase != null) highlights += FoodField.PURCHASE

        // 明确说出的地点不受常用位置表限制；只有完整名称/别名匹配才归一化。
        val locationText = text.replace(Regex("(?:开封后|储存条件|贮存条件|建议保存|建议冷藏)[^，。；;\\n]*"), "")
        val matchedLocation = matchCustomLocation(locationText, customLocations, locationAliases)
        val spokenLocation = matchSpokenLocation(locationText)
        var location = spokenLocation?.let { spoken ->
            locationAliases[spoken.place]?.takeIf(String::isNotBlank)
                ?: customLocations.firstOrNull { it == spoken.place }
                ?: normalizeSpokenLocation(spoken.place)
        } ?: matchedLocation.orEmpty()
        if (location.isNotEmpty()) {
            highlights += FoodField.LOCATION
            confidence += 0.1f
        } else {
            for ((words, target) in LOCATION_RULES) {
                if (words.any { locationText.contains(it) }) {
                    location = target
                    highlights += FoodField.LOCATION
                    confidence += 0.05f
                    break
                }
            }
        }

        // ---------------- 名称（先算名称，数量提取需要避开名称里的字） ----------------
        // 把命中的位置名与别名一并从文本里剔除，避免别名（如"冷藏"）混进食物名称
        val withoutLocation = spokenLocation?.let { text.replace(it.expression, " ") } ?: text
        val nameText = dates.matched.fold(withoutLocation) { value, matched -> value.replace(matched, " ") }
        val foodMatch = (customFoods.map { it to it } + foodAliases.entries.map { it.key to it.value })
            .filter { it.first.isNotBlank() && it.second.isNotBlank() }
            .sortedByDescending { it.first.length }.firstOrNull { nameText.contains(it.first) }
        val suggestions = VoicePhoneticCorrector.suggestions(text, customFoods + foodAliases.keys)
        val unresolvedName = suggestions.firstOrNull()?.substringAfter("原文“")?.substringBefore("”").orEmpty()
        val name = foodMatch?.second ?: unresolvedName.ifBlank { extractName(nameText, location, null, customLocations + locationAliases.keys + listOf(location)) }
        if (name.isNotBlank()) {
            highlights += FoodField.NAME
            confidence += 0.2f
        }

        // ---------------- 数量 + 单位 ----------------
        val quantityText = foodMatch?.first?.let { text.replace(it, " ") } ?: text
        val qty = com.example.foodmanager.core.RecognitionQuantity.parse(quantityText, voice = true)
        val quantity = qty.amount
        val unit = qty.unit
        if (quantity != null) highlights += FoodField.QUANTITY
        if (unit != null) highlights += FoodField.UNIT

        // ---------------- 分类 ----------------
        var category = ""
        Regex("(分类|属于|这是|类别)[:：]?\\s*([\\u4e00-\\u9fa5]{1,6})").find(text)?.let { m ->
            category = m.groupValues[2].trim()
            highlights += FoodField.CATEGORY
        }

        // ---------------- 备注 ----------------
        var note = ""
        Regex("(备注|注意|说明)[:：]?\\s*([^，。；;]{1,40})").find(text)?.let { m ->
            note = m.groupValues[2].trim()
        }

        val storageAdvice = Regex("(?:开封后|储存条件|贮存条件|建议保存|建议冷藏)[^，。；;\\n]*").find(text)?.value.orEmpty()
        note = (listOf(note, storageAdvice) + dates.warnings + qty.notes + suggestions)
            .filter { it.isNotBlank() }.joinToString("；")
        if (text != raw.trim()) note = listOf(note, "语音原文：$raw").filter { it.isNotBlank() }.joinToString("；")

        if (category.isBlank()) {
            category = OcrTextParser.categoryOf(name)
        }

        // 只有数量/单位、且没有任何日期信息时，不是一条有效记录（例如「三十 盒」）
        if (name.isBlank() && location.isBlank() && expiry == null && production == null) return null

        return FoodDraft(
            name = name,
            category = category,
            quantity = quantity ?: 1.0,
            quantitySpecified = quantity != null,
            unit = unit ?: "个",
            purchaseDate = purchaseDate,
            purchaseDateSpecified = dates.purchase != null,
            rawText = raw,
            productionDate = production,
            expiryDate = expiry,
            expiryDerivedFromProduction = dates.expiryDerivedFromProduction,
            location = location,
            note = note,
            source = defaultSource,
            highlights = highlights.distinct(),
            confidence = confidence.coerceIn(0f, 1f),
        )
    }

    /** 调试用：只跑名称抽取 */
    internal fun debugName(text: String): String = extractName(text, "", null)

    /** 支持批量语音：用「还有」「另外」「然后」「、」分隔的多条记录 */
    fun parseBatch(
        raw: String,
        defaultSource: String = "voice",
        customLocations: List<String> = emptyList(),
        customFoods: List<String> = emptyList(),
        locationAliases: Map<String, String> = emptyMap(),
        foodAliases: Map<String, String> = emptyMap(),
    ): List<FoodDraft> {
        // 用「不消耗分隔符」的方式切分：「还有三天过期」里的"还有"后面跟着时间量词，
        // 不能当成两条记录的分隔词（否则一句话会被拆成"有名称没日期"+"有日期没名称"两条残缺记录）。
        val separators = Regex(
            "[；;、]|另外|然后|以及|" +
                // 「还有」只有在后面不跟时间量词时才算分隔词
                "还有(?![\\s]*(?:\\d+(?:\\.\\d+)?|[零〇一二两三四五六七八九十]{1,3})\\s*(?:个)?\\s*(?:个月|星期|周|天|日|小时|盒|箱|瓶|袋|个))",
        )
        val parts = separators.split(raw)
            .flatMap { chunk ->
                val words = OcrTextParser.foodKeywordNames(customFoods + foodAliases.keys).filter { it.length >= 2 }
                fun foodIn(value: String) = words.filter { value.replace(" ", "").contains(it) }.maxByOrNull { it.length }
                val records = mutableListOf<String>()
                var current = ""
                for (clause in chunk.split(Regex("[，,]"))) {
                    val nextFood = foodIn(clause)
                    val currentFood = foodIn(current)
                    if (nextFood != null && currentFood != null && nextFood != currentFood) {
                        records += current
                        current = clause
                    } else current = if (current.isBlank()) clause else "$current，$clause"
                }
                if (current.isNotBlank()) records += current
                records
            }
            .map { it.trim() }
            .filter { it.length >= 2 }
        val drafts = parts.mapNotNull {
            parse(it, defaultSource, customLocations, customFoods, locationAliases, foodAliases)
        }
        if (drafts.isNotEmpty()) return mergeIncomplete(drafts)
        return listOfNotNull(
            parse(raw, defaultSource, customLocations, customFoods, locationAliases, foodAliases),
        )
    }

    /**
     * 兜底：如果拆分结果是「一条有名称没日期」+「一条只有日期没名称」，
     * 说明本来是一句话，合并回一条。
     */
    private fun mergeIncomplete(drafts: List<FoodDraft>): List<FoodDraft> {
        val named = drafts.filter { it.name.isNotBlank() }
        val orphanDate = drafts.filter { it.name.isBlank() && it.expiryDate != null }
        if (named.size == 1 && orphanDate.size == 1 && named.first().expiryDate == null) {
            val base = named.first()
            val date = orphanDate.first()
            return listOf(
                base.copy(
                    expiryDate = date.expiryDate,
                    location = base.location.ifBlank { date.location },
                    highlights = (base.highlights + date.highlights).distinct(),
                ),
            )
        }
        return drafts
    }

    // ------------------------------------------------------------------


    private val RELATIVE_RULES: List<Pair<String, Int>> = listOf(
        "大后天" to 3,
        "后天" to 2,
        "明天" to 1,
        "明儿" to 1,
        "今天" to 0,
        "今日" to 0,
        "今儿" to 0,
        "下周" to 7,
        "下个星期" to 7,
        "下星期" to 7,
        "下个月" to 30,
        "下月" to 30,
        "半个月" to 15,
        "一周" to 7,
        "一个星期" to 7,
        "两天" to 2,
        "三天" to 3,
        "一周后" to 7,
    )

    private data class SpokenLocation(val place: String, val expression: String)

    /** 动词/位置标签限定地点，句号、日期等限定结束；不依赖配置词表。 */
    private fun matchSpokenLocation(text: String): SpokenLocation? {
        return com.example.foodmanager.core.SpokenLocation.find(text)?.let { SpokenLocation(it.place, it.expression) }
    }

    private fun normalizeSpokenLocation(place: String): String {
        val canonicalWords = setOf("冰箱", "冷藏", "保鲜", "冷鲜", "冷冻", "速冻", "冰柜", "冷冻室", "厨房", "台面", "灶台", "料理台", "常温", "室温", "阴凉", "通风", "避光")
        return if (place in canonicalWords) LOCATION_RULES.firstOrNull { place in it.first }?.second ?: place else place
    }

    /** 未出现明确地点短语时，用常用位置和别名辅助匹配。 */
    private fun matchCustomLocation(
        text: String,
        customLocations: List<String>,
        aliasMap: Map<String, String> = emptyMap(),
    ): String? {
        if (customLocations.isEmpty() && aliasMap.isEmpty()) return null

        // 候选项 = 正式名 + 别名；匹配到别名时返回它对应的正式名
        val candidates = buildList {
            customLocations.forEach { add(it.trim() to it.trim()) }
            aliasMap.forEach { (alias, canonical) -> add(alias.trim() to canonical.trim()) }
        }.filter { it.first.isNotBlank() }

        // 名字长的优先，避免「茶几」抢走「茶几抽屉」
        for ((word, canonical) in candidates.sortedByDescending { it.first.length }) {
            if (canonical.isBlank()) continue
            if (text.contains(word)) return canonical
            // 去掉常见方位后缀再比一次（"茶几底下" -> "茶几"）
            for (suffix in LOCATION_SUFFIXES) {
                if (text.contains(word + suffix)) return canonical
            }
        }
        return null
    }

    private val LOCATION_SUFFIXES = listOf(
        "底下", "下面", "上面", "里面", "外面", "旁边", "附近", "上方", "下方",
        "下", "上", "里", "外", "边", "内", "中",
    )

    /** 去掉已识别的字段词，剩下的中文就是名称 */
    private fun extractName(
        text: String,
        location: String,
        unit: String?,
        /** 用户自定义的位置/食品名与其别名：也要从名称里剔除 */
        customWords: List<String> = emptyList(),
    ): String {
        // 先取明确的名称槽，量词只作为“数值+单位”移除，不能逐字删掉粉条、千层酥等名称。
        structuredName(text, customWords)?.let { return it }
        var s = text
        // 先剔除用户自定义词（长词优先，避免"茶几"截断"茶几抽屉"）
        customWords.map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()
            .sortedByDescending { it.length }
            .forEach { s = s.replace(it, " ") }
        val removable = buildList {
            addAll(LOCATION_RULES.flatMap { it.first })            // 口语里的方位/拥有/动词，都不是食物名称
            addAll(
                listOf(
                    "家里", "家中", "屋里", "我家", "俺家", "这边", "那边", "旁边", "里面", "里边", "外面",
                    "有", "还有", "放了", "放着", "放在", "放进", "搁", "塞进", "存", "剩下", "一共", "总共",
                    "买了", "买的", "购买", "购入", "今天买", "昨天买", "刚买", "新买",
                    // 状态描述不是食物名称
                    "还没有吃", "还没吃", "没有吃", "没吃", "还没喝完", "没喝完", "吃了一半", "没开封", "开封了",
                    "剩下的", "昨晚的", "昨天的", "今天剩的", "快没了", "很多", "挺多", "很少",
                    "过期", "到期", "保质期", "有效期", "生产日期",
                    "还有", "再过", "之后", "以后", "大概", "大约", "差不多",
                    "数量", "数量是", "几个", "一些",
                ),
            )
            addAll(listOf("个", "只", "枚", "颗", "粒", "头"))
            addAll(UNIT_RULES.flatMap { it.first })
            addAll(listOf("袋", "包", "盒", "瓶", "罐", "桶", "斤", "克", "千克", "毫升", "升", "把", "块", "份", "片"))
            addAll(RELATIVE_RULES.map { it.first })
            add("的")
        }
        // 先移除日期与数字
        s = Regex("\\d{4}\\s*[-/.年]\\s*\\d{1,2}\\s*[-/.月]\\s*\\d{1,2}\\s*日?").replace(s, " ")
        s = Regex("\\d{1,2}\\s*月\\s*\\d{1,2}\\s*[日号]?").replace(s, " ")
        s = Regex("\\d+(?:\\.\\d+)?").replace(s, " ")
        s = Regex("[零〇一二两三四五六七八九十百]+(?=[袋包盒瓶罐桶个只枚颗粒头斤克升把块份片])").replace(s, " ")
        for (word in removable.sortedByDescending { it.length }) {
            s = s.replace(word, " ")
        }
        s = s.replace(Regex("[\\s，,。.、；;：:！!？?~～\"'()（）\\[\\]【】]"), " ").trim()

        // 1) 优先取已知食物关键词（「家里有两个苹果」-> 苹果）
        OcrTextParser.longestFoodKeyword(s)?.let { return it }
        // 2) 否则取剩下的第一段作为品名（过滤掉纯数量/单位，例如「三十」「盒」）
        val token = s.split(' ').firstOrNull { it.isNotBlank() }?.trim() ?: ""
        if (Regex("^(?:备注|注意|说明|品牌|规格|净含量|分类|类别|数量|单位)").containsMatchIn(token)) return ""
        if (isQuantityOnly(token) || token.equals("unk", ignoreCase = true)) return ""
        return token.take(16)
    }

    /** 地点和已识别日期已替换为空格，剩下的首个完整短语可作为词库外名称。 */
    private fun structuredName(text: String, locationWords: List<String>): String? {
        var phrase = text.trim().split(Regex("[\\s，,。；;：:]|还有|再过|剩下|数量|分类|类别|备注|保质期|生产日期|还没有吃|还没吃|没有吃|没吃|还没喝完|没喝完|吃了一半|没开封|开封了|快没了"), limit = 2).first()
        phrase = phrase.replace(Regex("^(?:家里|家中|我家)?(?:有|买了|买的|买入|购入|剩下的|昨天的|昨晚的|今天剩的)"), "")
        val count = "(?:[0-9]+(?:\\.[0-9]+)?|[零〇一二两三四五六七八九十百半]+)"
        val units = UNIT_RULES.flatMap { it.first }.distinct().sortedByDescending(String::length).joinToString("|") { Regex.escape(it) }
        phrase = phrase.replace(Regex("^$count(?:$units)(?:的)?"), "")
            .replace(Regex("$count(?:$units)(?:人)?$"), "")
        if (phrase.length !in 2..16 || !Regex("[\\p{IsHan}A-Za-z]+(?:[\\p{IsHan}A-Za-z0-9]*)").matches(phrase)) return null
        if (isQuantityOnly(phrase) || phrase.equals("unk", ignoreCase = true)) return null
        if (Regex("^(?:放|存放|储存|保存在|位置|地点|今天|明天|后天|生产|到期|过期|有效期|数量|单位|品牌|规格|净含量|备注|注意|说明|没开封|开封|没吃|没有|还没)").containsMatchIn(phrase)) return null
        // 没有明确“放在…”的旧式句仍交给位置/状态兜底，不能把方位拼进名称。
        if ((LOCATION_RULES.flatMap { it.first } + locationWords.filter { it.length >= 2 }).any { phrase.contains(it) } || phrase.contains(Regex("放|还有|过期|到期|保质期"))) return null
        return phrase
    }

    /** 只由数字、数词、量词组成（"三十"、"盒"、"两箱"），不能当作食物名称 */
    private fun isQuantityOnly(token: String): Boolean {
        val t = token.trim()
        if (t.isEmpty()) return false
        val stripped = t.replace(QUANTITY_CHARS, "").trim()
        return stripped.isEmpty()
    }

    private val QUANTITY_CHARS = Regex(
        "[0-9零〇一二两三四五六七八九十百千万半几个只枚颗粒根条头块片份把捆扎袋包盒箱瓶支罐听桶斤克公斤千克毫升升大中小装]",
    )
}
