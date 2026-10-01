package com.example.foodmanager.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * 日期工具。全部基于「当地时区当日 0 点」的 epoch 毫秒，避免时区导致的跨天误差。
 * 为了兼容 minSdk 24（无 java.time），这里统一使用 Calendar / SimpleDateFormat。
 */
object DateUtils {

    private val TZ: TimeZone get() = TimeZone.getDefault()

    private fun calendar(): Calendar = Calendar.getInstance(TZ, Locale.CHINA)

    /** 今天 0 点 */
    fun today(): Long = startOfDay(System.currentTimeMillis())

    /** 把任意时间戳归零到当天 0 点 */
    fun startOfDay(millis: Long): Long {
        val c = calendar()
        c.timeInMillis = millis
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** 以今天为基准偏移天数 */
    fun todayPlus(days: Int): Long {
        val c = calendar()
        c.timeInMillis = today()
        c.add(Calendar.DAY_OF_YEAR, days)
        return c.timeInMillis
    }

    fun plusDays(base: Long, days: Int): Long {
        val c = calendar()
        c.timeInMillis = base
        c.add(Calendar.DAY_OF_YEAR, days)
        return startOfDay(c.timeInMillis)
    }

    /** 构造日期（月为 1-12） */
    fun of(year: Int, month: Int, day: Int): Long {
        val c = calendar()
        c.clear()
        c.set(year, month - 1, day, 0, 0, 0)
        return c.timeInMillis
    }

    fun year(date: Long): Int = calendar().apply { timeInMillis = date }.get(Calendar.YEAR)
    fun month(date: Long): Int = calendar().apply { timeInMillis = date }.get(Calendar.MONTH) + 1
    fun day(date: Long): Int = calendar().apply { timeInMillis = date }.get(Calendar.DAY_OF_MONTH)

    /** 日期 -> yyyy-MM-dd */
    fun format(date: Long?): String {
        if (date == null) return "—"
        return SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(date))
    }

    /** 日期 -> M月d日 */
    fun formatShort(date: Long?): String {
        if (date == null) return "—"
        return SimpleDateFormat("M月d日", Locale.CHINA).format(Date(date))
    }

    /** 日期 -> yyyy年M月d日 */
    fun formatCn(date: Long?): String {
        if (date == null) return "—"
        return SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(date))
    }

    fun formatDateTime(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(millis))

    /** 解析 yyyy-MM-dd / yyyy/MM/dd，失败返回 null */
    fun parse(text: String): Long? {
        val t = text.trim()
        val patterns = listOf("yyyy-MM-dd", "yyyy/MM/dd", "yyyy.MM.dd", "yyyyMMdd")
        for (p in patterns) {
            try {
                val fmt = SimpleDateFormat(p, Locale.CHINA)
                fmt.isLenient = false
                val d = fmt.parse(t) ?: continue
                return startOfDay(d.time)
            } catch (_: Exception) {
                // try next
            }
        }
        return null
    }

    /** 天数差（按自然日） */
    fun daysBetween(from: Long, to: Long): Long =
        TimeUnit.MILLISECONDS.toDays(startOfDay(to) - startOfDay(from))

    /** 人类可读的剩余天数描述 */
    fun describeRemaining(days: Long?): String = when {
        days == null -> "无保质期"
        days < 0 -> "已过期 ${-days} 天"
        days == 0L -> "今天到期"
        days == 1L -> "明天到期"
        days <= 30 -> "还剩 $days 天"
        else -> "还剩 $days 天"
    }

    /** 相对日期描述（用于列表里的买入时间） */
    fun describeRelative(date: Long): String {
        val diff = daysBetween(date, today())
        return when {
            diff == 0L -> "今天"
            diff == 1L -> "昨天"
            diff == 2L -> "前天"
            diff in 3..6 -> "$diff 天前"
            else -> format(date)
        }
    }
}
