package com.annyeongdrive.app.guide

import com.annyeongdrive.app.R
import com.annyeongdrive.app.rental.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 통화 중 안내: 통화 중(벨이 울리는 중 포함)에는 안내를 말하지 않고 같은 문장을 알림으로만 띄운다. 통화 중이 아니면 그대로 말한다.
 * 근거: 11/14 에뮬레이터 확인(통화 중에도 안내 음성이 끝까지 재생됨), 11/14 사용자 결정 (가) 통화 중에는 알림만,
 * 기능 보드 "주정차 경고" 2번(알림은 언어와 상관없이 띄움).
 */
class CallAwareGuidanceOutputTest {

    private val delivered = mutableListOf<GuidanceMessage>()
    private var inCall = false
    private val output = CallAwareGuidanceOutput({ delivered += it }, { inCall })

    /** 통화 중이 아니면 안내를 바꾸지 않고 그대로 말한다. */
    @Test
    fun `통화 중이 아니면 안내를 그대로 말한다`() {
        // 상황
        val message = spokenRoadRule()

        // 행동
        output.deliver(message)

        // 결과
        assertSame(message, delivered.single())
    }

    /** 통화 중 주행 규칙 안내는 말하지 않고 규칙 안내 알림으로 띄운다. */
    @Test
    fun `통화 중 주행 규칙 안내는 말하지 않고 알림으로 띄운다`() {
        // 상황
        inCall = true

        // 행동
        output.deliver(spokenRoadRule())

        // 결과
        val shown = delivered.single()
        assertFalse(shown.speak)
        assertEquals(GuidanceChannel.ROAD_RULE, shown.channel)
        assertEquals(spokenRoadRule().sentences, shown.sentences)
    }

    /** 통화 중 주정차 경고는 말하지 않고 주정차 경고 알림으로만 띄운다. */
    @Test
    fun `통화 중 주정차 경고는 말하지 않고 경고 알림만 띄운다`() {
        // 상황
        inCall = true

        // 행동
        output.deliver(spokenParkingWarning())

        // 결과
        val shown = delivered.single()
        assertFalse(shown.speak)
        assertEquals(GuidanceChannel.PARKING_WARNING, shown.channel)
    }

    /** 원래 알림만 띄우던 안내(한국어 대여)는 통화 중에도 그대로 띄운다. */
    @Test
    fun `원래 말하지 않던 안내는 통화 중에도 그대로 띄운다`() {
        // 상황
        inCall = true
        val message = spokenRoadRule().copy(language = AppLanguage.KOREAN, speak = false, channel = GuidanceChannel.ROAD_RULE)

        // 행동
        output.deliver(message)

        // 결과
        assertSame(message, delivered.single())
    }

    /**
     * 영어 대여에서 말하는 보호구역 안내를 만든다(알림 종류 없음).
     *
     * @return 말하는 주행 규칙 안내
     */
    private fun spokenRoadRule(): GuidanceMessage = GuidanceMessage(
        rule = Rule.SCHOOL_ZONE,
        encounter = 1,
        sentences = listOf(GuidanceSentence(R.string.guide_school_zone_core_limit, 30)),
        language = AppLanguage.ENGLISH,
        speak = true,
        channel = null,
    )

    /**
     * 영어 대여에서 말하고 알림도 띄우는 주정차 경고를 만든다.
     *
     * @return 말하는 주정차 경고
     */
    private fun spokenParkingWarning(): GuidanceMessage = GuidanceMessage(
        rule = Rule.PARKING_CAMERA,
        encounter = 1,
        sentences = listOf(GuidanceSentence(R.string.guide_parking_core)),
        language = AppLanguage.ENGLISH,
        speak = true,
        channel = GuidanceChannel.PARKING_WARNING,
    )
}
