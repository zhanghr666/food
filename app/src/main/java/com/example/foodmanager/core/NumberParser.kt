package com.example.foodmanager.core

/**
 * 中文数字 / 阿拉伯数字统一转换工具。
 * 支持：500、1.5、两、三、五、十、十五、二十、二十三、一百、两千、半。
 */
object NumberParser {

    private val cnDigits = mapOf(
        '零' to 0, '〇' to 0, 'O' to 0, 'o' to 0,
        '一' to 1, '壹' to 1, '幺' to 1,
        '二' to 2, '贰' to 2, '两' to 2,
        '三' to 3, '叁' to 3, '仨' to 3,
        '四' to 4, '肆' to 4,
        '五' to 5, '伍' to 5,
        '六' to 6, '陆' to 6,
        '七' to 7, '柒' to 7,
        '八' to 8, '捌' to 8,
        '九' to 9, '玖' to 9,
    )

    private val cnUnits = mapOf(
        '十' to 10, '拾' to 10,
        '百' to 100, '佰' to 100,
        '千' to 1000, '仟' to 1000,
        '万' to 10000,
    )

    /** 是否由中文数字组成 */
    fun isChineseNumber(text: String): Boolean =
        text.isNotEmpty() && text.all { it in cnDigits || it in cnUnits || it == '点' }

    /**
     * 解析数量文本，返回 null 表示无法解析。
     * "2" -> 2.0, "1.5" -> 1.5, "两" -> 2.0, "十二" -> 12.0, "半" -> 0.5
     */
    fun parse(text: String): Double? {
        val t = text.trim()
        if (t.isEmpty()) return null
        t.toDoubleOrNull()?.let { return it }
        if (t == "半") return 0.5

        // 1. 阿拉伯数字 + 中文单位 或 纯中文数字
        if (isChineseNumber(t)) return parseChinese(t)
        return null
    }

    private fun parseChinese(input: String): Double {
        var result = 0.0
        var section = 0.0
        var current = 0.0
        var decimal = 0.0
        var decimalScale = 10.0
        var inDecimal = false

        for (ch in input) {
            if (ch == '点') {
                inDecimal = true
                continue
            }
            val digit = cnDigits[ch]
            if (digit != null) {
                if (inDecimal) {
                    decimal += digit / decimalScale
                    decimalScale *= 10
                } else {
                    current = digit.toDouble()
                }
                continue
            }
            val unit = cnUnits[ch]
            if (unit != null) {
                if (unit == 10000) {
                    section = (section + (if (current == 0.0) 1.0 else current)) * unit
                    result += section
                    section = 0.0
                    current = 0.0
                } else {
                    // "十五" -> 15 ；"二十" -> 20 ；"两百" -> 200
                    val base = if (current == 0.0) 1.0 else current
                    section += base * unit
                    current = 0.0
                }
            }
        }
        result += section + current + decimal
        return if (result == 0.0 && input.any { it in cnDigits }) result else result
    }

    /** 从「一箱」这种词里取出中文数字部分 */
    fun findChineseNumber(text: String): Double? {
        val match = Regex("[零〇一壹二贰两三四肆五伍六陆七柒八捌九玖十拾百佰千仟万点]+").find(text) ?: return null
        return parseChinese(match.value)
    }
}

/** 日期文本解析：支持 2025-06-01 / 2025/6/1 / 2025.6.1 / 2025年6月1日 / 20250601 / 250601 / 6月1日 */
object DateTextParser {

    data class DateResult(
        val date: Long,
        val hasYear: Boolean,
        val matched: String,
    )

    private val cnYearDigits = mapOf(
        '〇' to '0', '零' to '0',
        '一' to '1', '二' to '2', '三' to '3', '四' to '4',
        '五' to '5', '六' to '6', '七' to '7', '八' to '8', '九' to '9',
    )

    /** 把「二〇二五」转成 2025 */
    fun normalizeChineseYear(text: String): String =
        text.map { cnYearDigits[it] ?: it }.joinToString("")

    private fun build(year: Int, month: Int, day: Int, hasYear: Boolean, matched: String): DateResult? {
        if (month !in 1..12) return null
        val maxDay = daysInMonth(year, month)
        if (day !in 1..maxDay) return null
        return DateResult(DateUtils.of(year, month, day), hasYear, matched)
    }

    fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
        else -> 30
    }

    /** 在当前年份附近推断一个合理的年份（处理无年份的「6月1日」） */
    private fun inferYear(month: Int, day: Int): Int {
        val today = DateUtils.today()
        val thisYear = DateUtils.year(today)
        val candidate = DateUtils.of(thisYear, month, day)
        // 如果这个日期比今天早 6 个月以上，认为是明年的（例如 12 月看到「1月1日」）
        return if (DateUtils.daysBetween(candidate, today) > 180) thisYear + 1 else thisYear
    }

    /**
     * 在文本中查找第一个日期。可选 [requireKeyword] 限定必须出现在某个关键词之后。
     */
    fun findFirst(text: String): DateResult? {
        // 带分隔符的完整日期 2025-06-01 / 2025/6/1 / 2025.6.1 / 2025年6月1日
        Regex("(\\d{4})\\s*[-/.年]\\s*(\\d{1,2})\\s*[-/.月]\\s*(\\d{1,2})\\s*日?").find(text)?.let { m ->
            build(
                m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(),
                true, m.value,
            )?.let { return it }
        }
        // 中文年份 二〇二五年六月一日
        Regex("([〇零一二三四五六七八九]{4})\\s*年\\s*([〇零一二三四五六七八九\\d]{1,3})\\s*月\\s*([〇零一二三四五六七八九\\d]{1,3})\\s*日?").find(text)?.let { m ->
            val y = normalizeChineseYear(m.groupValues[1]).toIntOrNull()
            val mo = normalizeChineseYear(m.groupValues[2]).toIntOrNull()
            val d = normalizeChineseYear(m.groupValues[3]).toIntOrNull()
            if (y != null && mo != null && d != null) {
                build(y, mo, d, true, m.value)?.let { return it }
            }
        }
        // 紧凑 8 位 20250601
        Regex("(?<!\\d)(20\\d{2})(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])(?!\\d)").find(text)?.let { m ->
            build(
                m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(),
                true, m.value,
            )?.let { return it }
        }
        // 年 + 月 2025年6月
        Regex("(\\d{4})\\s*年\\s*(\\d{1,2})\\s*月").find(text)?.let { m ->
            build(m.groupValues[1].toInt(), m.groupValues[2].toInt(), 1, true, m.value)?.let { return it }
        }
        // 年月 202506 -> 2025-06-01
        Regex("(?<!\\d)(20\\d{2})(0[1-9]|1[0-2])(?!\\d)").find(text)?.let { m ->
            build(m.groupValues[1].toInt(), m.groupValues[2].toInt(), 1, true, m.value)?.let { return it }
        }
        // 只有月日 6月1日 / 6-1
        Regex("(\\d{1,2})\\s*[-/.月]\\s*(\\d{1,2})\\s*日?").find(text)?.let { m ->
            val mo = m.groupValues[1].toInt()
            val d = m.groupValues[2].toInt()
            val year = inferYear(mo, d)
            build(year, mo, d, false, m.value)?.let { return it }
        }
        return null
    }

    /** 紧凑 6 位 yyMMdd，例如 250601 / 260101（OCR 常见） */
    fun findCompact6(text: String): DateResult? {
        Regex("(?<!\\d)(2[0-9])(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])(?!\\d)").find(text)?.let { m ->
            val year = 2000 + m.groupValues[1].toInt()
            return build(
                year, m.groupValues[2].toInt(), m.groupValues[3].toInt(), true, m.value,
            )
        }
        return null
    }
}

/** 保质期时长解析：12个月 / 18 月 / 30天 / 一年 / 两年 / 3周 */
object ShelfLifeParser {

    data class Duration(val days: Int, val matched: String)

    fun parse(text: String): Duration? {
        // 数字 + 年/月/天/日/周
        Regex("(\\d+(?:\\.\\d+)?)\\s*(个月|月|年|天|日|周|星期|小时)").find(text)?.let { m ->
            val value = m.groupValues[1].toDoubleOrNull() ?: return@let
            val unit = m.groupValues[2]
            return Duration(toDays(value, unit), m.value)
        }
        // 中文数字 + 年/月/天
        Regex("([零〇一二三四五六七八九十百两]+)\\s*(个月|月|年|天|日|周)").find(text)?.let { m ->
            val value = NumberParser.parse(m.groupValues[1]) ?: return@let
            return Duration(toDays(value, m.groupValues[2]), m.value)
        }
        return null
    }

    private fun toDays(value: Double, unit: String): Int = when (unit) {
        "年" -> (value * 365).toInt()
        "个月", "月" -> (value * 30).toInt()
        "周", "星期" -> (value * 7).toInt()
        "小时" -> (value / 24).toInt().coerceAtLeast(1)
        else -> value.toInt() // 天 / 日
    }
}
