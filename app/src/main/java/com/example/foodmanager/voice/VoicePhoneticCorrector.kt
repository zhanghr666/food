package com.example.foodmanager.voice

/**
 * 离线语音识别结果的「同音字/近音词」校正。
 *
 * Vosk 小模型是通用中文模型，对食物名词、位置词等专有词容易输出同音字
 * （实测：西瓜→吸管/习惯、下面→厦门、过期→过其）。
 * 这里做两件事：
 *  1. 高频错误词的**定向修正表**（安全、可控）
 *  2. 拼音近音校正：只把**不在食物词表里**的词替换成**拼音高度相似**的已知食物
 *
 * 校正只作用于语音结果，OCR 识别到的文字不做改动。
 */
object VoicePhoneticCorrector {

    /** 高频错误 -> 正确（定向修正，优先级最高） */
    private val FIX_MAP: List<Pair<Regex, String>> = listOf(
        Regex("吸管|习惯|稀管|席管") to "西瓜",
        Regex("厦门|夏门|吓门|下楼") to "下面",
        Regex("过其|过起|过齐|过七|过去|过气") to "过期",
        Regex("冰霜|冰厢|宾箱") to "冰箱",
        Regex("窗台|窗抬|床台") to "窗台",
        Regex("牛奶|牛乃|流奶") to "牛奶",
        Regex("西红柿|西红柿") to "西红柿",
        Regex("鸡蛋|鸡旦|机蛋") to "鸡蛋",
        Regex("核桃|合桃|何桃") to "核桃",
        Regex("苹果|平果|瓶果") to "苹果",
        Regex("香蕉|香焦|相蕉") to "香蕉",
        Regex("葡萄|葡陶|普桃") to "葡萄",
        Regex("大米|大迷|大密") to "大米",
        Regex("土豆|土逗|图豆") to "土豆",
    )

    /** 时间/状态词校正 */
    private val TIME_FIX = listOf(
        Regex("过其|过起|过齐|过七|过去|过气") to "过期",
        Regex("到其|到起|到齐") to "到期",
        Regex("保质其|保质起|保制期") to "保质期",
        Regex("有效其|有效起") to "有效期",
    )

    /** 位置词校正（让「存放位置」更准） */
    private val LOCATION_FIX = listOf(
        Regex("厦门|夏门|吓门") to "下面",
        Regex("冷臧|冷藏|冷藏") to "冷藏",
        Regex("冷冻|冷冬|冷洞") to "冷冻",
        Regex("常溫|常温|肠温") to "常温",
    )

