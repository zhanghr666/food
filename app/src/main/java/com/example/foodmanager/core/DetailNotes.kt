package com.example.foodmanager.core

/** 只在显示层区分来源和备注，不更改数据库原文；无法确定的文字仍作为备注展示。 */
data class DetailNotes(val user: String, val source: String, val dateWarnings: List<String>) {
    companion object {
        fun from(note: String): DetailNotes {
            val marker = Regex("(?:^|[；;\\n])\\s*(?:语音原文|识别原文)[:：]").find(note)
            val source = marker?.let { note.substring(it.range.last + 1).trim() }.orEmpty()
            val before = marker?.let { note.substring(0, it.range.first) } ?: note
            val warnings = mutableListOf<String>()
            val user = before.split(Regex("(?<=[；;\\n])")).filter { part ->
                val value = part.trim().trimEnd('；', ';').trim()
                val warning = value.startsWith("日期含义待确认：") || value.startsWith("日期无效或不完整：") ||
                    value.matches(Regex("(?:购买|生产|到期)日期存在冲突，请核对原文")) || value == "到期日期早于生产日期，请核对原文"
                if (warning) warnings += value
                !warning
            }.joinToString("").trim(' ', '；', ';', '\n')
            return DetailNotes(if (marker == null && warnings.isEmpty()) note else user, source, warnings)
        }
    }
}
