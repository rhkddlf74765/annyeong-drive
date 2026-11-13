package com.annyeongdrive.app.guide

import com.annyeongdrive.app.rental.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * 안내 음성의 Locale: 대여 언어에 나라까지 붙인 값을 쓴다. Pleos TTS는 나라 없는 "en"을 읽지 못하고 "en_US"는 읽는다.
 * 근거: 11/14 에뮬레이터 확인(9단계), 기능 확인 ③(Pleos TTS 영어), implementation-plan 8·9단계.
 */
class VoiceLocaleTest {

    /** 영어 대여는 en_US로 말한다. */
    @Test
    fun `영어 대여는 en_US로 말한다`() {
        // 상황 · 행동
        val locale = voiceLocaleOf(AppLanguage.ENGLISH)

        // 결과
        assertEquals(Locale.US, locale)
    }

    /** 모든 대여 언어는 나라가 붙은 Locale이다. */
    @Test
    fun `모든 대여 언어에 나라가 붙는다`() {
        // 상황 · 행동
        val countries = AppLanguage.entries.map { voiceLocaleOf(it).country }

        // 결과
        assertTrue("나라가 비어 있는 언어: $countries", countries.none { it.isEmpty() })
    }
}
