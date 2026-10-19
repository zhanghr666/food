package com.example.foodmanager.core

/** 食品和物品共用的地点边界识别；地点本身不受预设选项限制。 */
data class SpokenPlace(val place: String, val expression: String)
object SpokenLocation {
    fun find(text: String): SpokenPlace? {
        val number = "[0-9零〇一二两三四五六七八九十百]+"
        val duration = "$number(?:个)?(?:天|月|日|号|年|周|星期|小时)"
        val ending = "放着|摆着|搁着|存着|[，,。；;！？!?\\n]|今天|明天|后天|下周|下个?月|(?:还有|有|剩下|剩|再过)?$duration|到期|过期|保质期|生产日期|数量|分类|类别|属于|备注|品牌|型号|规格|提醒|检查|$"
        val prefix = "(?:存放在|存放于|储存在|储存于|保存在|放到了|放在|放到|放进|搁在|摆在|置于|收在|收进|(?:存放位置|位置|地点)[:：是为在]+|放|搁|在)(?:了)?\\s*"
        return Regex("$prefix([\\p{IsHan}A-Za-z0-9 _-]{1,80}?)(?=$ending)").findAll(text).mapNotNull {
            val place = it.groupValues[1].trim()
            if (place in setOf("这", "那", "这儿", "那儿", "这里", "那里", "这边", "那边", "unk", "的") ||
                Regex("(?:$duration|今天|明天|后天|下周|下个月)(?:后|内|以后|之后)?").matches(place)) null
            else SpokenPlace(place, it.value)
        }.firstOrNull()
    }
}
