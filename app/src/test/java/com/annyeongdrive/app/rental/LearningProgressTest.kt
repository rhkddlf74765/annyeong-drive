package com.annyeongdrive.app.rental

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 교육·퀴즈 진행 기록: 응답, 건너뛰기, 복습 목록, 처음부터 보기, 미완료 판단, 콘텐츠 버전.
 * 근거: 기능 실행안 부록 B "동작 기준"·복습 화면, 결정 문서 4장(진행 저장 형식 규칙).
 */
class LearningProgressTest {

    private val order = listOf(Q1, Q2, Q3)

    /** 새 진행은 교육을 시작하기 전 상태인지 확인한다. */
    @Test
    fun `처음 만든 교육 진행은 시작 전이다`() {
        // 상황
        val progress = LearningProgress.fresh(VERSION)
        // 행동
        val begun = progress.begun
        // 결과
        assertFalse(begun)
        assertFalse(progress.hasUnfinished(order))
    }

    /** "교육 시작"을 누르면 시작한 것으로 기록되는지 확인한다. */
    @Test
    fun `교육 시작을 누르면 시작한 것으로 기록된다`() {
        // 상황
        val progress = LearningProgress.fresh(VERSION)
        // 행동
        val begun = progress.begin()
        // 결과
        assertTrue(begun.begun)
    }

    /** {완료 수}는 정답 수가 아니라 정답 확인까지 마친 문항 수인지 확인한다. */
    @Test
    fun `완료 수는 정답 수가 아니라 응답한 문항 수다`() {
        // 상황
        val progress = LearningProgress.fresh(VERSION).begin()
        // 행동
        val answered = progress.answer(Q1, correct = true).answer(Q2, correct = false)
        // 결과
        assertEquals(2, answered.answeredCount())
        assertEquals(1, answered.remainingCount(order))
    }

    /** 정답을 확인한 문항은 다시 답해도 처음 기록이 유지되는지 확인한다. */
    @Test
    fun `확인한 답은 다시 바꿀 수 없다`() {
        // 상황
        val progress = LearningProgress.fresh(VERSION).begin().answer(Q1, correct = false)
        // 행동
        val again = progress.answer(Q1, correct = true)
        // 결과
        assertSame(progress, again)
        assertEquals(false, again.attempt.items.getValue(Q1).correct)
    }

    /** 틀린 문항이 복습 목록에 들어가는지 확인한다. */
    @Test
    fun `틀린 문항은 복습 목록에 들어간다`() {
        // 상황
        val progress = LearningProgress.fresh(VERSION).begin()
        // 행동
        val answered = progress.answer(Q1, correct = false)
        // 결과
        assertEquals(listOf(Q1), answered.reviewIds)
    }

    /** 건너뛴 문항이 미완료로 남고 복습 목록에 들어가는지 확인한다. */
    @Test
    fun `건너뛴 문항은 미완료로 남고 복습 목록에 들어간다`() {
        // 상황
        val progress = LearningProgress.fresh(VERSION).begin()
        // 행동
        val skipped = progress.skip(Q2)
        // 결과
        assertEquals(ItemStatus.SKIPPED, skipped.attempt.items.getValue(Q2).status)
        assertNull(skipped.attempt.items.getValue(Q2).correct)
        assertEquals(listOf(Q2), skipped.reviewIds)
        assertEquals(3, skipped.remainingCount(order))
    }

    /** 응답한 문항을 건너뛰어도 응답 기록이 남는지 확인한다. */
    @Test
    fun `응답한 문항은 건너뛰어도 응답 기록이 유지된다`() {
        // 상황
        val progress = LearningProgress.fresh(VERSION).begin().answer(Q1, correct = true)
        // 행동
        val skipped = progress.skip(Q1)
        // 결과
        assertSame(progress, skipped)
    }

    /** 복습 목록에 있던 문항을 다음 회차에서 맞히면 복습 목록에서 빠지는지 확인한다. */
    @Test
    fun `복습 목록에 있던 문항을 맞히면 복습 목록에서 빠진다`() {
        // 상황
        val progress = LearningProgress.fresh(VERSION).begin().skip(Q1).restart()
        // 행동
        val answered = progress.answer(Q1, correct = true)
        // 결과
        assertTrue(answered.reviewIds.isEmpty())
    }

