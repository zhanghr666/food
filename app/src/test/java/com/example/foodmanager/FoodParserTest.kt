package com.example.foodmanager

import com.example.foodmanager.core.DateTextParser
import com.example.foodmanager.core.DateUtils
import com.example.foodmanager.core.NumberParser
import com.example.foodmanager.core.ShelfLifeParser
import com.example.foodmanager.ocr.OcrTextParser
import com.example.foodmanager.voice.VoicePhoneticCorrector
import com.example.foodmanager.voice.VoiceTextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 核心解析逻辑的单元测试（纯 JVM，不需要设备） */
class FoodParserTest {

    // ---------------- 数字 ----------------

    @Test
    fun parseChineseNumbers() {
        assertEquals(2.0, NumberParser.parse("两")!!, 0.001)
        assertEquals(3.0, NumberParser.parse("三")!!, 0.001)
        assertEquals(15.0, NumberParser.parse("十五")!!, 0.001)
        assertEquals(20.0, NumberParser.parse("二十")!!, 0.001)
        assertEquals(23.0, NumberParser.parse("二十三")!!, 0.001)
        assertEquals(0.5, NumberParser.parse("半")!!, 0.001)
        assertEquals(2.5, NumberParser.parse("2.5")!!, 0.001)
        assertEquals(100.0, NumberParser.parse("一百")!!, 0.001)
    }

    // ---------------- 日期 ----------------

    @Test
    fun parseFullDates() {
        assertEquals(DateUtils.of(2026, 3, 15), DateTextParser.findFirst("保质期至2026-03-15")!!.date)
        assertEquals(DateUtils.of(2026, 3, 15), DateTextParser.findFirst("2026/3/15")!!.date)
        assertEquals(DateUtils.of(2026, 3, 15), DateTextParser.findFirst("2026年3月15日")!!.date)
        assertEquals(DateUtils.of(2026, 3, 15), DateTextParser.findFirst("20260315")!!.date)
        assertEquals(DateUtils.of(2026, 3, 1), DateTextParser.findFirst("2026年3月")!!.date)
    }

    @Test
    fun parseCompactSixDigitDate() {
        val result = DateTextParser.findCompact6("生产日期 250601")
        assertNotNull(result)
        assertEquals(DateUtils.of(2025, 6, 1), result!!.date)
    }

    @Test
    fun parseChineseYear() {
        val result = DateTextParser.findFirst("二〇二六年一月八日")
        assertNotNull(result)
        assertEquals(DateUtils.of(2026, 1, 8), result!!.date)
    }

    @Test
    fun parseInvalidDateReturnsNull() {
        assertEquals(null, DateTextParser.findFirst("2026-13-45"))
    }

    // ---------------- 保质期 ----------------

    @Test
    fun parseShelfLife() {
        // 12 个月按 30 天/月 = 360 天
        assertEquals(360, ShelfLifeParser.parse("保质期12个月")!!.days)
        assertEquals(30, ShelfLifeParser.parse("保质期30天")!!.days)
        assertEquals(730, ShelfLifeParser.parse("保质期两年")!!.days)
        assertEquals(90, ShelfLifeParser.parse("保质期3个月")!!.days)
    }

    // ---------------- OCR 解析 ----------------

    @Test
    fun parseOcrLabel() {
        val text = """
            全脂纯牛奶
            净含量 250mL
            生产日期 2026-01-05
            保质期至 2026-07-05
            贮存条件：常温阴凉干燥处
        """.trimIndent()

        val drafts = OcrTextParser.parse(text)
        assertEquals(1, drafts.size)
        val draft = drafts.first()
        // 「全脂纯牛奶」没有直接命中关键词，退化为整行作为品名
        assertEquals("全脂纯牛奶", draft.name)
        assertEquals("乳制品", draft.category)
        assertEquals(DateUtils.of(2026, 1, 5), draft.productionDate)
        assertEquals(DateUtils.of(2026, 7, 5), draft.expiryDate)
        assertEquals("常温储物柜", draft.location)
    }

    @Test
    fun parseShelfLifeFromProduction() {
        val text = "酸牛奶\n生产日期：2026-01-01\n保质期 21天"
        val drafts = OcrTextParser.parse(text)
        assertTrue(drafts.isNotEmpty())
        val draft = drafts.first()
        assertEquals(DateUtils.of(2026, 1, 1), draft.productionDate)
        assertEquals(DateUtils.of(2026, 1, 22), draft.expiryDate)
    }

