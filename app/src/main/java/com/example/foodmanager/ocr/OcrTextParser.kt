package com.example.foodmanager.ocr

import com.example.foodmanager.core.DateTextParser
import com.example.foodmanager.core.DateUtils
import com.example.foodmanager.core.FoodDraft
import com.example.foodmanager.core.FoodField
import com.example.foodmanager.core.NumberParser
import com.example.foodmanager.core.ShelfLifeParser
import com.example.foodmanager.data.Categories

/**
 * 把 OCR 识别出的整段文字解析成一条或多条食物草稿。
 *
 * 支持的信息：
 *  - 名称（商品名 / 食物关键词）
 *  - 数量 + 单位（2袋 / 500g / 1.5L）
 *  - 生产日期（生产日期、制造日期、灌装日期、MFG、PD）
 *  - 保质期（保质期12个月）与到期日（保质期至2026-01-01、EXP）
 *  - 存放位置（冷藏 / 冷冻 / 常温 等提示）
 */
object OcrTextParser {

    /** 食物关键词 -> 分类 */
    private val FOOD_KEYWORDS: Map<String, String> = linkedMapOf(
        // 乳制品
        "牛奶" to "乳制品", "纯牛奶" to "乳制品", "酸奶" to "乳制品", "奶酪" to "乳制品",
        "芝士" to "乳制品", "黄油" to "乳制品", "奶油" to "乳制品", "奶粉" to "乳制品",
        "乳酸菌" to "乳制品", "奶昔" to "乳制品",
        // 蛋类
        "鸡蛋" to "蛋类", "鸭蛋" to "蛋类", "鹌鹑蛋" to "蛋类", "咸蛋" to "蛋类", "皮蛋" to "蛋类", "蛋挞" to "蛋类",
        // 肉类
        "猪肉" to "肉类", "牛肉" to "肉类", "羊肉" to "肉类", "鸡肉" to "肉类", "鸭肉" to "肉类",
        "鸡翅" to "肉类", "鸡腿" to "肉类", "鸡胸" to "肉类", "排骨" to "肉类", "五花肉" to "肉类",
        "培根" to "肉类", "火腿" to "肉类", "香肠" to "肉类", "腊肉" to "肉类", "肉丸" to "肉类", "肉馅" to "肉类",
        "猪蹄" to "肉类", "猪肝" to "肉类", "牛腩" to "肉类", "牛排" to "肉类", "牛腱" to "肉类",
        "里脊" to "肉类", "前腿肉" to "肉类", "后腿肉" to "肉类", "猪排" to "肉类", "肉末" to "肉类",
        "鸡爪" to "肉类", "鸭腿" to "肉类", "鸭翅" to "肉类", "烤鸭" to "肉类", "烧鸡" to "肉类",
        "酱牛肉" to "熟食剩菜", "卤牛肉" to "熟食剩菜", "叉烧" to "熟食剩菜", "卤蛋" to "熟食剩菜",
        // 水产
        "鱼" to "水产", "虾" to "水产", "蟹" to "水产", "三文鱼" to "水产", "带鱼" to "水产",
        "鱿鱼" to "水产", "贝" to "水产", "蛤" to "水产", "海带" to "水产", "紫菜" to "水产",
        // 蔬菜
        "青菜" to "蔬菜", "白菜" to "蔬菜", "菠菜" to "蔬菜", "生菜" to "蔬菜", "西兰花" to "蔬菜",
        "西红柿" to "蔬菜", "番茄" to "蔬菜", "黄瓜" to "蔬菜", "土豆" to "蔬菜", "洋葱" to "蔬菜",
        "胡萝卜" to "蔬菜", "辣椒" to "蔬菜", "茄子" to "蔬菜", "豆角" to "蔬菜", "蘑菇" to "蔬菜",
        "香菇" to "蔬菜", "玉米" to "蔬菜", "南瓜" to "蔬菜", "芹菜" to "蔬菜", "韭菜" to "蔬菜", "豆腐" to "蔬菜",
        "油菜" to "蔬菜", "娃娃菜" to "蔬菜", "卷心菜" to "蔬菜", "包菜" to "蔬菜", "甘蓝" to "蔬菜",
        "空心菜" to "蔬菜", "茼蒿" to "蔬菜", "油麦菜" to "蔬菜", "上海青" to "蔬菜", "芥菜" to "蔬菜",
        "冬瓜" to "蔬菜", "丝瓜" to "蔬菜", "苦瓜" to "蔬菜", "黄瓜" to "蔬菜", "西葫芦" to "蔬菜",
        "莲藕" to "蔬菜", "藕" to "蔬菜", "山药" to "蔬菜", "芋头" to "蔬菜", "红薯" to "蔬菜",
        "紫薯" to "蔬菜", "土豆" to "蔬菜", "豌豆" to "蔬菜", "毛豆" to "蔬菜", "四季豆" to "蔬菜",
        "荷兰豆" to "蔬菜", "金针菇" to "蔬菜", "杏鲍菇" to "蔬菜", "平菇" to "蔬菜", "口蘑" to "蔬菜",
        "木耳菜" to "蔬菜", "秋葵" to "蔬菜", "芦笋" to "蔬菜", "莴笋" to "蔬菜", "茭白" to "蔬菜",
        "豆芽" to "蔬菜", "黄豆芽" to "蔬菜", "绿豆芽" to "蔬菜", "香菜" to "蔬菜", "小葱" to "蔬菜",
        "大葱" to "蔬菜", "生姜" to "蔬菜", "大蒜" to "蔬菜", "蒜苗" to "蔬菜", "蒜苔" to "蔬菜",
        "青椒" to "蔬菜", "彩椒" to "蔬菜", "尖椒" to "蔬菜", "小米椒" to "蔬菜", "杭椒" to "蔬菜",
        // 水果
        "苹果" to "水果", "香蕉" to "水果", "橙子" to "水果", "橘子" to "水果", "葡萄" to "水果",
        "草莓" to "水果", "蓝莓" to "水果", "西瓜" to "水果", "猕猴桃" to "水果", "梨" to "水果",
        "桃" to "水果", "柠檬" to "水果", "芒果" to "水果", "牛油果" to "水果", "樱桃" to "水果", "火龙果" to "水果",
        "石榴" to "水果", "柚子" to "水果", "柿子" to "水果", "菠萝" to "水果", "哈密瓜" to "水果",
        "木瓜" to "水果", "杨梅" to "水果", "枇杷" to "水果", "龙眼" to "水果", "荔枝" to "水果",
        "山竹" to "水果", "百香果" to "水果", "释迦" to "水果", "无花果" to "水果", "甘蔗" to "水果",
        "青提" to "水果", "红提" to "水果", "车厘子" to "水果", "西梅" to "水果", "莲雾" to "水果",
        "沙糖桔" to "水果", "砂糖橘" to "水果", "沃柑" to "水果", "丑橘" to "水果", "香梨" to "水果",
        "雪梨" to "水果", "皇冠梨" to "水果", "水蜜桃" to "水果", "油桃" to "水果", "蟠桃" to "水果",
        "冬枣" to "水果", "青枣" to "水果", "圣女果" to "水果", "小番茄" to "水果", "杨桃" to "水果",
        "榴莲" to "水果", "菠萝蜜" to "水果", "芭乐" to "水果", "番石榴" to "水果", "椰子" to "水果",
        "杏" to "水果", "李子" to "水果", "梅子" to "水果", "枣" to "水果", "枇杷果" to "水果",
        // 主食
        "面包" to "主食", "吐司" to "主食", "馒头" to "主食", "包子" to "主食", "米饭" to "主食",
        "大米" to "主食", "面条" to "主食", "挂面" to "主食", "饺子" to "主食", "馄饨" to "主食",
        "年糕" to "主食", "米粉" to "主食", "意面" to "主食", "面粉" to "主食", "燕麦" to "主食",
        // 零食
        "饼干" to "零食", "薯片" to "零食", "巧克力" to "零食", "糖果" to "零食", "蛋糕" to "零食",
        "坚果" to "零食", "瓜子" to "零食", "果冻" to "零食", "辣条" to "零食", "雪饼" to "零食",
        // 饮料
        "果汁" to "饮料", "可乐" to "饮料", "汽水" to "饮料", "咖啡" to "饮料", "茶" to "饮料",
        "啤酒" to "饮料", "红酒" to "饮料", "白酒" to "饮料", "矿泉水" to "饮料", "豆奶" to "饮料",
        "椰汁" to "饮料", "苏打水" to "饮料",
        // 调味品
        "酱油" to "调味品", "醋" to "调味品", "食用油" to "调味品", "橄榄油" to "调味品", "盐" to "调味品",
        "糖" to "调味品", "味精" to "调味品", "鸡精" to "调味品", "蚝油" to "调味品", "豆瓣酱" to "调味品",
        "番茄酱" to "调味品", "沙拉酱" to "调味品", "蜂蜜" to "调味品", "芝麻酱" to "调味品", "料酒" to "调味品",
        // 冷冻食品
        "汤圆" to "冷冻食品", "速冻" to "冷冻食品", "冰淇淋" to "冷冻食品", "雪糕" to "冷冻食品",
        "冷冻" to "冷冻食品", "冰棍" to "冷冻食品", "手抓饼" to "冷冻食品",
        // 熟食
        "剩菜" to "熟食剩菜", "熟食" to "熟食剩菜", "盒饭" to "熟食剩菜", "卤味" to "熟食剩菜", "酱牛肉" to "熟食剩菜",
        // 干货
        "木耳" to "干货", "红枣" to "干货", "枸杞" to "干货", "花生" to "干货", "黄豆" to "干货",
        "绿豆" to "干货", "银耳" to "干货", "粉丝" to "干货", "腐竹" to "干货",
        "核桃" to "干货", "腰果" to "干货", "杏仁" to "干货", "开心果" to "干货", "巴旦木" to "干货",
        "松子" to "干货", "榛子" to "干货", "板栗" to "干货", "莲子" to "干货", "桂圆" to "干货",
    )