    /** "처음부터 보기"를 하면 회차 기록은 비워지고 복습 목록과 본 카드는 남는지 확인한다. */
    @Test
    fun `처음부터 보기를 하면 이번 회차는 새로 시작하고 복습 목록은 남는다`() {
        // 상황
        val progress = LearningProgress.fresh(VERSION).begin()
            .viewCard(CARD).answer(Q1, correct = false).skip(Q2)
        // 행동
        val restarted = progress.restart()
        // 결과
        assertEquals(2, restarted.attempt.number)
        assertTrue(restarted.attempt.items.isEmpty())
        assertNull(restarted.attempt.position)
        assertEquals(listOf(Q1, Q2), restarted.reviewIds)
        assertEquals(setOf(CARD), restarted.viewedCardIds)
        assertTrue(restarted.begun)
    }

    /** 시작했지만 응답하지 않은 문항이 있으면 미완료 교육으로 보는지 확인한다. */
    @Test
    fun `시작했지만 응답하지 않은 문항이 있으면 미완료 교육이 있다`() {
        // 상황
        val progress = LearningProgress.fresh(VERSION).begin()
        // 행동
        val answered = progress.answer(Q1, correct = true)
        // 결과
        assertTrue(answered.hasUnfinished(order))
    }

    /** 모든 문항에 응답하면 미완료 교육이 없는지 확인한다. */
    @Test
    fun `모든 문항에 응답하면 미완료 교육이 없다`() {
        // 상황
        val progress = LearningProgress.fresh(VERSION).begin()
        // 행동
        val done = progress.answer(Q1, true).answer(Q2, false).answer(Q3, true)
        // 결과
        assertFalse(done.hasUnfinished(order))
    }

    /** 마지막 문항을 보고 있을 때만 마지막 문항으로 판단하는지 확인한다. */
    @Test
    fun `마지막 문항을 보고 있으면 마지막 문항이다`() {
        // 상황
        val progress = LearningProgress.fresh(VERSION).begin()
        // 행동
        val atSecond = progress.showQuestion(Q2)
        val atLast = progress.showQuestion(Q3)
        // 결과
        assertFalse(atSecond.isLastQuestion(order))
        assertTrue(atLast.isLastQuestion(order))
    }

    /** 이어 볼 위치가 마지막으로 본 화면과 항목인지 확인한다. */
    @Test
    fun `이어 볼 위치는 마지막으로 본 화면과 항목이다`() {
        // 상황
        val progress = LearningProgress.fresh(VERSION).begin().viewCard(CARD)
        // 행동
        val moved = progress.showQuestion(Q2)
        // 결과
        assertEquals(LearningPosition(LearningScreen.QUIZ, Q2), moved.attempt.position)
    }

    /** 교육 문구 버전이 다르면 진행을 버리고 새로 시작하는지 확인한다. */
    @Test
    fun `콘텐츠 버전이 다르면 진행을 버리고 새로 시작한다`() {
        // 상황
        val progress = LearningProgress.fresh(VERSION).begin().skip(Q1)
        // 행동
        val aligned = progress.alignedTo(NEW_VERSION)
        // 결과
        assertEquals(LearningProgress.fresh(NEW_VERSION), aligned)
    }

    /** 교육 문구 버전이 같으면 진행을 그대로 두는지 확인한다. */
    @Test
    fun `콘텐츠 버전이 같으면 진행을 유지한다`() {
        // 상황
        val progress = LearningProgress.fresh(VERSION).begin().skip(Q1)
        // 행동
        val aligned = progress.alignedTo(VERSION)
        // 결과
        assertSame(progress, aligned)
    }

    private companion object {
        const val VERSION = "2026-11-10"
        const val NEW_VERSION = "2026-11-20"
        const val Q1 = "quiz.lane_direction"
        const val Q2 = "quiz.school_zone_stop"
        const val Q3 = "quiz.roundabout_yield"
        const val CARD = "card.keep_right"
    }
}