    /** 已知食物词表（用于近音替换） */
    private val FOOD_LEXICON = listOf(
        "西瓜", "核桃", "苹果", "牛奶", "酸奶", "鸡蛋", "鸭蛋", "猪肉", "牛肉", "羊肉", "鸡肉",
        "大米", "面粉", "面包", "馒头", "面条", "香蕉", "橙子", "橘子", "梨", "葡萄", "草莓",
        "蓝莓", "柠檬", "芒果", "樱桃", "火龙果", "柚子", "猕猴桃", "青菜", "白菜",
        "菠菜", "生菜", "西兰花", "黄瓜", "土豆", "洋葱", "胡萝卜", "辣椒", "茄子", "蘑菇",
        "香菇", "玉米", "南瓜", "芹菜", "韭菜", "豆腐", "西红柿", "番茄", "鱼", "虾", "螃蟹",
        "带鱼", "紫菜", "海带", "汤圆", "饺子", "饼干", "巧克力", "糖果", "果汁", "可乐",
        "咖啡", "茶叶", "啤酒", "红酒", "酱油", "食用油", "蜂蜜", "花生", "红枣", "枸杞",
        "木耳", "银耳", "绿豆", "黄豆", "年糕", "矿泉水", "豆奶", "速冻", "冰淇淋",
        "雪糕", "剩菜", "熟食", "腐竹", "粉丝", "腰果", "杏仁", "开心果", "板栗", "莲子",
        // 常见水果（离线识别最容易出同音字的）
        "石榴", "柿子", "菠萝", "哈密瓜", "木瓜", "杨梅", "枇杷", "龙眼", "荔枝", "山竹",
        "百香果", "无花果", "甘蔗", "青提", "红提", "车厘子", "西梅", "莲雾", "砂糖橘",
        "沃柑", "丑橘", "香梨", "雪梨", "水蜜桃", "油桃", "冬枣", "圣女果", "杨桃", "榴莲",
        "菠萝蜜", "芭乐", "番石榴", "椰子", "李子", "梅子", "蓝莓", "桃",
        // 常见蔬菜
        "油菜", "娃娃菜", "卷心菜", "包菜", "甘蓝", "空心菜", "茼蒿", "油麦菜", "芥菜",
        "冬瓜", "丝瓜", "苦瓜", "西葫芦", "莲藕", "山药", "芋头", "红薯", "紫薯",
        "豌豆", "毛豆", "四季豆", "荷兰豆", "金针菇", "杏鲍菇", "平菇", "口蘑",
        "秋葵", "芦笋", "莴笋", "茭白", "豆芽", "香菜", "小葱", "大葱", "生姜", "大蒜",
        "蒜苗", "蒜苔", "青椒", "彩椒", "尖椒", "小米椒",
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
        // 1) 定向修正表
        for ((pattern, replacement) in TIME_FIX) {
            result = pattern.replace(result, replacement)
        }
        for ((pattern, replacement) in FIX_MAP) {
            result = pattern.replace(result, replacement)
        }
        for ((pattern, replacement) in LOCATION_FIX) {
            result = pattern.replace(result, replacement)
        }
        // 2) 食物词近音校正：在整句里滑动窗口找食物名（不依赖空格分词）
        val lexicon = if (extraFoods.isEmpty()) FOOD_LEXICON else (FOOD_LEXICON + extraFoods).distinct()
        val fixed = correctFoodNamesInSentence(result, lexicon)
        return fixed
    }

    /**
     * 在整句里找出"听起来像某个食物"的片段并替换成规范食物名。
     *
     * 不依赖空格分词（Vosk 有时输出不带空格，如「有四个十六放在了窗台下面」），
     * 而是用 2~5 字的滑动窗口逐窗比对，取相似度最高且唯一的替换。
     */
    private fun correctFoodNamesInSentence(text: String, lexicon: List<String>): String {
        if (text.isBlank()) return text
        var result = text
        var guard = 0
        while (guard++ < MAX_REPLACEMENTS) {
            var bestFood: String? = null
            var bestStart = -1
            var bestLen = 0
            var bestScore = 0.0
            var bestCount = 0

            for (win in 2..5) {
                if (result.length < win) break
                for (start in 0..(result.length - win)) {
                    val token = result.substring(start, start + win)
                    if (token.any { it.isDigit() }) continue
                    val food = matchFood(token, lexicon) ?: continue
                    val score = similarity(syllablesOf(token), syllablesOf(food))
                    when {
                        score > bestScore -> {
                            bestScore = score
                            bestFood = food
                            bestStart = start
                            bestLen = win
                            bestCount = 1
                        }

                        score == bestScore && food == bestFood -> bestCount++
                    }
                }
            }
            // 要求足够像、且该片段只出现一次，避免误伤
            if (bestFood == null || bestScore < 0.8 || bestCount != 1) break
            result = result.substring(0, bestStart) + bestFood + result.substring(bestStart + bestLen)
        }
        return result
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

    /** 按音节比较：完全相同 1 分，同首字母（近似声母）0.5 分 */
    private fun similarity(a: List<String>, b: List<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val total = maxOf(a.size, b.size).toDouble()
        var score = 0.0
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val sa = a[i]
            val sb = b[i]
            score += when {
                sa == sb -> 1.0
                sa.firstOrNull() == sb.firstOrNull() -> 0.5
                else -> 0.0
            }
        }
        return score / total
    }

    /** 逐字转拼音音节列表 */
    private fun syllablesOf(text: String): List<String> =
        text.map { ch -> PINYIN[ch] ?: ch.toString() }

    /** 一句话里最多做多少次食物名替换，防止死循环 */
    private const val MAX_REPLACEMENTS = 6
}
