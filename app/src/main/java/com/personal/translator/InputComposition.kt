package com.personal.translator

object InputComposition {
    private val pinyin = mapOf(
        "di" to listOf("地","第","低","底","滴","帝","敌"), "tie" to listOf("铁","贴"), "a" to listOf("啊", "阿"), "ai" to listOf("爱", "矮", "哎"), "an" to listOf("安", "按"),
        "ba" to listOf("吧", "八", "把"), "bu" to listOf("不", "步"), "chi" to listOf("吃"),
        "dian" to listOf("点", "店", "电"), "duo" to listOf("多"), "fan" to listOf("饭", "翻"),
        "hao" to listOf("好", "号", "豪"), "he" to listOf("和", "喝"), "hen" to listOf("很"),
        "jia" to listOf("家", "加"), "jian" to listOf("见", "间"), "jiao" to listOf("叫", "教"),
        "jin" to listOf("今", "进"), "jiu" to listOf("就", "九"), "kan" to listOf("看"),
        "ke" to listOf("可", "客"), "lai" to listOf("来"), "le" to listOf("了"), "li" to listOf("里", "离"),
        "ma" to listOf("吗", "妈"), "mei" to listOf("没", "美"), "ming" to listOf("明"),
        "na" to listOf("哪", "那"), "ni" to listOf("你", "呢", "尼"), "nv" to listOf("女"),
        "qing" to listOf("请", "情"), "qu" to listOf("去"), "ren" to listOf("人"), "shi" to listOf("是", "时"),
        "shui" to listOf("水", "谁"), "shuo" to listOf("说"), "tian" to listOf("天"),
        "wo" to listOf("我", "我们"), "xi" to listOf("洗", "西"), "xian" to listOf("先"),
        "xie" to listOf("谢", "些"), "yao" to listOf("要", "药"), "yi" to listOf("一", "以"),
        "you" to listOf("有", "又"), "zai" to listOf("在", "再"), "zhe" to listOf("这"),
        "zhong" to listOf("中", "种"), "zhu" to listOf("住", "主"),
        "nihao" to listOf("你好", "您好"), "zaijian" to listOf("再见"), "xiexie" to listOf("谢谢"),
        "qingwen" to listOf("请问"), "buhao" to listOf("不好"),
        "zaizheli" to listOf("在这里"), "wohao" to listOf("我好"), "women" to listOf("我们"),
        "nimen" to listOf("你们"), "zhege" to listOf("这个"), "nage" to listOf("那个"),
        "nali" to listOf("哪里", "那里"), "zenme" to listOf("怎么"), "shenme" to listOf("什么"),
        "duoshao" to listOf("多少"), "duojiu" to listOf("多久"), "canting" to listOf("餐厅"),
        "fujin" to listOf("附近"), "cesuo" to listOf("厕所"), "gongjiao" to listOf("公交"),
        "ditie" to listOf("地铁"), "yinhang" to listOf("银行"), "jichang" to listOf("机场"),
        "jiudian" to listOf("酒店"), "fuwuyuan" to listOf("服务员"), "maidan" to listOf("买单"),
        "keyi" to listOf("可以"), "bukeyi" to listOf("不可以"),
        "bangmang" to listOf("帮忙"), "zhaodao" to listOf("找到"), "woxiang" to listOf("我想"),
        "woyao" to listOf("我要"), "wozai" to listOf("我在"), "woxiangyao" to listOf("我想要"),
        "tingbudong" to listOf("听不懂"), "manyidian" to listOf("慢一点")
    )
    fun chineseCandidates(value: String): List<String> {
        val key = value.lowercase().replace(" ", "")
        return pinyin[key].orEmpty()
    }

