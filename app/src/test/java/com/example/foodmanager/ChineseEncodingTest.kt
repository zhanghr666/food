package com.example.foodmanager

import com.example.foodmanager.data.Locations
import com.example.foodmanager.data.Units
import com.example.foodmanager.voice.VoiceTextParser
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 回归测试：确保源码以 UTF-8 编译，中文文案没有乱码。
 * （Windows 中文环境下若 Kotlin 编译器误用 GBK 读取源码，这里会立刻失败）
 */
class ChineseEncodingTest {

    private fun codes(text: String) = text.map { it.code }.joinToString(",")

    @Test
    fun chineseLiteralsAreIntact() {
        assertEquals("20840,33026,32431,29275,22902", codes("\u5168\u8102\u7eaf\u725b\u5976"))
        assertEquals("29275,22902", codes("\u725b\u5976"))
        assertEquals("20912,31665,20919,34255", codes(Locations.ALL[0]))
        assertEquals("20010", codes(Units.ALL[0]))
    }

    @Test
    fun voiceParsingWithChineseUnitAndLocation() {
        val draft = VoiceTextParser.parse("\u725b\u5976\u4e24\u74f6\u653e\u51b0\u7bb1\u51b7\u85cf\uff0c\u516d\u6708\u4e00\u53f7\u8fc7\u671f")
        requireNotNull(draft)
        assertEquals("\u725b\u5976", draft.name)
        assertEquals("\u74f6", draft.unit)
        assertEquals("\u51b0\u7bb1\u51b7\u85cf", draft.location)
    }
}