    @Test
    fun parseReceiptMultipleItems() {
        val text = """
            鸡蛋 2盒 2026-01-08
            猪肉 1斤
            西红柿 3个
        """.trimIndent()
        val drafts = OcrTextParser.parse(text)
        assertEquals(3, drafts.size)
        assertEquals("鸡蛋", drafts[0].name)
        assertEquals(2.0, drafts[0].quantity, 0.001)
        assertEquals("盒", drafts[0].unit)
        assertEquals("肉类", drafts[1].category)
        assertEquals("蔬菜", drafts[2].category)
    }

    // ---------------- 单件商品 vs 小票批量 ----------------

    /**
     * 回归：单件商品的包装文案按行拆会被切碎。
     * 实测截图里「脉动 / 减糖 / 蓝莓汁 1L 1瓶」被拆成了 3 条（减糖 / 蓝莓汁 / 治量）。
     */
    @Test
    fun ocrSingleItemModeKeepsOneRecordForPackagingText() {
        val text = """
            脉动
            减糖
            蓝莓汁 1L
            1瓶
        """.trimIndent()

        val single = OcrTextParser.parse(text, "ocr", singleItemMode = true)
        assertEquals("一张照片应只解析出一条记录", 1, single.size)

        // 同一段文字在"小票批量"模式下仍按行拆分（保持原有能力）
        val batch = OcrTextParser.parse(text, "ocr", singleItemMode = false)
        assertTrue("批量模式应能拆出多条", batch.size >= 2)
    }

    @Test
    fun ocrSingleItemModeStillWorksForRealLabel() {
        val text = """
            全脂纯牛奶
            净含量 250mL
            生产日期 2026-01-05
            保质期至 2026-07-05
        """.trimIndent()

        val drafts = OcrTextParser.parse(text, "ocr", singleItemMode = true)
        assertEquals(1, drafts.size)
        assertEquals(DateUtils.of(2026, 7, 5), drafts.first().expiryDate)
    }

    /**
     * 回归：免责声明/广告语被误当成品名。
     *
     * 实测截图（马卡龙夹心饼干）里，真正品名是「奶油味夹心饼干」，
     * 却选中了「图案仅供参考,以实物为准」—— 因为它中文字多、打分反而更高。
     */
    @Test
    fun ocrPrefersRealProductNameOverDisclaimer() {
        val text = """
            OCOCO
            Macacon
            Macaon Havoved Creamy
            马卡龙风味
            奶油味夹心饼干
            flavored sandwich
            cookies
            时尚生活伴随着美味
            FASHION LIFE ACCOMPANIED BY DELICIOUS
            图案仅供参考,以实物为准
            净含量:50克
        """.trimIndent()

        val drafts = OcrTextParser.parse(text, "ocr", singleItemMode = true)
        assertEquals(1, drafts.size)
        val name = drafts.first().name
        assertTrue(
            "品名不应是免责声明，实际得到：$name",
            !name.contains("仅供参考") && !name.contains("实物") && !name.contains("时尚生活"),
        )
        assertTrue("品名应命中真实商品（饼干），实际得到：$name", name.contains("饼干"))
        assertEquals(50.0, drafts.first().quantity, 0.001)
        assertEquals("克", drafts.first().unit)
    }

    // ---------------- 语音解析 ----------------

    @Test
    fun parseVoiceSentence() {
        val draft = VoiceTextParser.parse("牛奶两瓶放冰箱冷藏，六月一号过期")
        if (draft == null) {
            println("DEBUG parseVoiceSentence -> null")
            println("DEBUG name = " + VoiceTextParser.debugName("牛奶两瓶放冰箱冷藏，六月一号过期"))
            assertNotNull(draft)
        }
        draft!!
        assertEquals("牛奶", draft.name)
        assertEquals(2.0, draft.quantity, 0.001)
        assertEquals("瓶", draft.unit)
        assertEquals("冰箱冷藏", draft.location)
        assertEquals(6, DateUtils.month(draft.expiryDate!!))
        assertEquals(1, DateUtils.day(draft.expiryDate!!))
    }

    @Test
    fun parseVoiceRelativeDate() {
        val draft = VoiceTextParser.parse("鸡蛋两盒放冰箱冷藏，三周后过期")
        assertNotNull(draft)
        val expected = DateUtils.todayPlus(21)
        assertEquals(expected, draft!!.expiryDate)
    }

