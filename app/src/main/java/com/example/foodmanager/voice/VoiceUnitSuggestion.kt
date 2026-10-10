package com.example.foodmanager.voice

/** “个人”只在紧邻食品名称的数量槽内提出量词建议，不改受益人、人数或原文。 */
object VoiceUnitSuggestion {
    private val shapeUnits = mapOf("甘蔗" to "根", "黄瓜" to "根", "胡萝卜" to "根", "香蕉" to "根", "大葱" to "根", "小葱" to "根", "玉米" to "根")
    fun suggest(raw: String, heardName: String, foodName: String, preferredUnit: String = ""): String? {
        if (heardName.isBlank() || foodName.isBlank()) return null
        // 词库外物品不能仅因名字相邻就推断形状或单位。
        if (foodName !in com.example.foodmanager.ocr.OcrTextParser.foodKeywordNames() && preferredUnit.isBlank()) return null
        val compact = raw.replace(Regex("(?<=[\\p{IsHan}0-9]) +(?=[\\p{IsHan}0-9])"), "")
        val name = Regex.escape(heardName)
        val count = "[0-9零〇一二两三四五六七八九十百]+"
        val ending = "(?=放|在|还有|[，,。；;]|$)"
        if (!Regex("$name(?:数量[:：是为]*)?${count}个人$ending|${count}个人(?:的)?$name$ending").containsMatchIn(compact)) return null
        return preferredUnit.takeIf(String::isNotBlank) ?: shapeUnits[foodName] ?: "个"
    }
}