    private val romaji = mapOf(
        "a" to "あ", "i" to "い", "u" to "う", "e" to "え", "o" to "お",
        "ka" to "か", "ki" to "き", "ku" to "く", "ke" to "け", "ko" to "こ",
        "ga" to "が", "gi" to "ぎ", "gu" to "ぐ", "ge" to "げ", "go" to "ご",
        "sa" to "さ", "shi" to "し", "si" to "し", "su" to "す", "se" to "せ", "so" to "そ",
        "za" to "ざ", "ji" to "じ", "zi" to "じ", "zu" to "ず", "ze" to "ぜ", "zo" to "ぞ",
        "ta" to "た", "chi" to "ち", "ti" to "ち", "tsu" to "つ", "tu" to "つ", "te" to "て", "to" to "と",
        "da" to "だ", "di" to "ぢ", "du" to "づ", "de" to "で", "do" to "ど",
        "na" to "な", "ni" to "に", "nu" to "ぬ", "ne" to "ね", "no" to "の",
        "ha" to "は", "hi" to "ひ", "fu" to "ふ", "hu" to "ふ", "he" to "へ", "ho" to "ほ",
        "ba" to "ば", "bi" to "び", "bu" to "ぶ", "be" to "べ", "bo" to "ぼ",
        "pa" to "ぱ", "pi" to "ぴ", "pu" to "ぷ", "pe" to "ぺ", "po" to "ぽ",
        "ma" to "ま", "mi" to "み", "mu" to "む", "me" to "め", "mo" to "も",
        "ya" to "や", "yu" to "ゆ", "yo" to "よ", "ra" to "ら", "ri" to "り", "ru" to "る", "re" to "れ", "ro" to "ろ",
        "wa" to "わ", "wo" to "を", "kya" to "きゃ", "kyu" to "きゅ", "kyo" to "きょ",
        "sha" to "しゃ", "shu" to "しゅ", "sho" to "しょ", "cha" to "ちゃ", "chu" to "ちゅ", "cho" to "ちょ",
        "nya" to "にゃ", "nyu" to "にゅ", "nyo" to "にょ", "rya" to "りゃ", "ryu" to "りゅ", "ryo" to "りょ",
        "mya" to "みゃ", "myu" to "みゅ", "myo" to "みょ", "hya" to "ひゃ", "hyu" to "ひゅ", "hyo" to "ひょ",
        "gya" to "ぎゃ", "gyu" to "ぎゅ", "gyo" to "ぎょ", "ja" to "じゃ", "ju" to "じゅ", "jo" to "じょ",
        "bya" to "びゃ", "byu" to "びゅ", "byo" to "びょ", "pya" to "ぴゃ", "pyu" to "ぴゅ", "pyo" to "ぴょ"
    )
    private val japaneseWords = mapOf(
        "はし" to listOf("はし","橋","箸","端"), "こんにちは" to listOf("こんにちは"), "こんにちわ" to listOf("こんにちは", "こんにちわ"), "こんばんは" to listOf("こんばんは"),
        "ありがとう" to listOf("ありがとう", "有難う"), "すみません" to listOf("すみません"),
        "おねがいします" to listOf("お願いします"), "はい" to listOf("はい"), "いいえ" to listOf("いいえ"),
        "わたし" to listOf("私", "わたし"), "えき" to listOf("駅", "えき"),
        "れすとらん" to listOf("レストラン"), "どこ" to listOf("どこ", "何処"),
        "これ" to listOf("これ", "此れ"), "ください" to listOf("ください", "下さい"),
        "たすけて" to listOf("助けて"), "いくら" to listOf("いくら", "幾ら")
    )

    fun japaneseKana(romanText: String): String {
        val text = romanText.lowercase()
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            if (i + 1 < text.length && text[i] == text[i + 1] && text[i] in "bcdfghjklmpqrstvwxyz" && text[i] != 'n') {
                out.append('っ'); i++; continue
            }
            if (text[i] == 'n' && i + 1 < text.length && text[i + 1] == 'n') {
                out.append('ん')
                if (i + 2 < text.length && text[i + 2] in "aiueoy") i++ else i += 2
                continue
            }
            if (text[i] == 'n' && (i + 1 == text.length || text[i + 1] !in "aiueoy")) {
                out.append('ん'); i++; continue
            }
            val match = (3 downTo 1).firstNotNullOfOrNull { size ->
                if (i + size <= text.length) romaji[text.substring(i, i + size)]?.let { size to it } else null
            }
            if (match != null) { out.append(match.second); i += match.first }
            else { out.append(text[i]); i++ }
        }
        return out.toString()
    }

    fun japaneseCandidates(romanText: String): List<String> {
        if (romanText.isBlank()) return emptyList()
        val kana = japaneseKana(romanText)
        return japaneseWords[kana] ?: listOf(kana)
    }
}
