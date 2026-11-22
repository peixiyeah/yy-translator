package com.personal.translator

/** Small two-set Hangul composer. No network requests or dictionaries are needed. */
object Hangul {
    private const val leads = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    private const val vowels = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
    private const val tails = "\u0000ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"
    private val vowelPairs = mapOf("ㅗㅏ" to 'ㅘ', "ㅗㅐ" to 'ㅙ', "ㅗㅣ" to 'ㅚ', "ㅜㅓ" to 'ㅝ', "ㅜㅔ" to 'ㅞ', "ㅜㅣ" to 'ㅟ', "ㅡㅣ" to 'ㅢ')
    private val tailPairs = mapOf("ㄱㅅ" to 'ㄳ', "ㄴㅈ" to 'ㄵ', "ㄴㅎ" to 'ㄶ', "ㄹㄱ" to 'ㄺ', "ㄹㅁ" to 'ㄻ', "ㄹㅂ" to 'ㄼ', "ㄹㅅ" to 'ㄽ', "ㄹㅌ" to 'ㄾ', "ㄹㅍ" to 'ㄿ', "ㄹㅎ" to 'ㅀ', "ㅂㅅ" to 'ㅄ')
    private fun syllable(l: Int, v: Int, t: Int = 0) = (0xAC00 + (l * 21 + v) * 28 + t).toChar()
    fun append(text: String, key: Char): String {
        if (text.isEmpty()) return key.toString()
        val last = text.last()
        val prefix = text.dropLast(1)
        val v = vowels.indexOf(key)
        if (last in '\uAC00'..'\uD7A3') {
            val n = last.code - 0xAC00
            val l0 = n / 588; val v0 = n / 28 % 21; val t0 = n % 28
            if (v >= 0) {
                if (t0 == 0) {
                    val combined = vowelPairs["${vowels[v0]}$key"]
                    return if (combined != null) prefix + syllable(l0, vowels.indexOf(combined)) else text + key
                }
                val split = tailPairs.entries.firstOrNull { it.value == tails[t0] }?.key
                val keep = if (split == null) 0 else tails.indexOf(split[0])
                val nextLead = leads.indexOf(split?.get(1) ?: tails[t0])
                return prefix + syllable(l0, v0, keep) + syllable(nextLead, v)
            }
            val t = tails.indexOf(key)
            if (t > 0 && t0 == 0) return prefix + syllable(l0, v0, t)
            if (t > 0) tailPairs["${tails[t0]}$key"]?.let { return prefix + syllable(l0, v0, tails.indexOf(it)) }
        } else if (v >= 0) {
            val l = leads.indexOf(last)
            if (l >= 0) return prefix + syllable(l, v)
            vowelPairs["$last$key"]?.let { return prefix + it }
        }
        return text + key
    }
    fun backspace(text: String): String {
        if (text.isEmpty()) return text
        val last = text.last(); val prefix = text.dropLast(1)
        if (last in '\uAC00'..'\uD7A3') {
            val n = last.code - 0xAC00
            val l = n / 588; val v = n / 28 % 21; val t = n % 28
            if (t > 0) {
                val split = tailPairs.entries.firstOrNull { it.value == tails[t] }?.key
                return prefix + syllable(l, v, if (split == null) 0 else tails.indexOf(split[0]))
            }
            val split = vowelPairs.entries.firstOrNull { it.value == vowels[v] }?.key
            return prefix + if (split == null) leads[l] else syllable(l, vowels.indexOf(split[0]))
        }
        val count = Character.charCount(text.codePointBefore(text.length))
        return text.dropLast(count)
    }
}
