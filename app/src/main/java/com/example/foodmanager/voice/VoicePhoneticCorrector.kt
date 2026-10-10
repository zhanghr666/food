package com.example.foodmanager.voice

/** 限定上下文纠错；歧义食品只给建议，保留原始文字供核对。 */
object VoicePhoneticCorrector {

    /** 时间/状态词校正 */
    private val TIME_FIX = listOf(
        Regex("过其|过起|过齐|过七|过去|过气") to "过期",
        Regex("到其|到起|到齐") to "到期",
        Regex("保质其|保质起|保制期") to "保质期",
        Regex("有效其|有效起") to "有效期",
    )

    /** 逐字拼音（覆盖食物/位置/时间常用字） */
    private val PINYIN: Map<Char, String> = buildMap {
        fun add(py: String, vararg chars: Char) = chars.forEach { put(it, py) }
        add("xi", '西', '吸', '希', '稀', '夕', '席', '洗', '喜', '习', '息')
        add("gua", '瓜', '刮', '挂')
        add("guan", '管', '惯', '观', '关', '馆', '罐', '灌')
        add("he", '核', '合', '何', '河', '盒', '喝', '和')
        add("tao", '桃', '萄', '陶', '淘', '逃')
        add("ping", '苹', '平', '瓶', '评', '屏')
        add("guo", '果', '过', '国', '锅')
        add("qi", '其', '期', '起', '七', '齐', '气', '器', '骑', '旗')
        add("niu", '牛', '扭')
        add("nai", '奶', '乃', '耐')
        add("ji", '鸡', '机', '几', '记', '级', '即', '集', '急')
        add("dan", '蛋', '旦', '但', '单', '淡')
        add("zhu", '猪', '主', '竹', '住', '注')
        add("rou", '肉', '柔')
        add("yang", '羊', '洋', '阳', '样', '养')
        add("yu", '鱼', '玉', '雨', '于', '与', '语')
        add("xia", '虾', '下', '夏', '吓')
        add("cai", '菜', '才', '采', '财')
        add("qing", '青', '清', '轻', '请', '情')
        add("bai", '白', '百', '摆')
        add("bo", '菠', '波', '播', '拨')
        add("cai", '菜')
        add("sheng", '生', '声', '升', '省')
        add("huang", '黄', '皇')
        add("tu", '土', '图', '兔', '吐')
        add("dou", '豆', '都', '斗', '逗')
        add("cong", '葱', '从', '聪')
        add("suan", '酸', '蒜', '算')
        add("jiang", '姜', '酱', '将', '江')
        add("qie", '茄', '切', '且')
        add("jiao", '椒', '蕉', '交', '教', '脚', '角', '饺')
        add("mo", '蘑', '磨', '摸', '墨')
        add("gu", '菇', '姑', '古', '谷', '骨')
        add("nan", '南', '男', '难')
        add("qin", '芹', '亲', '琴')
        add("jiu", '韭', '九', '酒', '久', '旧')
        add("fu", '腐', '服', '福', '父', '付')
        add("mian", '面', '棉', '免')
        add("bao", '包', '宝', '抱', '报', '饱')
        add("tou", '头', '投', '偷')
        add("tiao", '条', '跳', '调')
        add("mai", '麦', '买', '卖', '脉')
        add("pian", '片', '偏', '骗')
        add("bing", '饼', '冰', '病', '并', '兵')
        add("gan", '干', '甘', '肝', '敢', '感')
        add("zhe", '蔗', '哲', '遮', '折')
        add("qiao", '巧', '桥', '敲', '悄')
        add("ke", '克', '可', '客', '课', '刻')
        add("li", '力', '李', '里', '离', '梨', '礼')
        add("tang", '糖', '汤', '堂', '躺')
        add("zhi", '汁', '只', '知', '直', '纸', '指')
        add("le", '乐', '了')
        add("ka", '咖', '卡', '咖')
        add("fei", '啡', '飞', '非', '肥')
        add("cha", '茶', '查', '差', '插')
        add("pi", '啤', '皮', '批', '疲')
        add("jiu", '酒')
        add("shi", '是', '事', '时', '十', '实', '食', '拾', '石')
        add("si", '四', '死', '思', '丝', '司')
        add("wu", '五', '无', '午', '物', '武')
        add("qi", '七', '其', '期', '起', '齐', '气', '器', '骑', '旗')
        add("ba", '八', '把', '爸', '吧', '巴')
        add("jiu", '九', '酒', '久', '旧', '韭')
        add("ge", '个', '各', '格', '歌')
        add("liang", '两', '量', '亮', '凉')
        add("ban", '半', '班', '搬')
        add("bai", '百', '白', '摆')
        add("qian", '千', '前', '钱')
        add("wan", '万', '晚', '完', '碗')
        add("liu", '六', '流', '留', '榴')
        add("luo", '萝', '罗', '落', '络')
        add("bo", '菠', '波', '播', '拨', '萝')
        add("you", '油', '有', '又', '右', '由', '游', '柚', '鱿')
        add("yan", '盐', '言', '眼', '严', '烟')
        add("cu", '醋', '促', '粗')
        add("feng", '蜂', '风', '封', '峰', '丰')
        add("mi", '蜜', '米', '迷', '密')
        add("hua", '花', '话', '画', '华')
        add("hong", '红', '洪', '轰')
        add("zao", '枣', '早', '造', '遭')
        add("gou", '枸', '狗', '够', '沟')
        add("mu", '木', '母', '目', '幕')
        add("er", '耳', '二', '儿', '而')
        add("yin", '银', '因', '音', '引', '印')
        add("lv", '绿', '旅', '律')
        add("fen", '粉', '分', '份', '奋')
        add("si", '丝', '四', '死', '思')
        add("nian", '年', '念', '粘')
        add("gao", '糕', '高', '告')
        add("xiang", '香', '想', '相', '箱', '乡', '响')
        add("jiao", '蕉')
        add("cheng", '橙', '成', '城', '程', '称')
        add("ju", '橘', '局', '举', '句')
        add("zi", '子', '字', '自', '紫')
        add("pu", '葡', '普', '铺', '扑')
        add("cao", '草', '操')
        add("mei", '莓', '没', '每', '美', '煤')
        add("lan", '蓝', '篮', '懒', '烂')
        add("ning", '柠', '宁', '凝')
        add("meng", '檬', '梦', '猛', '蒙')
        add("mang", '芒', '忙', '盲')
        add("ying", '樱', '应', '英', '影', '赢')
        add("huo", '火', '活', '货', '获')
        add("long", '龙', '笼', '拢')
        add("hou", '猴', '后', '厚', '候')
        add("zhu", '竹')
        add("shi", '西', '是', '事', '时', '十', '实', '食')
        add("liang", '两', '量', '亮', '凉')
        add("ge", '个', '各', '格', '歌')
        add("tian", '天', '田', '填', '甜')
        add("zhou", '周', '州', '轴')
        add("yue", '月', '越', '约')
        add("ri", '日')
        add("xiao", '小', '笑', '校', '消')
        add("shao", '少', '烧', '稍')
        add("duo", '多', '朵', '躲')
        add("kuai", '快', '块', '筷')
        add("man", '慢', '满', '馒')
        add("zao", '早')
        add("wan", '晚', '完', '碗', '万')
        add("wu", '五', '无', '午', '物')
        add("liu", '六', '流', '留')
        add("ba", '八', '把', '爸', '吧')
        add("san", '三', '伞', '散', '叁')
        add("shi", '十', '拾')
        add("er", '二')
        add("yi", '一', '衣', '医', '已')
    }

