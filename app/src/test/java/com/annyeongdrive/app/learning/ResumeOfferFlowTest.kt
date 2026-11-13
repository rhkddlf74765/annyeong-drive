package com.annyeongdrive.app.learning

import com.annyeongdrive.app.admin.AdminTestHarness
import com.annyeongdrive.app.drive.state.LearningCard
import com.annyeongdrive.app.drive.state.LearningQuiz
import com.annyeongdrive.app.drive.state.ParkedResumeOffer
import com.annyeongdrive.app.drive.state.ParkedRest
import com.annyeongdrive.app.learning.LearningTestSteps.FIRST_CARD
import com.annyeongdrive.app.learning.LearningTestSteps.FIRST_QUESTION
import com.annyeongdrive.app.learning.LearningTestSteps.SECOND_QUESTION
import com.annyeongdrive.app.learning.LearningTestSteps.answerAndNext
import com.annyeongdrive.app.learning.LearningTestSteps.driveAndStop
import com.annyeongdrive.app.learning.LearningTestSteps.shownQuestionId
import com.annyeongdrive.app.learning.LearningTestSteps.toFirstQuestion
import com.annyeongdrive.app.storage.TempDataStores
import com.annyeongdrive.app.vehicle.UxRestriction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "이어서 확인할까요?": 미완료 교육이 있을 때 정차하면 제안하고, 이어 보기·처음부터 보기·나중에 보기를 고른다.
 * "처음부터 보기"는 새 회차를 저장한 뒤 이어 보기(T19·B2)로 들어가 첫 카드를 본다(상태 머신 변경 없음, 11/14 결정).
 * 예외 상황(화면 제한 중 처음부터 보기, 제안 화면이 아닐 때의 입력, 모두 답한 뒤 정차)을 함께 확인한다.
 * 실제 DataStore 파일·엔진·Coordinator·1차 콘텐츠를 연결해 확인한다.
 * 근거: 기능 실행안 부록 B(이어 보기·처음부터 보기), 결정 문서 4장, docs/state-machine.md T15·T16·T19·T20·B2, implementation-plan 7단계.
 */
class ResumeOfferFlowTest {

    private val stores = TempDataStores()
    private val writeScope = CoroutineScope(SupervisorJob())

    /** 파일 쓰기 범위를 닫고 임시 파일을 지운다. */
    @After
    fun tearDown() {
        writeScope.cancel()
        stores.delete()
    }

    /**
     * 첫 문항을 틀리게 답하고 둘째 문항에서 출발했다가 다시 서서, 이어서 확인할까요 화면에 있는 앱을 만든다.
     *
     * @return 이어서 확인할까요 화면에 있는 앱
     */
    private suspend fun atResumeOffer(): AdminTestHarness {
        val app = AdminTestHarness(stores, writeScope, catalog = CORE_CATALOG)
        toFirstQuestion(app)
        answerAndNext(app, 1)
        driveAndStop(app)
        return app
    }

    /** 이어 보기를 누르면 멈춘 문제로 간다. */
    @Test
    fun `이어 보기를 누르면 멈춘 문제로 간다`() = runBlocking {
        // 상황
        val app = atResumeOffer()
        val offered = app.engine.state.value

        // 행동
        app.resumeOffer.resume()
        app.learningFlow.sync()

        // 결과
        assertSame(ParkedResumeOffer, offered)
        assertSame(LearningQuiz, app.engine.state.value)
        assertEquals(SECOND_QUESTION, shownQuestionId(app))
    }

    /** 처음부터 보기를 누르면 새 회차가 되어 첫 카드로 가고, 틀린 문항의 복습 목록은 남는다. */
    @Test
    fun `처음부터 보기를 누르면 첫 카드로 가고 새 회차가 되며 복습 목록은 남는다`() = runBlocking {
        // 상황
        val app = atResumeOffer()

        // 행동
        app.resumeOffer.restart()
        app.learningFlow.sync()

        // 결과
        assertSame(LearningCard, app.engine.state.value)
        assertEquals(FIRST_CARD, (app.learningFlow.view.value as LearningView.Card).card.id)
        val learning = app.driverSessions.session.value!!.learning
        assertEquals(2, learning.attempt.number)
        assertEquals(0, learning.answeredCount())
        assertEquals(listOf(FIRST_QUESTION), learning.reviewIds)
    }

    /** 나중에 보기를 누르면 정차 대기로 가고, 다음 정차 때 다시 제안한다. */
    @Test
    fun `나중에 보기를 누르면 정차 대기로 가고 다음 정차 때 다시 제안한다`() = runBlocking {
        // 상황
        val app = atResumeOffer()

        // 행동
        app.resumeOffer.later()
        val afterLater = app.engine.state.value
        driveAndStop(app)

        // 결과
        assertSame(ParkedRest, afterLater)
        assertSame(ParkedResumeOffer, app.engine.state.value)
    }

    /** 모든 문항에 답했으면 다음 정차 때 이어 보기를 제안하지 않고 정차 대기로 간다. */
    @Test
    fun `모든 문항에 답했으면 다음 정차 때 이어 보기를 제안하지 않는다`() = runBlocking {
        // 상황
        val app = AdminTestHarness(stores, writeScope, catalog = CORE_CATALOG)
        toFirstQuestion(app)
        LearningTestSteps.CORRECT_CHOICES.forEach { answerAndNext(app, it) }

        // 행동
        driveAndStop(app)

        // 결과
        assertSame(ParkedRest, app.engine.state.value)
    }

    /** 화면 제한 중에는 처음부터 보기를 눌러도 진행이 바뀌지 않고 제안 화면에 남는다. */
    @Test
    fun `화면 제한 중에는 처음부터 보기를 눌러도 진행이 바뀌지 않는다`() = runBlocking {
        // 상황
        val app = atResumeOffer()
        app.signals.ux.change(UxRestriction.RESTRICTED)

        // 행동
        app.resumeOffer.restart()

        // 결과
        assertSame(ParkedResumeOffer, app.engine.state.value)
        val learning = app.driverSessions.session.value!!.learning
        assertEquals(1, learning.attempt.number)
        assertEquals(1, learning.answeredCount())
    }

    /** 제안 화면이 아닐 때(정차 대기) 들어온 처음부터 보기·이어 보기 입력은 무시된다. */
    @Test
    fun `제안 화면이 아닐 때 들어온 처음부터 보기 입력은 무시된다`() = runBlocking {
        // 상황
        val app = atResumeOffer()
        app.resumeOffer.later()

        // 행동
        app.resumeOffer.restart()
        app.resumeOffer.resume()

        // 결과
        assertSame(ParkedRest, app.engine.state.value)
        val learning = app.driverSessions.session.value!!.learning
        assertEquals(1, learning.attempt.number)
        assertTrue(learning.hasUnfinished(CORE_CATALOG.questions.map { it.id }))
    }
}
