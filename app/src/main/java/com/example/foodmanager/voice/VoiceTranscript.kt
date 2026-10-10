package com.example.foodmanager.voice

/** 每次端点 result 是独立片段；partial 只属于尚未完成的尾段。 */
class VoiceTranscript {
    private val segments = mutableListOf<String>()
    private var partial = ""
    private var paths = listOf("")
    fun acceptCompleted(text: String, alternatives: List<String> = emptyList()): String {
        if (text.isNotBlank()) segments += text.trim()
        appendAlternatives(text, alternatives)
        partial = ""
        return text()
    }
    fun acceptPartial(text: String): String {
        partial = text.trim()
        return text()
    }
    fun finish(tail: String, alternatives: List<String> = emptyList()): String {
        val finalTail = tail.trim().ifBlank { partial }
        partial = ""
        if (finalTail.isNotBlank()) segments += finalTail
        appendAlternatives(finalTail, alternatives)
        return text()
    }
    fun text(): String = (segments + partial).filter { it.isNotBlank() }.joinToString(" ")
    fun alternatives(): List<String> = paths.filter { it.isNotBlank() && it != text() }.take(4)

    private fun appendAlternatives(primary: String, alternatives: List<String>) {
        val options = (listOf(primary) + alternatives).map(String::trim).filter(String::isNotBlank).distinct().take(3)
        if (options.isEmpty()) return
        // 保留主路径及每个端点的替代结果，限制组合规模，不丢掉已结束的前半句。
        val combinations = options.flatMap { option -> paths.map { prefix -> listOf(prefix, option).filter(String::isNotBlank).joinToString(" ") } }
        paths = combinations.distinct().take(5)
    }
}
