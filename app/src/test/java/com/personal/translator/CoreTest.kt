package com.personal.translator

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CoreTest {
    private fun type(keys: String) = keys.fold("") { text, c -> Hangul.append(text, c) }
    @Test fun koreanPhrase() { assertEquals("안녕하세요", type("ㅇㅏㄴㄴㅕㅇㅎㅏㅅㅔㅇㅛ")) }
    @Test fun finalConsonantMovesToNextSyllable() { assertEquals("가나", type("ㄱㅏㄴㅏ")) }
    @Test fun compoundFinalSplits() { assertEquals("읽어", type("ㅇㅣㄹㄱㅇㅓ")); assertEquals("달가", type("ㄷㅏㄹㄱㅏ")) }
    @Test fun compoundVowels() { assertEquals("괜찮아요", type("ㄱㅗㅐㄴㅊㅏㄴㅎㅇㅏㅇㅛ")); assertEquals("쉬워", type("ㅅㅜㅣㅇㅜㅓ")) }
    @Test fun backspaceUncomposes() {
        assertEquals("일", Hangul.backspace("읽")); assertEquals("고", Hangul.backspace("과"))
        assertEquals("ㄱ", Hangul.backspace("가")); assertEquals("", Hangul.backspace("😀"))
    }
    @Test fun latinAndPunctuation() { assertEquals("Hello!", type("Hello!")); assertEquals("안녕!", type("ㅇㅏㄴㄴㅕㅇ!")) }
    @Test fun wavHeaderMatchesPayload() {
        val pcm = ByteArray(32000)
        val wav = Wav.encode(pcm)
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(wav, 0, 4)); assertEquals(32036, b.getInt(4))
        assertEquals(16000, b.getInt(24)); assertEquals(32000, b.getInt(40)); assertEquals(32044, wav.size)
    }
}