    /** 存放位置关键词 */
    private val LOCATION_KEYWORDS: List<Pair<List<String>, String>> = listOf(
        listOf("冷冻", "速冻", "零下", "冰柜", "冷冻室") to "冰箱冷冻",
        listOf("变温", "软冷冻", "微冻") to "冰箱变温室",
        listOf("冷藏", "保鲜", "冰箱", "0-4", "0~4", "0～4") to "冰箱冷藏",
        listOf("酒柜", "恒温酒") to "酒柜",
        listOf("阴凉干燥", "常温", "室温", "避光", "通风") to "常温储物柜",
        listOf("阳台") to "阳台",
        listOf("厨房") to "厨房台面",
    )

    /** 需要忽略的商品信息行（注意：贮存条件里含有关键的存放位置信息，不要过滤） */
    private val NOISE_PATTERNS = listOf(
        Regex(
            "^净含量|^规格|^条形码|^执行标准|^产品标准|^配料|^营养成分|^食品添加剂|^生产许可|" +
                "^SC\\d|^QS|^客服|^电话|^地址|^厂家|^制造商|^委托方|^保质期[:：]?\\s*$|^食用方法|" +
                "^￥|^¥|^价格|^元/|^第\\d+页",
        ),
        // 免责声明 / 广告语：整行都是套话，绝不能当成品名
        // （实测「图案仅供参考,以实物为准」因中文字数多，打分反而高于真实品名而被误选）
        Regex(
            "仅供参考|以实物为准|图片与实物|图案与实物|实物为准|" +
                "时尚生活|美味相伴|伴你左右|尽享美味|美好生活|" +
                "本产品|最终解释权|请以实物|包装以实物|如有变更|恕不另行通知|" +
                "合格品|检验合格|QS认证|ISO\\d|HACCP",
        ),
        Regex("^[\\d\\s\\-/.]+$"),
        Regex("^[A-Za-z0-9\\s\\-_.:：/]{12,}$"),
    )