    /**
     * 校正一段识别文字（实时框与最终提交都走这里）。
     * 只做词级替换，不改动语序。
     */
    fun correct(text: String, extraFoods: List<String> = emptyList()): String {
        if (text.isBlank()) return text
        var result = text
        // 真实常用词（过去、吸管、习惯、厦门）不作全局替换。用户词优先保留。
        for ((pattern, replacement) in TIME_FIX) {
            result = pattern.replace(result) { m ->
                val left = result.substring(0, m.range.first).takeLast(18)
                if (extraFoods.any { it.contains(m.value) } || m.value in setOf("过去", "过气")) m.value
                else if (replacement == "保质期" || replacement == "有效期" ||
                    Regex("(?:天|日|号|月|周|星期|今天|明天|后天)\\s*(?:后|之后)?\\s*$").containsMatchIn(left)) replacement
                else m.value
            }
        }
        result = Regex("(冰箱|柜子|桌子|窗台)\\s*(厦门|夏门|吓门)").replace(result) { m ->
            if (extraFoods.any { it.contains(m.value.replace(" ", "")) }) m.value
            else m.value.replace(m.groupValues[2], "下面")
        }
        return result
    }

    /** 歧义词只提示候选，用户确认前不把原文改成食品名。 */
    data class NameSuggestion(val heard: String, val candidate: String, val suggestedQuantity: Double? = null, val suggestedUnit: String? = null)

    fun suggestions(text: String, extraFoods: List<String> = emptyList()): List<String> =
        nameSuggestions(text, extraFoods).map { "名称待确认：原文“${it.heard}”，可能是“${it.candidate}”" }

