package com.example.foodmanager.voice

import com.example.foodmanager.core.DateTextParser
import com.example.foodmanager.core.DateUtils
import com.example.foodmanager.core.FoodDraft
import com.example.foodmanager.core.FoodField
import com.example.foodmanager.core.NumberParser
import com.example.foodmanager.core.ShelfLifeParser
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
    ): FoodDraft? {
        if (raw.isBlank()) return null
        // 先做同音字校正（西瓜被听成吸管这类问题在这里兜住），用户自定义食品也参与校正
        var text = VoicePhoneticCorrector.correct(raw, customFoods).trim()
        for (f in FILLER_PATTERNS) text = f.replace(text, "").trim()
        if (text.isBlank()) return null

        val highlights = mutableListOf<String>()
        var confidence = 0.5f

        // ---------------- 保质期 / 日期 ----------------
        var expiry: Long? = null
        var production: Long? = null
        val shelfLife = ShelfLifeParser.parse(text)

        // 相对日期
        val relative = findRelativeDate(text)
        if (relative != null) {
            expiry = relative.first
            highlights += FoodField.EXPIRY
            confidence += 0.1f
        }

        // 绝对日期（含「六月一号」这类中文日期）
        if (expiry == null) {
            val dateResult = findAbsoluteDate(text)
            if (dateResult != null) {
                val isPurchase = PURCHASE_WORDS.any { text.contains(it) }
                if (isPurchase) {
                    production = dateResult
                    highlights += FoodField.PRODUCTION
                } else {
                    expiry = dateResult
                    highlights += FoodField.EXPIRY
                }
                confidence += 0.1f
            }
        }

        // 购买时间
        val purchaseDate = findPurchaseDate(text) ?: DateUtils.today()
        if (purchaseDate != DateUtils.today()) {
            highlights += "买入时间"
        }

        // 生产日期 + 保质期 -> 过期日期
        if (production != null && expiry == null && shelfLife != null) {
            expiry = DateUtils.plusDays(production, shelfLife.days)
            highlights += FoodField.EXPIRY
        }

        // ---------------- 位置（自定义位置优先，其次内置规则） ----------------
        var location: String = matchCustomLocation(text, customLocations, locationAliases) ?: ""
        if (location.isNotEmpty()) {
            highlights += FoodField.LOCATION
            confidence += 0.1f
        } else {
            for ((words, target) in LOCATION_RULES) {
                if (words.any { text.contains(it) }) {
                    location = target
                    highlights += FoodField.LOCATION
                    confidence += 0.05f
                    break
                }
            }
        }

        // ---------------- 名称（先算名称，数量提取需要避开名称里的字） ----------------
        // 把命中的位置名与别名一并从文本里剔除，避免别名（如"冷藏"）混进食物名称
        val name = extractName(text, location, null, customLocations + locationAliases.keys)
        if (name.isNotBlank()) {
            highlights += FoodField.NAME
            confidence += 0.2f
        }

        // ---------------- 数量 + 单位 ----------------
        val qty = findQuantity(text, name)
        var quantity = qty.first
        var unit = qty.second
        if (quantity != null) {
            highlights += FoodField.QUANTITY
        }
        if (unit != null) {
            highlights += FoodField.UNIT
        }
        if (quantity == null && unit == null) {
            val implicit = implicitQuantity(text)
            quantity = implicit.first
            unit = implicit.second
            if (quantity != null) highlights += FoodField.QUANTITY
            if (unit != null) highlights += FoodField.UNIT
        }

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

        if (category.isBlank()) {
            category = OcrTextParser.categoryOf(name)
        }

        // 只有数量/单位、且没有任何日期信息时，不是一条有效记录（例如「三十 盒」）
        if (name.isBlank() && expiry == null && production == null) return null

        return FoodDraft(
            name = name,
            category = category,
            quantity = quantity ?: 1.0,
            unit = unit ?: "个",
            purchaseDate = purchaseDate,
            productionDate = production,
            expiryDate = expiry,
            location = location,
            note = note,
            source = defaultSource,
            highlights = highlights.distinct(),
            confidence = confidence.coerceIn(0f, 1f),
        )
    }

    /**
     * 抽取数量与单位。
     *
     * 一句话里可能出现多个数量（"一箱牛奶三十盒"）：
     * 中文习惯是**先说包装、后说实际数量**，所以取"数字表示最长"的那个，
     * 相同则取更靠后的 —— 即上例取 30 盒，而不是 1 箱。
     *
     * 注意：[nameToIgnore] 是已识别出的食物名（如"牛奶"），要先把名字从文本里剔除，
     * 否则"牛奶"里的"奶"会被当成量词，抢走后面的"盒"。
     */
    internal fun findQuantity(text: String, nameToIgnore: String): Pair<Double?, String?> {
        // Vosk 输出是空格分词的（"牛 奶"），名字里可能没有空格，所以要容忍字符间空格
        val scanText = if (nameToIgnore.isNotBlank()) {
            val pattern = nameToIgnore.toCharArray().joinToString("\\s*") { Regex.escape(it.toString()) }
            runCatching { text.replace(Regex(pattern), " ") }.getOrDefault(text)
        } else {
            text
        }
        val matches = QTY_PATTERN.findAll(scanText).toList()
        if (matches.isEmpty()) return null to null

        data class Best(val value: Double, val unit: String, val digits: Int, val start: Int)

        var best: Best? = null
        for (m in matches) {
            val numberText = m.groupValues[1]
            val value = NumberParser.parse(numberText) ?: continue
            val unit = normalizeUnit(m.groupValues[2])
            // 过滤掉"落在食物名里"的假量词（"一箱牛奶"里的"奶"）
            if (NumberParser.isChineseNumber(numberText) && isInsideFoodName(scanText, m.range.last)) continue
            val digits = numberText.length
            val start = m.range.first
            val current = best
            if (current == null || digits > current.digits ||
                (digits == current.digits && start > current.start)
            ) {
                best = Best(value, unit, digits, start)
            }
        }
        return if (best == null) null to null else best.value to best.unit
    }

    /** 位置 [index] 是否落在某个食物关键词内部（用于排除名字里的字被当作量词） */
    internal fun debugInsideFoodName(text: String, index: Int): Boolean = isInsideFoodName(text, index)

    /** 位置 [index] 是否落在某个食物关键词内部（用于排除名字里的字被当作量词） */
    private fun isInsideFoodName(text: String, index: Int): Boolean {
        if (index < 0 || index >= text.length) return false
        for (kw in OcrTextParser.foodKeywordNames()) {
            if (kw.length < 2) continue
            var from = 0
            while (true) {
                val at = text.indexOf(kw, from)
                if (at < 0) break
                if (index in at until (at + kw.length)) return true
                from = at + 1
            }
        }
        return false
    }

    /** 没有数量时，只要出现单位词就补一个默认单位 */
    private fun implicitQuantity(text: String): Pair<Double?, String?> {
        for ((words, target) in UNIT_RULES) {
            if (words.any { text.contains(it) }) return null to target
        }
        return null to null
    }

    private fun normalizeUnit(unitText: String): String =
        UNIT_RULES.firstOrNull { it.first.any { a -> a.equals(unitText, ignoreCase = true) } }?.second
            ?: unitText

    /** 数量 + 单位 的匹配模式（数词或阿拉伯数字 + 量词） */
    private val QTY_PATTERN = Regex(
        "(\\d+(?:\\.\\d+)?|[零〇一二两三四五六七八九十百]+)\\s*" +
            "(袋|包|盒|箱|瓶|支|罐|听|桶|个|只|枚|颗|粒|根|条|头|斤|千克|公斤|kg|KG|克|g|毫升|ml|ML|升|L|把|捆|扎|块|份|片)"
    )

    /** 绝对日期：优先标准格式，其次「六月一号」「六月一日」这类全中文日期 */
    private fun findAbsoluteDate(text: String): Long? {
        DateTextParser.findFirst(text)?.let { return it.date }
        DateTextParser.findCompact6(text)?.let { return it.date }

        // 六月一号 / 6月1号 / 六月一日
        Regex("([0-9]{1,2}|[一二三四五六七八九十]{1,3})\\s*月\\s*([0-9]{1,2}|[一二三四五六七八九十〇零]{1,3})\\s*[日号]").find(text)?.let { m ->
            val month = NumberParser.parse(m.groupValues[1])?.toInt()
            val day = NumberParser.parse(m.groupValues[2])?.toInt()
            if (month != null && day != null && month in 1..12 && day in 1..31) {
                return DateUtils.of(inferYear(month, day), month, day)
            }
        }
        // 只有「一号」这类日期，取本月
        Regex("([0-9]{1,2}|[一二三四五六七八九十]{1,3})\\s*[日号]\\s*(过期|到期)").find(text)?.let { m ->
            val day = NumberParser.parse(m.groupValues[1])?.toInt()
            val today = DateUtils.today()
            val month = DateUtils.month(today)
            if (day != null && day in 1..31) {
                val candidate = DateUtils.of(DateUtils.year(today), month, day)
                return if (candidate >= today) candidate else DateUtils.of(inferYear(month, day), month, day)
            }
        }
        return null
    }

    /** 与 DateTextParser 一致：无年份时推断最近一次出现该日期的年份 */
    private fun inferYear(month: Int, day: Int): Int {
        val today = DateUtils.today()
        val thisYear = DateUtils.year(today)
        val candidate = DateUtils.of(thisYear, month, day)
        return if (DateUtils.daysBetween(candidate, today) > 180) thisYear + 1 else thisYear
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
            "[；;、，,]|另外|然后|以及|" +
                // 「还有」只有在后面不跟时间量词时才算分隔词
                "还有(?![\\s]*(?:\\d+(?:\\.\\d+)?|[零〇一二两三四五六七八九十]{1,3})\\s*(?:个)?\\s*(?:个月|星期|周|天|日|小时))",
        )
        val parts = separators.split(raw)
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

    private val PURCHASE_WORDS = listOf("买的", "买入", "购买", "购入", "今天买", "昨天买", "刚买")

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

    private fun findRelativeDate(text: String): Pair<Long, String>? {
        // 「还有三天过期」「三天后到期」
        Regex("(还有|再过|过了)?\\s*([零〇一二三四五六七八九十\\d]{1,3})\\s*(天|日|周|个?星期|个月|月)\\s*(后|之后|以后)?\\s*(过期|到期|坏)").find(text)?.let { m ->
            val value = NumberParser.parse(m.groupValues[2]) ?: return@let
            val unit = m.groupValues[3]
            val days = when {
                unit.contains("周") || unit.contains("星期") -> (value * 7).toInt()
                unit.contains("月") -> (value * 30).toInt()
                else -> value.toInt()
            }
            return DateUtils.todayPlus(days) to m.value
        }
        for ((word, days) in RELATIVE_RULES.sortedByDescending { it.first.length }) {
            if (text.contains(word)) {
                // 「明天买的」是买入时间，不是过期时间
                val index = text.indexOf(word)
                val tail = text.substring(index + word.length, (index + word.length + 4).coerceAtMost(text.length))
                val isPurchase = PURCHASE_WORDS.any { tail.contains(it.take(2)) } || tail.contains("买")
                if (!isPurchase) return DateUtils.todayPlus(days) to word
            }
        }
        return null
    }

    private fun findPurchaseDate(text: String): Long? {
        if (!PURCHASE_WORDS.any { text.contains(it) }) return null
        for ((word, days) in RELATIVE_RULES) {
            if (text.contains(word)) return DateUtils.todayPlus(days)
        }
        val dateResult = DateTextParser.findFirst(text)
        return dateResult?.date
    }

    /**
     * 匹配用户自定义位置。支持后缀变体：配置了「茶几」时，
     * 说「茶几底下」「茶几下面」「茶几上」也能识别到「茶几」。
     *
     * 也支持**别名**：给「冰箱」配了别名「冷藏」后，说「放冷藏」会归到「冰箱」。
     * 返回的是**正式名**（不是别名），这样入库后的位置文字始终统一。
     */
    private fun matchCustomLocation(
        text: String,
        customLocations: List<String>,
        aliasMap: Map<String, String> = emptyMap(),
    ): String? {
        if (customLocations.isEmpty()) return null

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
        if (isQuantityOnly(token)) return ""
        return token.take(16)
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