    /** 根据名称猜分类（供语音解析复用） */
    fun categoryOf(name: String): String = guessCategory(name)

    /** 取文本中出现的最长食物关键词，没有则返回 null（供语音解析复用） */
    fun longestFoodKeyword(text: String): String? = longestKeywordIn(text)?.first

    /** 是否命中了已知食物关键词 */
    fun isKnownFood(name: String): Boolean = longestKeywordIn(name) != null

    /** 全部食物关键词名称（供语音语法词表使用） */
    fun foodKeywordNames(extra: List<String> = emptyList()): List<String> =
        (FOOD_KEYWORDS.keys + extra.filter { it.isNotBlank() }).distinct()

    /** 当前解析使用的关键词表（含用户自定义常用食品） */
    private val activeKeywords = ThreadLocal.withInitial { FOOD_KEYWORDS }

    private fun currentKeywords(): Map<String, String> = activeKeywords.get() ?: FOOD_KEYWORDS

    /** 合并用户自定义常用食品后的关键词表（自定义的归入「其他」分类） */
    private fun keywordMap(extra: List<String>): Map<String, String> {
        if (extra.isEmpty()) return FOOD_KEYWORDS
        val merged = LinkedHashMap<String, String>()
        // 自定义词优先（更贴合用户自己的叫法）
        extra.filter { it.isNotBlank() }.forEach { merged[it] = "其他" }
        merged.putAll(FOOD_KEYWORDS)
        return merged
    }

