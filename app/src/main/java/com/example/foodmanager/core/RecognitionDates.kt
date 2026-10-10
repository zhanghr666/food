package com.example.foodmanager.core

import java.util.Calendar

/** 只接受有明确角色的日期；不把购买时间或孤立喷码猜成生产时间。 */
object RecognitionDates {
    data class Result(
        val purchase: Long?, val production: Long?, val expiry: Long?,
        val matched: List<String>, val warnings: List<String>,
        val expiryDerivedFromProduction: Boolean = false,
    )

    private const val N = "[0-9零〇一二两三四五六七八九十百]+"
    private val date = Regex(
        "(?:[12][0-9]{3}[-/.年][0-9]{1,2}[-/.月][0-9]{1,2}日?)|" +
            "(?<![0-9])[12][0-9]{7}(?![0-9])|" +
            "(?:(?:[0-9零〇一二三四五六七八九]{4})年)?${N}月${N}[日号]|" +
            "大前天|前天|昨天|大后天|后天|明天|今天|今日|明儿|今儿|" +
            "(?:还有|再过)?${N}(?:个?星期|周|个月|天)(?:之后|以后|后)?",
    )
    private val role = Regex("购买日期|买入时间|买入日期|购买|购入|买入|买的|买了|生产日期|制造日期|生产|制造|保质期至|有效期至|到期日期|到期日|到期|过期|有效期|EXP|MFG", RegexOption.IGNORE_CASE)
    private val connectors = Regex("^[\\s:：的是在于为至]*$")
    private fun kind(label: String): Int = when {
        label.contains("买") || label.contains("购") -> 0
        label.contains("生产") || label.contains("制造") || label.equals("MFG", true) -> 1
        else -> 2
    }

    fun parse(raw: String, voice: Boolean = false): Result {
        val text = raw.replace(Regex("(?<=[\\p{IsHan}0-9]) +(?=[\\p{IsHan}0-9])"), "")
        val tokens = date.findAll(text).toList()
        val values = Array(3) { mutableListOf<Long>() }
        val warnings = mutableListOf<String>()
        val invalid = mutableSetOf<Int>()
        tokens.forEachIndexed { index, token ->
            val left = text.substring(if (index == 0) 0 else tokens[index - 1].range.last + 1, token.range.first)
            val right = text.substring(token.range.last + 1, tokens.getOrNull(index + 1)?.range?.first ?: text.length)
            val prefix = role.findAll(left).lastOrNull()?.takeIf { connectors.matches(left.substring(it.range.last + 1)) }
            val suffix = role.find(right)?.takeIf { connectors.matches(right.substring(0, it.range.first)) }
            // 「10月1日购买，10月8日到期」优先后缀；「生产日期：...」优先明确前缀。
            val label = prefix?.takeIf { it.value.contains("日期") || it.value.contains("至") || left.substring(it.range.last + 1).contains(Regex("[:：]")) }
                ?: suffix ?: prefix
            if (label == null) {
                // 保质期天数由下方计算，不属于孤立日期。
                if (!left.endsWith("保质期") && !left.endsWith("保质期：") && !left.endsWith("保质期:")) warnings += "日期含义待确认：${token.value}"
            } else {
                val k = kind(label.value)
                val value = resolve(token.value, voice)
                if (value == null) { invalid += k; warnings += "日期无效或不完整：${token.value}" }
                else values[k] += value
            }
        }
        fun unique(k: Int): Long? {
            val distinct = values[k].distinct()
            if (distinct.size > 1) warnings += "${listOf("购买", "生产", "到期")[k]}日期存在冲突，请核对原文"
            return distinct.singleOrNull().takeUnless { k in invalid }
        }
        val purchase = unique(0)
        val production = unique(1)
        var expiry = unique(2)
        var derived = false
        val shelf = Regex("保质期[:：是为]*($N)(天|日|周|个?月|年)").find(text)
        if (expiry == null && values[2].isEmpty() && 2 !in invalid && production != null && shelf != null) {
            val amount = NumberParser.parse(shelf.groupValues[1])?.toInt()
            if (amount != null && amount in 1..36500) {
                expiry = add(production, amount, shelf.groupValues[2])
                derived = true
            }
        }
        if (production != null && expiry != null && expiry!! < production) {
            warnings += "到期日期早于生产日期，请核对原文"
            expiry = null
        }
        return Result(purchase, production, expiry, tokens.map { it.value }, warnings.distinct(), derived && expiry != null)
    }

    private fun resolve(text: String, voice: Boolean): Long? {
        if (text.matches(Regex("[12][0-9]{7}"))) return strict(text.take(4).toInt(), text.substring(4, 6).toInt(), text.takeLast(2).toInt())
        Regex("([12][0-9]{3})[-/.年]([0-9]{1,2})[-/.月]([0-9]{1,2})日?").matchEntire(text)?.let {
            return strict(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt())
        }
        Regex("(?:([0-9零〇一二三四五六七八九]{4})年)?($N)月($N)[日号]").matchEntire(text)?.let {
            val month = NumberParser.parse(it.groupValues[2])?.toInt() ?: return null
            val day = NumberParser.parse(it.groupValues[3])?.toInt() ?: return null
            val yearText = it.groupValues[1]
            if (yearText.isBlank() && !voice) return null
            val year = if (yearText.isBlank()) DateUtils.year(DateUtils.today()) else yearText.map { c ->
                if (c.isDigit()) c else "零一二三四五六七八九".indexOf(if (c == '〇') '零' else c).digitToChar()
            }.joinToString("").toInt()
            return strict(year, month, day)
        }
        if (!voice) return null
        val offsets = mapOf("大前天" to -3, "前天" to -2, "昨天" to -1, "今天" to 0, "今日" to 0, "今儿" to 0, "明天" to 1, "明儿" to 1, "后天" to 2, "大后天" to 3)
        offsets[text]?.let { return DateUtils.todayPlus(it) }
        Regex("(?:还有|再过)?($N)(个?星期|周|个月|天)(?:之后|以后|后)?").matchEntire(text)?.let {
            val amount = NumberParser.parse(it.groupValues[1])?.toInt() ?: return null
            if (amount !in 0..36500) return null
            return add(DateUtils.today(), amount, it.groupValues[2])
        }
        return null
    }

    private fun strict(year: Int, month: Int, day: Int): Long? = runCatching {
        Calendar.getInstance().apply { clear(); isLenient = false; set(year, month - 1, day) }.timeInMillis
    }.getOrNull()

    private fun add(start: Long, count: Int, unit: String): Long = Calendar.getInstance().apply {
        timeInMillis = start
        when {
            unit.contains("月") -> add(Calendar.MONTH, count)
            unit == "年" -> add(Calendar.YEAR, count)
            unit == "周" || unit.contains("星期") -> add(Calendar.DAY_OF_MONTH, count * 7)
            else -> add(Calendar.DAY_OF_MONTH, count)
        }
    }.timeInMillis
}