    @Test
    fun parseVoiceSimpleItem() {
        val draft = VoiceTextParser.parse("一袋大米放常温储物柜")
        assertNotNull(draft)
        assertEquals("大米", draft!!.name)
        assertEquals(1.0, draft.quantity, 0.001)
        assertEquals("袋", draft.unit)
        assertEquals("常温储物柜", draft.location)
    }

    /** 真机实测句式：「家里有三个苹果」应该识别出「苹果」而不是「家里有」 */
    @Test
    fun parseVoiceWithPossessionPhrase() {
        val draft = VoiceTextParser.parse("家里有三个苹果")
        assertNotNull(draft)
        assertEquals("苹果", draft!!.name)
        assertEquals(3.0, draft.quantity, 0.001)
        assertEquals("个", draft.unit)
        assertEquals("水果", draft.category)
    }

    @Test
    fun parseVoiceApplesInFridge() {
        val draft = VoiceTextParser.parse("家里有两个苹果在冰箱旁边")
        assertNotNull(draft)
        assertEquals("苹果", draft!!.name)
        assertEquals(2.0, draft.quantity, 0.001)
        assertEquals("冰箱冷藏", draft.location)
    }

    @Test
    fun parseVoiceMilkTwoBottles() {
        val draft = VoiceTextParser.parse("牛奶两瓶放冰箱冷藏，六月一号过期")
        assertNotNull(draft)
        assertEquals("牛奶", draft!!.name)
        assertEquals(2.0, draft.quantity, 0.001)
        assertEquals("瓶", draft.unit)
        assertEquals("冰箱冷藏", draft.location)
    }

    /** 真机实测句式：状态描述不能混进名称，多字食物关键词不能被单字截断 */
    @Test
    fun parseVoiceWithStatusPhrase() {
        val draft = VoiceTextParser.parse("家里有两个核桃还没有吃")
        assertNotNull(draft)
        assertEquals("核桃", draft!!.name)
        assertEquals(2.0, draft.quantity, 0.001)
    }

    /** 真机实测句式：一句话里同时有位置和相对日期，不能被拆成两条 */
    @Test
    fun parseBatchKeepsSingleSentenceWithRelativeDate() {
        val drafts = VoiceTextParser.parseBatch("家里有两个核桃在冰箱下面还有三天过期")
        assertEquals(1, drafts.size)
        val draft = drafts.first()
        assertEquals("核桃", draft.name)
        assertEquals(2.0, draft.quantity, 0.001)
        assertEquals("冰箱冷藏", draft.location)
        assertEquals(DateUtils.todayPlus(3), draft.expiryDate)
    }

    @Test
    fun parseBatchSplitsTwoRealItems() {
        val drafts = VoiceTextParser.parseBatch("牛奶两瓶放冰箱冷藏；鸡蛋两盒放冰箱冷藏")
        assertEquals(2, drafts.size)
        assertEquals("牛奶", drafts[0].name)
        assertEquals("鸡蛋", drafts[1].name)
    }

    /** 真机实测：离线识别把「西瓜」听成「吸管」，应按拼音近似纠正回食物名 */
    @Test
    fun parseVoiceCorrectsHomophoneFoodName() {
        val draft = VoiceTextParser.parse("家里有两个吸管还有一天过期")
        assertNotNull(draft)
        assertEquals("西瓜", draft!!.name)
        assertEquals(2.0, draft.quantity, 0.001)
        assertEquals(DateUtils.todayPlus(1), draft.expiryDate)
    }

    /** 真机实测整句：吸管→西瓜、厦门→下面、过其→过期 都要被修正 */
    @Test
    fun correctorFixesRealSentence() {
        val fixed = VoicePhoneticCorrector.correct("有 两个 的 吸管 在 冰箱 厦门 放着 还有 三天 过其")
        assertEquals("有 两个 的 西瓜 在 冰箱 下面 放着 还有 三天 过期", fixed)
    }

    @Test
    fun correctorKeepsCorrectWords() {
        val fixed = VoicePhoneticCorrector.correct("家里 有 两个 西瓜 在 冰箱 下面 还有 三天 过期")
        assertEquals("家里 有 两个 西瓜 在 冰箱 下面 还有 三天 过期", fixed)
    }

