package com.annyeongdrive.app.storage

import com.annyeongdrive.app.drive.LearningResumePoint
import com.annyeongdrive.app.learning.QuizOrder
import com.annyeongdrive.app.rental.RentalSession
import com.annyeongdrive.app.storage.StorageFixtures.NOW
import com.annyeongdrive.app.storage.StorageFixtures.QUIZ_IDS
import com.annyeongdrive.app.time.Clock
import com.annyeongdrive.app.vendor.ReturnTerms
import com.annyeongdrive.app.vendor.VendorConfig
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/**
 * Coordinator가 읽는 대여·교육 상태: 저장된 Aggregate를 상태 머신 가드로 바꿔 읽는다.
 * 근거: docs/state-machine.md T2·T3·T4·B1·T15·T29·T30·B6·B2 가드, 결정 문서 3·4장.
 */
class StoredStatusReadersTest {

    private val session = MutableStateFlow<RentalSession?>(null)
    private val vendor = MutableStateFlow(VendorConfig.initial())
    private val order = QuizOrder { QUIZ_IDS }
    private val rentalReader = StoredRentalStatusReader(session, vendor, Clock { NOW })
    private val learningReader = StoredLearningStatusReader(session, order)

    /** 대여가 없으면 대여 설정이 없는 것으로 보는지 확인한다(T2). */
    @Test
    fun `대여가 없으면 대여 설정이 없다`() {
        // 상황
        session.value = null
        // 행동
        val hasSetup = rentalReader.hasRentalSetup()
        // 결과
        assertFalse(hasSetup)
    }

    /** 출발 전 점검을 마친 대여가 있으면 대여 설정이 있는 것으로 보는지 확인한다(T3). */
    @Test
    fun `출발 전 점검을 마친 대여가 있으면 대여 설정이 있다`() {
        // 상황
        session.value = StorageFixtures.startedSession().completePrecheck()
        // 행동
        val hasSetup = rentalReader.hasRentalSetup()
        // 결과
        assertTrue(hasSetup)
    }

    /** 주입한 시계 기준으로 반납 예정 + N시간이 지났는지 읽는지 확인한다(T4). */
    @Test
    fun `지금 시각이 반납 예정 뒤 N시간을 넘으면 예비 초기화 대상이다`() {
        // 상황
        vendor.value = VendorConfig.initial()
            .withReturnTerms(ReturnTerms(dueAt = NOW.minus(Duration.ofHours(25))))
        // 행동
        val overdue = rentalReader.isResetOverdue()
        // 결과
        assertTrue(overdue)
    }

    /** 대여가 없으면 교육 시작 전이고 미완료 교육도 없는 것으로 보는지 확인한다(B1, T15). */
    @Test
    fun `대여가 없으면 교육 시작 전이고 미완료 교육도 없다`() {
        // 상황
        session.value = null
        // 행동
        val started = learningReader.hasStarted()
        val unfinished = learningReader.hasUnfinished()
        // 결과
        assertFalse(started)
        assertFalse(unfinished)
    }

    /** 문항 순서를 받아 미완료 교육을 판단하는지 확인한다(T15). */
    @Test
    fun `문항 순서 중 응답하지 않은 문항이 있으면 미완료 교육이 있다`() {
        // 상황
        session.value = StorageFixtures.startedSession().beginLearning().answerQuestion(QUIZ_IDS[0], correct = true)
        // 행동
        val unfinished = learningReader.hasUnfinished()
        // 결과
        assertTrue(unfinished)
    }

    /** 문항 순서의 마지막 문항을 보고 있으면 마지막 문항으로 읽는지 확인한다(T30, B6). */
    @Test
    fun `순서의 마지막 문항을 보고 있으면 마지막 문항이다`() {
        // 상황
        session.value = StorageFixtures.startedSession().beginLearning().showQuestion(QUIZ_IDS.last())
        // 행동
        val last = learningReader.isLastQuestion()
        // 결과
        assertTrue(last)
    }

    /** 문제를 보다 멈췄으면 이어 보기 위치가 문제인지 확인한다(B2). */
    @Test
    fun `문제를 보다 멈췄으면 이어 보기 위치는 문제다`() {
        // 상황
        session.value = StorageFixtures.startedSession().beginLearning().viewCard("card.keep_right").showQuestion(QUIZ_IDS[1])
        // 행동
        val point = learningReader.resumePoint()
        // 결과
        assertEquals(LearningResumePoint.QUIZ, point)
    }

    /** 본 위치가 없으면 이어 보기 위치가 학습 카드인지 확인한다(B2). */
    @Test
    fun `본 위치가 없으면 이어 보기 위치는 학습 카드다`() {
        // 상황
        session.value = StorageFixtures.startedSession().beginLearning()
        // 행동
        val point = learningReader.resumePoint()
        // 결과
        assertEquals(LearningResumePoint.CARD, point)
    }
}