    /**
     * 解析整段文字，返回按录入顺序排列的草稿列表。
     *
     * @param singleItemMode true = **整段文字当成一件商品**（默认，适合拍单件商品包装）；
     *   false = 按行拆分成多条（适合拍购物小票批量录入）。
     *   单件商品包装上的「脉动 / 减糖 / 蓝莓汁 1L」三行独立看都像商品名，
     *   按行拆会被切碎成 3 条 —— 这是默认值取 true 的原因。
     */
    fun parse(
        text: String,
        defaultSource: String = "ocr",
        extraKeywords: List<String> = emptyList(),
        singleItemMode: Boolean = false,
    ): List<FoodDraft> {
        val previous = activeKeywords.get()
        activeKeywords.set(keywordMap(extraKeywords))
        return try {
            val records = if (singleItemMode) listOf(text) else splitRecords(text)
            records.mapNotNull { parseRecord(it, defaultSource) }.filter { it.isUsable }
        } finally {
            activeKeywords.set(previous)
        }
    }

    /** 单条记录解析（也用于语音文本） */
    fun parseRecord(
        raw: String,
        defaultSource: String = "ocr",
        extraKeywords: List<String> = emptyList(),
    ): FoodDraft? {
        val previous = activeKeywords.get()
        activeKeywords.set(keywordMap(extraKeywords))
        return try {
            parseRecordInternal(raw, defaultSource)
        } finally {
            activeKeywords.set(previous)
        }
    }

    /** 单条记录解析（使用当前生效的关键词表） */
    fun parseRecord(raw: String, defaultSource: String = "ocr"): FoodDraft? =
        parseRecordInternal(raw, defaultSource)

