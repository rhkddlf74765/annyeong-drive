package com.annyeongdrive.app.rental

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * 운전자 데이터 루트: 대여 시작, 대여 설정 완료, 언어, 교육 진행 변경.
 * 근거: 결정 문서 3·4·6장, docs/state-machine.md T2·T3(대여 설정 여부 가드).
 */
class RentalSessionTest {

    /**
     * 테스트용 대여를 시작한다.
     *
     * @return 막 시작한 대여
     */
    private fun started(): RentalSession = RentalSession.start(
        RentalStartRequest(
            rentalId = RENTAL_ID,
            startedAt = STARTED_AT,
            language = AppLanguage.ENGLISH,
            contentVersion = VERSION,
        ),
    )

    /** 대여를 시작하면 언어와 시작 시각이 기록되고 대여 설정은 아직 끝나지 않았는지 확인한다. */
    @Test
    fun `대여를 시작하면 고른 언어와 시작 시각이 기록되고 대여 설정은 끝나지 않은 상태다`() {
        // 상황
        val request = RentalStartRequest(RENTAL_ID, STARTED_AT, AppLanguage.ENGLISH, VERSION)
        // 행동
        val session = RentalSession.start(request)
        // 결과
        assertEquals(RENTAL_ID, session.rentalId)
        assertEquals(STARTED_AT, session.startedAt)
        assertEquals(AppLanguage.ENGLISH, session.language)
        assertFalse(session.hasRentalSetup())
        assertEquals(LearningProgress.fresh(VERSION), session.learning)
    }

    /** 출발 전 점검을 마치면 대여 설정이 끝난 것으로 보는지 확인한다. */
    @Test
    fun `출발 전 점검을 마치면 대여 설정이 끝난 것으로 본다`() {
        // 상황
        val session = started()
        // 행동
        val checked = session.completePrecheck()
        // 결과
        assertTrue(checked.hasRentalSetup())
    }

    /** 운전 국가는 확장 기능이라 지금은 비워 두는지 확인한다. */
    @Test
    fun `운전 국가는 지금은 비워 둔다`() {
        // 상황
        val session = started()
        // 행동
        val country = session.drivingCountry
        // 결과
        assertNull(country)
    }

    /** 언어를 바꿔도 교육 진행이 유지되는지 확인한다. */
    @Test
    fun `언어를 바꿔도 교육 진행은 유지된다`() {
        // 상황
        val session = started().beginLearning().skipQuestion(Q1)
        // 행동
        val changed = session.changeLanguage(AppLanguage.KOREAN)
        // 결과
        assertEquals(AppLanguage.KOREAN, changed.language)
        assertEquals(session.learning, changed.learning)
    }

    /** 대여 루트의 메서드로 교육 진행이 바뀌고, 원래 객체는 그대로인지 확인한다(불변). */
    @Test
    fun `교육 진행은 대여의 메서드로 바뀌고 원래 대여는 그대로다`() {
        // 상황
        val session = started().beginLearning()
        // 행동
        val answered = session.answerQuestion(Q1, correct = false)
        // 결과
        assertEquals(listOf(Q1), answered.learning.reviewIds)
        assertTrue(session.learning.reviewIds.isEmpty())
    }

    /** 교육 문구 버전이 바뀌면 대여 정보는 두고 교육 진행만 새로 시작하는지 확인한다. */
    @Test
    fun `콘텐츠 버전이 바뀌면 대여는 유지하고 교육 진행만 새로 시작한다`() {
        // 상황
        val session = started().completePrecheck().beginLearning().skipQuestion(Q1)
        // 행동
        val aligned = session.alignContentVersion(NEW_VERSION)
        // 결과
        assertTrue(aligned.hasRentalSetup())
        assertEquals(RENTAL_ID, aligned.rentalId)
        assertEquals(LearningProgress.fresh(NEW_VERSION), aligned.learning)
    }

    private companion object {
        const val RENTAL_ID = "rental-0001"
        const val VERSION = "2026-11-10"
        const val NEW_VERSION = "2026-11-20"
        const val Q1 = "quiz.lane_direction"
        val STARTED_AT: Instant = Instant.parse("2026-11-12T00:00:00Z")
    }
}
