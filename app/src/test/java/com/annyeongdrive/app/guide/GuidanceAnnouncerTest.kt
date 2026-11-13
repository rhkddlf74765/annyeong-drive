package com.annyeongdrive.app.guide

import com.annyeongdrive.app.R
import com.annyeongdrive.app.rental.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 안내 문장 만들기: 이번 대여에서 처음 만난 규칙은 2문장, 다시 만나면 1문장. 제한속도가 없으면 숫자 없는 문장.
 * 음성을 쓸 수 있는 언어(영어)는 말하고, 아니면(한국어) 알림 문장으로 띄운다. 주정차 경고는 언어와 상관없이 알림을 띄운다.
 * 근거: 기능 보드 "주행 중 규칙 안내" 3·4번·"주정차 경고" 2·3·5번, 부록 E(음성 미지원 → 화면으로 계속),
 * CLAUDE.md 도메인 원칙(30 고정 금지), implementation-plan 8단계.
 */
class GuidanceAnnouncerTest {

    private val delivered = mutableListOf<GuidanceMessage>()
    private var language: AppLanguage? = AppLanguage.ENGLISH
    private val announcer = GuidanceAnnouncer(EncounterBasedPolicy(), { delivered += it }, { language })

    /** 처음 만난 보호구역은 2문장(핵심 + 결과), 두 번째는 1문장이다. */
    @Test
    fun `처음 만난 규칙은 2문장이고 다시 만나면 1문장이다`() {
        // 상황
        val zone = RoadRuleReminder.SchoolZone("SZ1", 30)

        // 행동
        announcer.remindRoadRule(zone)
        announcer.remindRoadRule(zone)

        // 결과
        assertEquals(2, delivered[0].sentences.size)
        assertEquals(listOf(GuidanceSentence(R.string.guide_school_zone_core_limit, 30)), delivered[1].sentences)
        assertEquals(2, delivered[1].encounter)
    }

    /** 제한속도를 모르는 보호구역은 숫자 없는 문장으로 안내한다. */
    @Test
    fun `제한속도를 모르면 숫자 없는 문장으로 안내한다`() {
        // 상황 · 행동
        announcer.remindRoadRule(RoadRuleReminder.SchoolZone("SZ1", null))

        // 결과
        val core = delivered.single().sentences.first()
        assertEquals(R.string.guide_school_zone_core, core.textId)
        assertNull(core.limitKmh)
    }

    /** 어린이보호구역 카메라는 일반 카메라와 다른 문장이다. */
    @Test
    fun `어린이보호구역 카메라는 그렇다고 말한다`() {
        // 상황 · 행동
        announcer.remindRoadRule(RoadRuleReminder.SpeedCamera("C1", 30, inSchoolZone = true))
        announcer.remindRoadRule(RoadRuleReminder.SpeedCamera("C2", 50, inSchoolZone = false))

        // 결과
        assertEquals(R.string.guide_school_camera_core_limit, delivered[0].sentences.first().textId)
        assertEquals(GuidanceSentence(R.string.guide_camera_core_limit, 50), delivered[1].sentences.first())
    }

    /** 영어 대여는 음성으로 말하고 규칙 안내 알림은 띄우지 않는다. */
    @Test
    fun `영어 대여는 음성으로 안내한다`() {
        // 상황 · 행동
        announcer.remindRoadRule(RoadRuleReminder.SchoolZone("SZ1", 30))

        // 결과
        assertTrue(delivered.single().speak)
        assertNull(delivered.single().channel)
    }

    /** 한국어 대여는 음성을 쓸 수 없어 알림 문장으로 안내한다. */
    @Test
    fun `한국어 대여는 음성 대신 알림 문장으로 안내한다`() {
        // 상황
        language = AppLanguage.KOREAN

        // 행동
        announcer.remindRoadRule(RoadRuleReminder.SchoolZone("SZ1", 30))

        // 결과
        assertFalse(delivered.single().speak)
        assertSame(GuidanceChannel.ROAD_RULE, delivered.single().channel)
        assertSame(AppLanguage.KOREAN, delivered.single().language)
    }

    /** 주정차 경고는 한국어 대여에도 알림을 띄우고, 영어 대여는 음성과 알림을 함께 낸다. 문장은 확인을 요청한다. */
    @Test
    fun `주정차 경고는 언어와 상관없이 알림을 띄운다`() {
        // 상황
        language = AppLanguage.KOREAN
        announcer.announceParkingWarning("PC5")
        language = AppLanguage.ENGLISH

        // 행동
        announcer.announceParkingWarning("PC5")

        // 결과
        assertSame(GuidanceChannel.PARKING_WARNING, delivered[0].channel)
        assertFalse(delivered[0].speak)
        assertSame(GuidanceChannel.PARKING_WARNING, delivered[1].channel)
        assertTrue(delivered[1].speak)
        assertEquals(GuidanceSentence(R.string.guide_parking_core), delivered[0].sentences.first())
        assertEquals(1, delivered[1].sentences.size)
    }

    /** 반납으로 대여가 끝나면 다음 대여에서는 다시 처음 만남(2문장)으로 센다. */
    @Test
    fun `대여가 끝나면 처음 만남부터 다시 센다`() {
        // 상황
        val zone = RoadRuleReminder.SchoolZone("SZ1", 30)
        announcer.remindRoadRule(zone)

        // 행동
        announcer.forgetEncounters()
        announcer.remindRoadRule(zone)

        // 결과
        assertEquals(1, delivered[1].encounter)
        assertEquals(2, delivered[1].sentences.size)
    }
}