    private fun parseRecordInternal(
        raw: String,
        defaultSource: String,
    ): FoodDraft? {
        var text = raw.trim()
        if (text.length < 1) return null

        val highlights = mutableListOf<String>()
        var confidence = 0.4f

        // ---------- 存放位置 ----------
        var location = ""
        for ((keywords, target) in LOCATION_KEYWORDS) {
            if (keywords.any { text.contains(it) }) {
                location = target
                highlights += FoodField.LOCATION
                confidence += 0.05f
                break
            }
        }

        // ---------- 日期 ----------
        var production = findDateAfterKeywords(text, PRODUCTION_KEYWORDS)
        var expiry = findDateAfterKeywords(text, EXPIRY_KEYWORDS)
        if (production != null) {
            highlights += FoodField.PRODUCTION
            confidence += 0.15f
        }
        if (expiry != null) {
            highlights += FoodField.EXPIRY
            confidence += 0.15f
        }

        // 保质期时长（12个月 / 30天）
        val shelfLife = ShelfLifeParser.parse(text)
        if (shelfLife != null && production != null && expiry == null) {
            expiry = DateUtils.plusDays(production, shelfLife.days)
            highlights += FoodField.EXPIRY
            confidence += 0.1f
        }

        // 生产日期 + 到期日 都没找到时，退化为「找所有日期」
        if (production == null && expiry == null) {
            val all = findAllDates(text)
            if (all.size >= 2) {
                production = all.first()
                expiry = all.last()
                highlights += FoodField.PRODUCTION
                highlights += FoodField.EXPIRY
                confidence += 0.1f
            } else if (all.size == 1) {
                val only = all.first()
                val keywordIsExpiry = EXPIRY_KEYWORDS.any { text.contains(it) }
                if (keywordIsExpiry) {
                    expiry = only
                    highlights += FoodField.EXPIRY
                } else {
                    // 只有一个日期时，跟随年限判断：如果加上常见保质期后在未来，就当作生产日期
                    production = only
                    if (shelfLife != null) {
                        expiry = DateUtils.plusDays(only, shelfLife.days)
                        highlights += FoodField.EXPIRY
                    }
                    highlights += FoodField.PRODUCTION
                }
                confidence += 0.05f
            }
        }

        // ---------- 数量 + 单位 ----------
        val (quantity, unit) = findQuantity(text)
        if (quantity != null) {
            highlights += FoodField.QUANTITY
        }
        if (unit != null) {
            highlights += FoodField.UNIT
        }

        // ---------- 名称 ----------
        val (name, category) = findName(text)
        if (name.isNotBlank()) {
            highlights += FoodField.NAME
            confidence += 0.2f
        }
        if (category.isNotBlank()) highlights += FoodField.CATEGORY

        val note = buildNote(text, name)

        if (name.isBlank() && expiry == null && production == null) return null

        return FoodDraft(
            name = name,
            category = category,
            quantity = quantity ?: 1.0,
            unit = unit ?: "个",
            productionDate = production,
            expiryDate = expiry,
            location = location,
            note = note,
            source = defaultSource,
            highlights = highlights.distinct(),
            confidence = confidence.coerceIn(0f, 1f),
        )
    }

    // ------------------------------------------------------------------
    // 名称 / 分类
    // ------------------------------------------------------------------

    internal fun findName(text: String): Pair<String, String> {
        val lines = text.split('\n', '|', '｜', ';', '；')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        // 1) 选出一行「最像品名」的文本
        var best: String = ""
        var bestScore = Int.MIN_VALUE
        for (line in lines) {
            if (isNoise(line)) continue
            val score = nameScore(line)
            if (score > bestScore) {
                bestScore = score
                best = line
            }
        }

        // 2) 如果这行里含有已知食物关键词，优先只取关键词，避免把口味/规格写进名字
        //    例如「全脂纯牛奶 1L」-> 牛奶 ；否则退化为整行清洗后的文本
        val keyword = longestKeywordIn(best)
        if (keyword != null) {
            val cleaned = cleanupName(best)
            if (cleaned.isNotBlank()) return cleaned to guessCategory(cleaned)
            return keyword.first to keyword.second
        }

        val cleaned = cleanupName(best)
        return cleaned to guessCategory(cleaned)
    }
    /** 找出文本中最长的食物关键词 */
    private fun longestKeywordIn(text: String): Pair<String, String>? {
        var best: Pair<String, String>? = null
        var bestLength = 0
        for ((kw, cat) in currentKeywords()) {
            if (text.contains(kw) && kw.length > bestLength) {
                best = kw to cat
                bestLength = kw.length
            }
        }
        return best
    }

