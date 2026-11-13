package com.annyeongdrive.app.rental

import com.annyeongdrive.app.storage.StorageFixtures.CONTENT_VERSION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 문항 이동 규칙: 다음 문항은 지금 문항 뒤의 응답하지 않은 첫 문항이고, 뒤에 남은 문항이 없으면 마지막 문항이다.
 * 이어 보기로 중간 문항에 들어와도 상태 머신(T29·T30·B6)과 화면 흐름이 같은 기준을 쓴다.
 * 근거: 결정 문서 4장(건너뛴 문항은 미완료), docs/state-machine.md T28~T30·B6, implementation-plan 6단계.
 */
class QuizNavigationTest {

    private val order = listOf("q1", "q2", "q3")

    /**
     * 교육을 시작한 진행을 만든다.
     *
     * @return 진행
     */
    private fun started(): LearningProgress = LearningProgress.fresh(CONTENT_VERSION).begin()

    /** 다음 문항은 지금 문항 뒤의 응답하지 않은 첫 문항이다. */
    @Test
    fun `다음 문항은 지금 문항 뒤의 응답하지 않은 첫 문항이다`() {
        // 상황
        val atFirst = started().answer("q2", true).showQuestion("q1")

        // 행동
        val next = atFirst.nextUnansweredAfterPosition(order)

        // 결과
        assertEquals("q3", next)
    }

    /** 뒤의 문항을 이미 모두 답했으면 마지막 문항으로 본다. */
    @Test
    fun `뒤의 문항을 모두 답했으면 지금 문항이 마지막이다`() {
        // 상황
        val progress = started().answer("q2", true).answer("q3", false).showQuestion("q1")

        // 행동 · 결과
        assertTrue(progress.isLastQuestion(order))
        assertNull(progress.nextUnansweredAfterPosition(order))
    }

    /** 건너뛴 문항은 응답하지 않은 문항이라 다시 찾을 수 있다. */
    @Test
    fun `건너뛴 문항은 응답하지 않은 첫 문항으로 찾는다`() {
        // 상황
        val progress = started().skip("q1").answer("q2", true)

        // 행동
        val first = progress.firstUnanswered(order)

        // 결과
        assertEquals("q1", first)
    }

    /** 카드를 보고 있을 때는 마지막 문항이 아니다. */
    @Test
    fun `카드를 보고 있으면 마지막 문항이 아니다`() {
        // 상황
        val progress = started().viewCard("card.a")

        // 행동 · 결과
        assertFalse(progress.isLastQuestion(order))
    }

    /** 답한 문항의 정답 여부를 돌려주고, 답하지 않은 문항은 null이다. */
    @Test
    fun `답한 문항만 정답 여부가 있다`() {
        // 상황
        val progress = started().answer("q1", false).skip("q2")

        // 행동 · 결과
        assertEquals(false, progress.answeredCorrectly("q1"))
        assertNull(progress.answeredCorrectly("q2"))
        assertNull(progress.answeredCorrectly("q3"))
    }
}