    fun nameSuggestions(text: String, extraFoods: List<String> = emptyList()): List<NameSuggestion> {
        val lexicon = com.example.foodmanager.ocr.OcrTextParser.foodKeywordNames(extraFoods)
        val compact = text.replace(Regex("(?<=[\\p{IsHan}0-9]) +(?=[\\p{IsHan}0-9])"), "")
        val count = "[0-9零〇一二两三四五六七八九十百]+"
        val unit = "个|只|颗|瓶|盒|袋|根|条|份|把|包|箱|块|斤"
        val ending = "在|放|还有|[\\s，,。；;]|$"
        val slot = Regex("($count)($unit)(月)?(?:人)?(?:的)?([\\p{IsHan}]{2,5}?)(?=$ending)")
        val leading = slot.findAll(compact).mapNotNull { m ->
            val word = m.groupValues[4]
            val damagedQuantity = m.groupValues[3].isNotBlank()
            val prefix = compact.substring(0, m.range.first).substringAfterLast('，').substringAfterLast(',')
            // 真正的时长描述不推成库存数量；多出的“月”只在数量开头、同音食品槽里给候选。
            val quantityContext = Regex("^(?:家里|家中)?(?:有|买了|买的|买入|购入|库存[:：是为有]*|数量[:：是为有]*)?$").matches(prefix)
            if (damagedQuantity && (m.groupValues[2] != "个" || !quantityContext)) return@mapNotNull null
            if (word in extraFoods || word in lexicon) null else {
                val candidate = if (word in listOf("吸管", "习惯", "稀管", "席管")) "西瓜" else matchFood(word, lexicon)
                candidate?.let { NameSuggestion(word, it,
                    suggestedQuantity = if (damagedQuantity) com.example.foodmanager.core.NumberParser.parse(m.groupValues[1]) else null,
                    suggestedUnit = if (damagedQuantity) "个" else null) }
            }
        }.toList()
        // 名称在前、数量在后与无数量的地点句也使用相同的唯一同音候选逻辑。
        val trailing = Regex("(?:^|[，,。；;])(?:家里|家中)?(?:有|买了|买的|记录)?([\\p{IsHan}]{2,12}?)($count)($unit)(?:人)?(?=$ending)")
        val beforePlace = Regex("(?:^|[，,。；;])(?:家里|家中)?(?:有|买了|买的|记录)?([\\p{IsHan}]{2,12}?)(?=放在|放到|在)")
        val additional = (trailing.findAll(compact) + beforePlace.findAll(compact)).mapNotNull { m ->
            val word = m.groupValues[1]
            if (word in lexicon || word in extraFoods) null else matchFood(word, lexicon)?.let { NameSuggestion(word, it) }
        }.toList()
        return (leading + additional).distinct()
    }

    /** 纯比对逻辑：token 听起来是不是某个食物词（不做任何替换） */
    private fun matchFood(token: String, lexicon: List<String>): String? {
        val clean = token.replace(Regex("[\\u3000\\s，。、；：！？]"), "")
        if (clean.length < 2) return null
        // 已命中食物词就不动
        if (lexicon.any { it == clean || clean.contains(it) }) return null
        // 非食物词（位置/桌面/称谓/常用词）不参与替换，避免"茶几"→"茶鸡精"这种误伤
        if (NON_FOOD_WORDS.any { clean.contains(it) }) return null
        val target = syllablesOf(clean)
        if (target.size < 2) return null

        // 只接受「音节序列完全一致」的食物名 —— 即真正的同音词（十六 → 石榴）
        val exact = lexicon.filter { syllablesOf(it) == target }
        if (exact.size != 1) return null
        return exact.first()
    }

    /** 非食物词表：出现这些词时不做食物名替换 */
    private val NON_FOOD_WORDS = listOf(
        // 位置 / 家具
        "茶几", "茶几下", "桌子", "桌下", "椅子", "架子", "货架", "置物架", "抽屉", "柜子", "橱柜",
        "窗台", "窗下", "窗边", "飘窗", "床下", "沙发", "地上", "地面", "台上", "台面", "灶台",
        "冰箱", "冷藏", "冷冻", "冷冻室", "变温室", "酒柜", "阳台", "厨房", "碗柜", "层架",
        "底下", "下面", "上面", "里面", "外面", "旁边", "附近", "上方", "下方",
        // 时间 / 状态 / 口语
        "还有", "过期", "到期", "保质", "生产", "今天", "明天", "后天", "昨天", "前天",
        "下周", "下周", "下月", "这周", "本月", "早上", "晚上", "中午", "小时", "分钟",
        "放在", "放了", "买了", "记得", "一共", "总共", "大概", "大约", "有点", "多少",
        // 常用名词（避免被当成食物同音词）
        "东西", "物品", "食品", "零食", "饮料", "家里", "家里有", "房间", "客厅", "卧室",
    )

    /** 逐字转拼音音节列表 */
    private fun syllablesOf(text: String): List<String> =
        text.map { ch -> PINYIN[ch] ?: ch.toString() }

}