    private fun nameScore(line: String): Int {
        var score = 0
        val chinese = line.count { it.code in 0x4E00..0x9FFF }
        score += chinese * 4
        score -= line.count { it.isDigit() } * 3
        if (line.contains(Regex("[¥￥$]|元|折|促销|特价|买一"))) score -= 25
        if (line.contains(Regex("[年月日]|保质|生产|到期|有效期|净含量|克|毫升|kg|ml|g\\b"))) score -= 12
        if (chinese in 2..8) score += 12
        if (chinese > 12) score -= 8
        // 命中已知食物关键词的行，几乎可以确定就是品名，给一个足够大的加权
        // （否则「图案仅供参考,以实物为准」这种中文字多的套话会赢过真正的「奶油味夹心饼干」）
        if (longestKeywordIn(line) != null) score += 40
        return score
    }

    private fun isNoise(line: String): Boolean =
        line.length < 2 || NOISE_PATTERNS.any { it.containsMatchIn(line) }

    private fun cleanupName(line: String): String {
        var s = line.trim()
        // 去掉可能拼接进来的字段与价格
        s = s.replace(Regex("^(品名|产品名称|商品名称|名称)[:：]?\\s*"), "")
        s = s.replace(Regex("[¥￥]\\s*\\d+(?:\\.\\d+)?"), " ")
        s = s.replace(Regex("\\d+(?:\\.\\d+)?\\s*(元|折|角|分)"), " ")
        s = s.replace(Regex("(生产日期|制造日期|灌装日期|生产于|保质期至|保质期到|保质期|有效期至|有效期|到期日?期?|净含量)[:：]?"), " ")
        s = s.replace(Regex("[(（].*?[)）]"), " ")
        // 去掉日期
        s = s.replace(Regex("\\d{4}\\s*[-/.年]\\s*\\d{1,2}\\s*[-/.月]\\s*\\d{1,2}\\s*日?"), " ")
        s = s.replace(Regex("(?<!\\d)20\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])(?!\\d)"), " ")
        s = s.replace(Regex("\\d{1,2}\\s*月\\s*\\d{1,2}\\s*[日号]"), " ")
        // 去掉数量 + 单位 / 裸数字
        s = s.replace(
            Regex("\\d+(?:\\.\\d+)?\\s*(袋|包|盒|箱|瓶|支|罐|听|桶|个|只|枚|颗|粒|根|条|头|斤|千克|公斤|kg|KG|克|g|毫升|ml|ML|升|L|把|捆|扎|块|份|片)"),
            " ",
        )
        s = s.replace(Regex("\\d+(?:\\.\\d+)?"), " ")
        s = s.replace(Regex("\\s+"), " ")
        // 取第一个有意义的片段（去掉空格分隔的口味/规格信息）
        val firstToken = s.split(' ').firstOrNull { it.isNotBlank() } ?: ""
        var result = firstToken.trim(' ', '-', '·', '*', ':', '：', '|', '｜', ',', '，', '.', '　')
        if (result.length > 16) result = result.take(16)
        return result
    }

    private fun guessCategory(name: String): String {
        if (name.isBlank()) return ""
        for ((kw, cat) in currentKeywords()) {
            if (name.contains(kw)) return cat
        }
        return Categories.ALL.firstOrNull { name.contains(it) } ?: ""
    }

    // ------------------------------------------------------------------
    // 日期
    // ------------------------------------------------------------------

    private val PRODUCTION_KEYWORDS = listOf(
        "生产日期", "生产曰期", "制造日期", "灌装日期", "加工日期", "包装日期", "出品日期", "生产于",
        "MFG", "MFD", "PROD", "PD",
    )

    private val EXPIRY_KEYWORDS = listOf(
        "保质期至", "保质期到", "有效期至", "有效期到", "有效期", "保存期至", "到期日", "到期日期",
        "此日期前食用", "此日期前最佳", "最佳食用日期", "请在...前食用", "限食用日期", "过期时间",
        "EXP", "EXPIRY", "BBE", "USE BY",
    )

