import com.personal.translator.Hangul
import com.personal.translator.Wav
import java.nio.ByteBuffer
import java.nio.ByteOrder

fun main() {
    fun type(keys: String) = keys.fold("") { text, char -> Hangul.append(text, char) }
    val phrases = mapOf(
        "ㅇㅏㄴㄴㅕㅇㅎㅏㅅㅔㅇㅛ" to "안녕하세요", "ㄱㅏㄴㅏ" to "가나",
        "ㅇㅣㄹㄱㅇㅓ" to "읽어", "ㄷㅏㄹㄱㅏ" to "달가", "ㄱㅗㅐㄴㅊㅏㄴㅎㅇㅏㅇㅛ" to "괜찮아요",
        "ㅅㅜㅣㅇㅜㅓ" to "쉬워", "Hello!" to "Hello!", "ㅇㅏㄴㄴㅕㅇ!" to "안녕!",
        "ㅆㅏㅇ" to "쌍", "ㅅㅣㄱㄷㅏㅇㅇㅣ ㅇㅓㄷㅣㅇㅔ ㅇㅣㅆㄴㅏㅇㅛ?" to "식당이 어디에 있나요?"
    )
    phrases.forEach { (keys, expected) -> check(type(keys) == expected) { "$keys: ${type(keys)} != $expected" } }
    mapOf("읽" to "일", "과" to "고", "가" to "ㄱ", "😀" to "", "" to "").forEach { (input, expected) -> check(Hangul.backspace(input) == expected) }
    // All modern Hangul syllables should survive lead/vowel/tail composition.
    val leads = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    val vowels = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
    val tails = "\u0000ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"
    for (l in leads.indices) for (v in vowels.indices) for (t in tails.indices) {
        val keys = "${leads[l]}${vowels[v]}" + if (t == 0) "" else tails[t]
        check(type(keys) == (0xAC00 + (l * 21 + v) * 28 + t).toChar().toString())
    }
    val wav = Wav.encode(ByteArray(32000))
    val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
    check(String(wav, 0, 4) == "RIFF" && String(wav, 8, 4) == "WAVE")
    check(b.getInt(4) == 32036 && b.getInt(24) == 16000 && b.getInt(40) == 32000 && wav.size == 32044)
    println("PASS: 10 input phrases, 5 deletion cases, 11,172 Hangul syllables, PCM WAV headers.")
}
