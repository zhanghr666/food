package com.example.foodmanager.core

/** 规格与营养数值不能变成库存数量。只有明确的库存表达才覆盖歧义。 */
object RecognitionQuantity {
    data class Result(val amount: Double? = null, val unit: String? = null, val notes: List<String> = emptyList())
    private val pattern = Regex("([0-9]+(?:\\.[0-9]+)?|[零〇一二两三四五六七八九十百半]+)\\s*(千克|公斤|毫升|kg|ml|袋|包|盒|箱|瓶|支|罐|听|桶|个|只|枚|颗|粒|根|条|头|斤|克|g|升|l|把|捆|扎|块|份|片)", RegexOption.IGNORE_CASE)
    private val measures = setOf("千克", "毫升", "斤", "克", "升")
    private fun unit(raw: String) = when (raw.lowercase()) {
        "公斤", "kg" -> "千克"; "g" -> "克"; "ml" -> "毫升"; "l" -> "升"
        "只", "枚", "颗", "粒", "头" -> "个"
        else -> raw
    }
    fun parse(raw: String, voice: Boolean = false): Result {
        val text = raw.replace(Regex("(?<=[\\p{IsHan}0-9]) +(?=[\\p{IsHan}0-9])"), "")
        data class Candidate(val value: Double, val unit: String, val explicit: Boolean)
        val candidates = mutableListOf<Candidate>()
        val notes = mutableListOf<String>()
        var nutrition = false
        text.split(Regex("[\\n，,；;。]")).forEach { line ->
            if (line.contains(Regex("营养成分|营养信息|NRV", RegexOption.IGNORE_CASE))) nutrition = true
            if (line.contains(Regex("生产日期|保质期|储存|贮存|库存|买了|数量"))) nutrition = false
            pattern.findAll(line).forEach { m ->
                val value = NumberParser.parse(m.groupValues[1]) ?: return@forEach
                val u = unit(m.groupValues[2])
                val prefix = line.substring(0, m.range.first)
                val tail = line.substring(m.range.last + 1)
                val explicit = Regex("(?:库存|数量|买了|买入|购入|剩余|剩下|一共|总共|合计)[:：是为有]*$").containsMatchIn(prefix)
                val spec = Regex("净含量|净重|规格|每盒|每瓶|每袋|每包|每份|每箱|每个").containsMatchIn(prefix) ||
                    Regex("每[:：]?$|[×xX*]$").containsMatchIn(prefix) || Regex("^[/／](盒|瓶|袋|包|份|箱)").containsMatchIn(tail)
                if (!explicit && (nutrition || spec || (!voice && u in measures))) {
                    notes += "规格/营养信息：${m.value}"
                } else candidates += Candidate(value, u, explicit)
            }
        }
        val explicit = candidates.filter { it.explicit }.distinctBy { it.value to it.unit }
        val possible = (if (explicit.isNotEmpty()) explicit else candidates).distinctBy { it.value to it.unit }
        val selected = possible.singleOrNull()
        if (possible.size > 1) notes += "存在多个数量，请确认库存数量和单位"
        return Result(selected?.value, selected?.unit, notes.distinct())
    }
}
