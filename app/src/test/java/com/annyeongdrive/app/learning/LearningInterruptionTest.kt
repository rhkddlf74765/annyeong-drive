package com.annyeongdrive.app.learning

import com.annyeongdrive.app.admin.AdminTestHarness
import com.annyeongdrive.app.drive.state.DrivingCruising
import com.annyeongdrive.app.drive.state.LearningExplain
import com.annyeongdrive.app.drive.state.LearningResult
import com.annyeongdrive.app.drive.state.ParkedRest
import com.annyeongdrive.app.gear.Gear
import com.annyeongdrive.app.learning.LearningTestSteps.FIRST_CARD
import com.annyeongdrive.app.learning.LearningTestSteps.FIRST_QUESTION
import com.annyeongdrive.app.learning.LearningTestSteps.answerAndNext
import com.annyeongdrive.app.learning.LearningTestSteps.toFirstCard
import com.annyeongdrive.app.learning.LearningTestSteps.toFirstQuestion
import com.annyeongdrive.app.rental.LearningScreen
import com.annyeongdrive.app.storage.TempDataStores
import com.annyeongdrive.app.vehicle.UxRestriction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 교육 중단: 교육·퀴즈의 어느 화면에서든 주행 진입(기어 D/R, 차속 발생)이나 화면 제한이 생기면
 * 교육 화면 닫기와 학습 음성 중단을 한 번에 실행하고, 진행은 그대로 남긴다.
 * 실제 DataStore 파일·엔진·Coordinator·1차 콘텐츠를 연결해 확인한다.
 * 근거: CLAUDE.md 아키텍처("주행 진입이 생기면 교육 화면 닫기와 학습 음성 중단을 한 번에"), 결정 문서 3장,
 * docs/state-machine.md T9·B7, implementation-plan 7단계.
 */
class LearningInterruptionTest {

    private val stores = TempDataStores()
    private val writeScope = CoroutineScope(SupervisorJob())

    /** 파일 쓰기 범위를 닫고 임시 파일을 지운다. */
    @After
    fun tearDown() {
        writeScope.cancel()
        stores.delete()
    }

    /**
     * 1차 콘텐츠를 쓰는 앱을 만든다.
     *
     * @return 앱
     */
    private fun newApp(): AdminTestHarness = AdminTestHarness(stores, writeScope, catalog = CORE_CATALOG)

    /** 학습 카드를 보다 D로 바꾸면 화면을 닫고 음성을 멈추며, 화면 흐름이 비워진다. */
    @Test
    fun `카드를 보다 D로 바꾸면 교육 화면을 닫고 학습 음성을 멈춘다`() = runBlocking {
        // 상황
        val app = newApp()
        toFirstCard(app)

        // 행동
        app.signals.gear.shift(Gear.D)
        app.learningFlow.sync()

        // 결과
        assertSame(DrivingCruising, app.engine.state.value)
        assertEquals(1, app.ports.screenClosed)
        assertEquals(1, app.ports.speechStopped)
        assertSame(LearningView.Hidden, app.learningFlow.view.value)
        assertEquals(FIRST_CARD, app.driverSessions.session.value!!.learning.attempt.position?.itemId)
    }

    /** 해설을 보다 D로 바꾸면 닫히고, 확인한 답은 남는다. */
    @Test
    fun `해설을 보다 D로 바꾸면 닫히고 확인한 답은 남는다`() = runBlocking {
        // 상황
        val app = newApp()
        toFirstQuestion(app)
        app.learningFlow.selectAnswer(0)
        app.learningFlow.checkAnswer()
        val before = app.engine.state.value

        // 행동
        app.signals.gear.shift(Gear.D)
        app.learningFlow.sync()

        // 결과
        assertSame(LearningExplain, before)
        assertSame(DrivingCruising, app.engine.state.value)
        assertEquals(1, app.ports.screenClosed)
        assertEquals(1, app.ports.speechStopped)
        assertEquals(true, app.driverSessions.session.value!!.learning.answeredCorrectly(FIRST_QUESTION))
    }

    /** 결과 화면을 보다 기어 신호 없이 차속만 생겨도 닫힌다. */
    @Test
    fun `결과를 보다 차속만 생겨도 닫힌다`() = runBlocking {
        // 상황
        val app = newApp()
        toFirstQuestion(app)
        LearningTestSteps.CORRECT_CHOICES.forEach { answerAndNext(app, it) }
        val before = app.engine.state.value

        // 행동
        app.signals.speed.changeSpeed(3f)
        app.learningFlow.sync()

        // 결과
        assertSame(LearningResult, before)
        assertSame(DrivingCruising, app.engine.state.value)
        assertEquals(1, app.ports.screenClosed)
        assertEquals(1, app.ports.speechStopped)
        assertSame(LearningView.Hidden, app.learningFlow.view.value)
    }

    /** 정차 중이라도 문제를 보다 화면 제한이 걸리면 닫고 정차 대기로 가며, 보던 문제 위치는 남는다. */
    @Test
    fun `문제를 보다 화면 제한이 걸리면 닫고 정차 대기로 간다`() = runBlocking {
        // 상황
        val app = newApp()
        toFirstQuestion(app)

        // 행동
        app.signals.ux.change(UxRestriction.RESTRICTED)
        app.learningFlow.sync()

        // 결과
        assertSame(ParkedRest, app.engine.state.value)
        assertEquals(1, app.ports.screenClosed)
        assertEquals(1, app.ports.speechStopped)
        val position = app.driverSessions.session.value!!.learning.attempt.position
        assertEquals(LearningScreen.QUIZ, position?.screen)
        assertEquals(FIRST_QUESTION, position?.itemId)
    }

    /** 화면 제한이 풀려도 교육이 저절로 다시 열리지 않는다. 다시 열려면 사용자가 연다. */
    @Test
    fun `화면 제한이 풀려도 교육이 저절로 다시 열리지 않는다`() = runBlocking {
        // 상황
        val app = newApp()
        toFirstQuestion(app)
        app.signals.ux.change(UxRestriction.RESTRICTED)

        // 행동
        app.signals.ux.change(UxRestriction.NONE)
        app.learningFlow.sync()

        // 결과
        assertSame(ParkedRest, app.engine.state.value)
        assertEquals(1, app.ports.screenClosed)
        assertSame(LearningView.Hidden, app.learningFlow.view.value)
    }
}