    /** 关键词后紧跟的日期 */
    private fun findDateAfterKeywords(text: String, keywords: List<String>): Long? {
        for (kw in keywords) {
            var index = text.indexOf(kw, ignoreCase = true)
            while (index >= 0) {
                val start = (index + kw.length).coerceAtMost(text.length)
                val end = (start + 16).coerceAtMost(text.length)
                val window = text.substring(start, end)
                DateTextParser.findFirst(window)?.let { return it.date }
                DateTextParser.findCompact6(window)?.let { return it.date }
                index = text.indexOf(kw, index + 1, ignoreCase = true)
            }
        }
        return null
    }

    /** 文本中所有可识别的日期（去重、按出现顺序） */
    private fun findAllDates(text: String): List<Long> {
        val found = LinkedHashSet<Long>()
        // 逐段扫描，保证顺序
        var rest = text
        while (rest.isNotEmpty()) {
            val r = DateTextParser.findFirst(rest) ?: break
            found += r.date
            val next = rest.indexOf(r.matched)
            rest = if (next < 0) "" else rest.substring(next + r.matched.length)
        }
        if (found.isEmpty()) {
            DateTextParser.findCompact6(text)?.let { found += it.date }
        }
        return found.toList()
    }

    // ------------------------------------------------------------------
    // 数量 / 单位
    // ------------------------------------------------------------------

    private val UNIT_ALIASES: List<Pair<List<String>, String>> = listOf(
        listOf("袋", "包") to "袋",
        listOf("盒", "箱") to "盒",
        listOf("瓶", "支") to "瓶",
        listOf("罐", "听") to "罐",
        listOf("桶") to "桶",
        listOf("个", "只", "枚", "颗", "粒", "头") to "个",
        listOf("斤") to "斤",
        listOf("千克", "公斤", "kg", "KG", "Kg") to "千克",
        listOf("克", "g", "G", "gram") to "克",
        listOf("毫升", "ml", "ML", "mL") to "毫升",
        listOf("升", "L", "l") to "升",
        listOf("把", "捆", "扎") to "把",
        listOf("块") to "块",
        listOf("份") to "份",
    )

    private val QTY_REGEX = Regex(
        "(\\d+(?:\\.\\d+)?|[零〇一二两三四五六七八九十百]+)\\s*" +
            "(袋|包|盒|箱|瓶|支|罐|听|桶|个|只|枚|颗|粒|根|条|头|斤|千克|公斤|kg|KG|克|g|毫升|ml|ML|升|L|把|捆|扎|块|份|片)"
    )

    /** 返回 数量 to 单位（可为 null），兼容「净含量500g」这种没有数量的写法 */
    private fun findQuantity(text: String): Pair<Double?, String?> {
        QTY_REGEX.findAll(text).forEach { m ->
            val numberText = m.groupValues[1]
            val unitText = m.groupValues[2]
            val value = NumberParser.parse(numberText) ?: return@forEach
            // 排除「生产日期」里的数字误匹配（单位不同，基本不会命中）
            val unit = UNIT_ALIASES.firstOrNull { it.first.any { a -> a.equals(unitText, ignoreCase = true) } }?.second
                ?: unitText
            return@findQuantity value to unit
        }
        // 只有单位没有数量，例如「瓶装」
        for ((aliases, unit) in UNIT_ALIASES) {
            if (aliases.any { text.contains(it) }) return null to unit
        }
        return null to null
    }

    // ------------------------------------------------------------------
    // 其他
    // ------------------------------------------------------------------

    private fun buildNote(text: String, name: String): String {
        val lines = text.split('\n').map { it.trim() }.filter { it.isNotBlank() }
        val picked = lines
            .filter { it != name && !isNoise(it) }
            .filter { it.contains(Regex("品牌|产地|厂家|储存|贮存|食用|开封|冷藏|冷冻|常温|阴凉")) }
            .take(2)
        val note = picked.joinToString("；")
        return note.take(120)
    }

