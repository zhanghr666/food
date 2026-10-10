package com.example.foodmanager.core

/** 仅修复明确到期倒计时中的疑似转写单位；保留原文并要求用户确认推断日期。 */
object VoiceTimeRepair {
    data class Result(val text: String, val warnings: List<String>)
    private val units = mapOf("千" to "天", "签" to "天", "钱" to "天", "田" to "天", "添" to "天", "填" to "天",
        "越" to "个月", "粤" to "个月", "岳" to "个月", "念" to "年", "州" to "周", "舟" to "周")
    private val number = "[0-9零〇一二两三四五六七八九十百]+"
    private val pattern = Regex("(还有|再过|剩下|剩)($number)(${units.keys.joinToString("|")})(之后|以后|后)?(就|会|将)?(过期|到期)|" +
        "($number)(${units.keys.joinToString("|")})(之后|以后|后)(就|会|将)?(过期|到期)")

    fun correct(raw: String): Result {
        val compact = raw.replace(Regex("(?<=[\\p{IsHan}0-9]) +(?=[\\p{IsHan}0-9])"), "")
        val warnings = mutableListOf<String>()
        val text = pattern.replace(compact) { match ->
            val first = match.groupValues[1].isNotBlank()
            val amount = NumberParser.parse(match.groupValues[if (first) 2 else 7])?.toInt()
            val unit = match.groupValues[if (first) 3 else 8]
            val replacement = units.getValue(unit)
            val max = if (replacement == "年") 100 else 36500
            if (amount == null || amount !in 0..max) match.value else {
                val corrected = if (first) match.groupValues[1] + match.groupValues[2] + replacement + match.groupValues[4] + match.groupValues[5] + match.groupValues[6]
                    else match.groupValues[7] + replacement + match.groupValues[9] + match.groupValues[10] + match.groupValues[11]
                warnings += "时间单位可能识别错了：“${match.value}”可能是“$corrected”，请确认到期日期"
                corrected
            }
        }
        return Result(text, warnings.distinct())
    }
}