    @Test
    fun correctorFixesTimeWords() {
        assertEquals("过期", VoicePhoneticCorrector.correct("过去"))
        assertEquals("到期", VoicePhoneticCorrector.correct("到其"))
        assertEquals("保质期", VoicePhoneticCorrector.correct("保质其"))
    }

    /** 真机实测：「一箱牛奶三十盒」应取实际数量 30 盒，而不是包装数 1 箱 */
    @Test
    fun parseVoicePrefersActualCountOverPackage() {
        val draft = VoiceTextParser.parse("家里有一箱牛奶三十盒三十天过期")
        assertNotNull(draft)
        assertEquals("牛奶", draft!!.name)
        assertEquals(30.0, draft.quantity, 0.001)
        assertEquals("盒", draft.unit)
    }

    @Test
    fun parseVoiceSingleQuantityUnchanged() {
        val draft = VoiceTextParser.parse("家里有一箱牛奶")
        assertNotNull(draft)
        assertEquals("牛奶", draft!!.name)
        assertEquals(1.0, draft.quantity, 0.001)
        assertEquals("箱", draft.unit)
    }

    /** Vosk 输出带空格分词，数量/单位不能被名字里的字干扰 */
    @Test
    fun parseVoiceSpacedTokensFromAsr() {
        val draft = VoiceTextParser.parse("家里 有 一 箱 牛奶 还有 三十 盒 三十 天 过期")
        assertNotNull(draft)
        assertEquals("牛奶", draft!!.name)
        assertEquals(30.0, draft.quantity, 0.001)
        assertEquals("盒", draft.unit)
        assertEquals(DateUtils.todayPlus(30), draft.expiryDate)
    }

    @Test
    fun parseVoiceSpacedSinglePackage() {
        val draft = VoiceTextParser.parse("家里 有 一 箱 牛奶")
        assertNotNull(draft)
        assertEquals("牛奶", draft!!.name)
        assertEquals(1.0, draft.quantity, 0.001)
        assertEquals("箱", draft.unit)
    }

    /** 真机实测：「石榴」被识别成「十六」，应校正回水果名并保留数量 */
    @Test
    fun correctorFixesPomegranateHomophone() {
        val fixed = VoicePhoneticCorrector.correct("有 四 个 十六 放在 了 窗台 下面 还有 十 天 过期")
        assertEquals("有 四 个 石榴 放在 了 窗台 下面 还有 十 天 过期", fixed)
    }

    @Test
    fun parseVoicePomegranateSentence() {
        val draft = VoiceTextParser.parse("有 四 个 十六 放在 了 茶几 底下 还有 十 天 过期")
        assertNotNull(draft)
        assertEquals("石榴", draft!!.name)
        assertEquals(4.0, draft.quantity, 0.001)
        assertEquals("个", draft.unit)
        assertEquals(DateUtils.todayPlus(10), draft.expiryDate)
    }

    /** 用户自定义位置：配置后语音说它（含方位后缀）应能识别 */
    @Test
    fun parseVoiceWithCustomLocation() {
        val custom = listOf("茶几", "飘窗")
        val draft = VoiceTextParser.parse("有四个石榴在茶几底下还有十天过期", "voice", custom)
        assertNotNull(draft)
        assertEquals("石榴", draft!!.name)
        assertEquals(4.0, draft.quantity, 0.001)
        assertEquals("茶几", draft.location)
    }

    @Test
    fun parseVoiceCustomLocationWithSuffixVariants() {
        val custom = listOf("茶几")
        for (text in listOf(
            "苹果放在茶几上",
            "苹果放在茶几下面",
            "苹果放在茶几旁边",
            "苹果在茶几里面",
        )) {
            val draft = VoiceTextParser.parse(text, "voice", custom)
            assertNotNull("解析失败：$text", draft)
            assertEquals("自定义位置未命中：$text", "茶几", draft!!.location)
        }
    }

    @Test
    fun parseVoiceWithoutCustomLocationFallsBackToBuiltin() {
        val draft = VoiceTextParser.parse("有四个石榴在茶几底下还有十天过期", "voice", emptyList())
        assertNotNull(draft)
        assertEquals("厨房台面", draft!!.location)
    }

    /** 校正不能把位置词改掉（"茶几"曾被误改成"茶鸡精"） */
    @Test
    fun correctorKeepsLocationWords() {
        val fixed = VoicePhoneticCorrector.correct("有 四 个 石榴 在 茶几 底下 还有 十 天 过期")
        assertEquals("有 四 个 石榴 在 茶几 底下 还有 十 天 过期", fixed)
    }

