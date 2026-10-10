package com.example.foodmanager.voice

/** 每次端点 result 是独立片段；partial 只属于尚未完成的尾段。 */
class VoiceTranscript {
    private val segments = mutableListOf<String>()
    private var partial = ""
    fun acceptCompleted(text: String): String {
        if (text.isNotBlank()) segments += text.trim()
        partial = ""
        return text()
    }
    fun acceptPartial(text: String): String {
        partial = text.trim()
        return text()
    }
    fun finish(tail: String): String {
        val finalTail = tail.trim().ifBlank { partial }
        partial = ""
        if (finalTail.isNotBlank()) segments += finalTail
        return text()
    }
    fun text(): String = (segments + partial).filter { it.isNotBlank() }.joinToString(" ")
}
