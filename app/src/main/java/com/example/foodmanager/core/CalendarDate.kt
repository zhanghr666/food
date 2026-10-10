package com.example.foodmanager.core

import java.io.Serializable
import java.util.Calendar

/** 年/月改变时保留日期，超出新月份的日数才向月末收敛。 */
data class CalendarDate(val year: Int, val month: Int, val day: Int) : Serializable {
    init { require(year > 0 && month in 1..12 && day in 1..daysInMonth(year, month)) }
    val maximumDay: Int get() = daysInMonth(year, month)
    fun withYear(value: Int) = CalendarDate(value, month, day.coerceAtMost(daysInMonth(value, month)))
    fun withMonth(value: Int) = CalendarDate(year, value, day.coerceAtMost(daysInMonth(year, value)))
    fun withDay(value: Int) = CalendarDate(year, month, value)
    fun toMillis(): Long = DateUtils.of(year, month, day)
    companion object {
        fun fromMillis(date: Long) = CalendarDate(DateUtils.year(date), DateUtils.month(date), DateUtils.day(date))
        private fun daysInMonth(year: Int, month: Int): Int = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, 1)
        }.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
}