    /**
     * 把整段 OCR 文本切成多条记录。
     *
     * 需要同时处理两种版式：
     *  - 包装标签：一个商品占多行（名称 / 净含量 / 生产日期 / 保质期），应合并成一条
     *  - 小票或手写清单：一行一个商品，应拆成多条
     *
     * 判断依据：只有「当前记录已经攒齐名称和日期」且「新的一行自身就是一条独立商品」时才拆分。
     */
    private fun splitRecords(text: String): List<String> {
        val rawLines = text.split('\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (rawLines.isEmpty()) return emptyList()

        val records = mutableListOf<String>()
        var current = ""
        var currentHasDate = false
        var currentHasName = false

        fun flush() {
            if (current.isNotBlank()) records += current.trim()
            current = ""
            currentHasDate = false
            currentHasName = false
        }

        for (line in rawLines) {
            val info = LineInfo.of(line)

            if (info.isNoise) continue

            if (current.isNotBlank() && currentHasName && currentHasDate && info.standaloneProduct) {
                flush()
            }

            if (current.isNotBlank() && currentHasName && info.hasName &&
                !currentHasDate && !info.hasDate
            ) {
                flush()
            }

            current = if (current.isBlank()) line else "$current $line"
            currentHasDate = currentHasDate || info.hasDate
            currentHasName = currentHasName || info.hasName
        }
        flush()

        return records.ifEmpty { listOf(text) }
    }

    /** 单行信息类型判断 */
    private class LineInfo(
        val hasDate: Boolean,
        val hasName: Boolean,
        /** 这一行自身就像一条独立的商品记录（排除「生产日期」这类字段行） */
        val standaloneProduct: Boolean,
        val isNoise: Boolean,
    ) {
        companion object {
            private val PRICE_ONLY =
                Regex("[¥￥$]|\\d+(\\.\\d+)?\\s*(元|折|角|分)\\s*$|促销|特价|买一送一|扫码|关注")

            private val LABEL_ONLY = Regex(
                "保质期|有效期|生产日期|制造日期|灌装日期|加工日期|包装日期|到期|净含量|规格|储存|贮存|保存方法|食用方法|配料|营养成分|执行标准|产品标准"
            )

            private val UNIT_WORD = Regex(
                "(袋|包|盒|箱|瓶|支|罐|听|桶|个|只|枚|颗|粒|根|条|头|斤|千克|公斤|克|毫升|升|把|捆|扎|块|份|片|kg|KG|ml|ML|mL|L|g)"
            )

            fun of(line: String): LineInfo {
                val hasProductionKeyword = PRODUCTION_KEYWORDS.any { line.contains(it, ignoreCase = true) }
                val hasExpiryKeyword = EXPIRY_KEYWORDS.any { line.contains(it, ignoreCase = true) }
                val isLabelField = hasProductionKeyword || hasExpiryKeyword || LABEL_ONLY.containsMatchIn(line)

                val genericDate = DateTextParser.findFirst(line) != null ||
                    DateTextParser.findCompact6(line) != null

                val hasFoodKeyword = currentKeywords().keys.any { line.contains(it) }
                val hasUnitQty = QTY_REGEX.containsMatchIn(line) || UNIT_WORD.containsMatchIn(line)

                val isPriceOnly = PRICE_ONLY.containsMatchIn(line) && !hasFoodKeyword && !genericDate
                val isNoise = line.length < 2 ||
                    NOISE_PATTERNS.any { it.containsMatchIn(line) } ||
                    isPriceOnly

                return LineInfo(
                    hasDate = isLabelField || genericDate,
                    hasName = (hasFoodKeyword || (!isLabelField && hasUnitQty)) && !isPriceOnly,
                    standaloneProduct = !isLabelField && (hasFoodKeyword || hasUnitQty) && !isPriceOnly,
                    isNoise = isNoise,
                )
            }
        }
    }
}

/** 取关键词之后的部分（用于判断这一行是不是真正的日期行） */
private fun String.substringAfterAnyKeyword(keywords: List<String>): String {
    for (kw in keywords) {
        val index = indexOf(kw, ignoreCase = true)
        if (index >= 0) return substring((index + kw.length).coerceAtMost(length))
    }
    return ""
}