    /** 位置必须能被识别出来（茶几底下 / 冰箱冷藏 / 窗台下面） */
    @Test
    fun parseVoiceRecognizesLocations() {
        val cases = mapOf(
            "有四个石榴在茶几底下还有十天过期" to "厨房台面",
            "牛奶两瓶放冰箱冷藏" to "冰箱冷藏",
            "一袋大米放常温储物柜" to "常温储物柜",
            "鸡蛋两盒放冰箱冷冻" to "冰箱冷冻",
            "苹果放窗台下面" to "常温储物柜",
        )
        for ((text, expected) in cases) {
            val draft = VoiceTextParser.parse(text)
            assertNotNull("解析失败：$text", draft)
            assertEquals("位置识别错误：$text", expected, draft!!.location)
        }
    }

    @Test
    fun parseVoicePomegranateCorrectlySpoken() {
        val draft = VoiceTextParser.parse("有四个石榴放在窗台下面还有十天过期")
        assertNotNull(draft)
        assertEquals("石榴", draft!!.name)
        assertEquals(4.0, draft.quantity, 0.001)
    }

    @Test
    fun correctorHandlesXiguanVariant() {
        assertEquals("西瓜", VoicePhoneticCorrector.correct("习惯"))
        assertEquals("西瓜", VoicePhoneticCorrector.correct("吸管"))
        assertEquals("下面", VoicePhoneticCorrector.correct("厦门"))
    }

    @Test
    fun parseVoiceKeepsCorrectFoodName() {
        val draft = VoiceTextParser.parse("家里有两个西瓜还有一天过期")
        assertNotNull(draft)
        assertEquals("西瓜", draft!!.name)
    }

    // ---------- 用户自定义常用食品（设置页维护） ----------

    @Test
    fun customFoodIsRecognizedInVoiceParsing() {
        // 「螺蛳粉」不在内置词表里，添加为常用食品后应能被当作名称识别
        val draft = VoiceTextParser.parse(
            "螺蛳粉两袋放在储物柜",
            customFoods = listOf("螺蛳粉"),
        )
        assertNotNull(draft)
        assertEquals("螺蛳粉", draft!!.name)
        assertEquals(2.0, draft.quantity, 0.001)
    }

    @Test
    fun customFoodExtendsCorrectorWithoutBreakingBuiltins() {
        // 自定义食品会扩充同音校正词表；内置的「吸管 -> 西瓜」规则必须仍然生效
        val custom = listOf("螺蛳粉")
        assertEquals("西瓜", VoicePhoneticCorrector.correct("吸管", custom))
        assertEquals("下面", VoicePhoneticCorrector.correct("厦门", custom))
        // 已经说对的名称不应被改动
        assertEquals("螺蛳粉", VoicePhoneticCorrector.correct("螺蛳粉", custom))
        // 与自定义食品音节完全不同的词不会被误替换（只做精确同音替换）
        assertEquals("酱油", VoicePhoneticCorrector.correct("酱油", custom))
    }

    @Test
    fun customFoodEntersVoiceGrammarWordList() {
        val grammar = com.example.foodmanager.voice.VoiceGrammar.build(
            extraLocations = listOf("茶几底下"),
            extraFoods = listOf("螺蛳粉"),
        )
        assertTrue("自定义食品未进入语法词表", grammar.contains("\"螺蛳粉\""))
        assertTrue("自定义位置未进入语法词表", grammar.contains("\"茶几底下\""))
    }

    @Test
    fun customFoodIsMatchedByOcrParser() {
        val drafts = OcrTextParser.parse(
            "螺蛳粉 净含量 300g 保质期 6个月",
            "ocr",
            listOf("螺蛳粉"),
        )
        assertTrue("自定义食品未被 OCR 解析识别", drafts.any { it.name.contains("螺蛳粉") })
    }

    @Test
    fun customFoodDoesNotBreakBuiltinFoods() {
        // 自定义食品不应影响内置食物识别
        val draft = VoiceTextParser.parse(
            "家里有两个西瓜还有一天过期",
            customFoods = listOf("螺蛳粉"),
        )
        assertNotNull(draft)
        assertEquals("西瓜", draft!!.name)
    }
}
