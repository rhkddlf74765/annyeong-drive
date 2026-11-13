package com.annyeongdrive.app.learning

import com.annyeongdrive.app.R
import com.annyeongdrive.app.admin.AdminTestHarness
import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.drive.state.DrivingCruising
import com.annyeongdrive.app.drive.state.LearningCard
import com.annyeongdrive.app.drive.state.LearningExplain
import com.annyeongdrive.app.drive.state.LearningQuiz
import com.annyeongdrive.app.drive.state.LearningResult
import com.annyeongdrive.app.drive.state.LearningStart
import com.annyeongdrive.app.drive.state.ParkedResumeOffer
import com.annyeongdrive.app.drive.state.ParkedRest
import com.annyeongdrive.app.gear.Gear
import com.annyeongdrive.app.rental.AppLanguage
import com.annyeongdrive.app.storage.TempDataStores
import com.annyeongdrive.app.vehicle.FuelType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 교육·퀴즈 흐름: 교육 시작 → 학습 카드 → 핵심 퀴즈 → 정답 확인 → 해설 → 다음 → 결과·복습.
 * 예외 상황(답 고르기 전 확인, 확인 후 답 변경, 건너뛰기, 문제 중 출발, 해설 중 재시작, 결과 후 이어 보기,
 * 교육 화면이 아닐 때의 입력)을 함께 확인한다. 실제 DataStore 파일·엔진·Coordinator·1차 콘텐츠를 연결해 확인한다.
 * 근거: 기능 실행안 부록 A·B~D, 결정 문서 3·4장, docs/state-machine.md T25~T30·B2·B6·T9·T15·T19, implementation-plan 6단계.
 */
class LearningFlowTest {

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

    /**
     * 처음 설치한 앱에서 대여 설정을 마치고 교육 시작을 눌러 첫 학습 카드까지 간다.
     *
     * @return 첫 학습 카드에 있는 앱
     */
    private suspend fun atFirstCard(): AdminTestHarness {
        val app = newApp()
        app.startAndSit()
        app.setupFlow.selectLanguage(AppLanguage.ENGLISH)
        app.setupFlow.confirmSeatBelts(true)
        app.setupFlow.completePrecheck()
        app.learningEntry.begin()
        app.learningFlow.sync()
        return app
    }

    /**
     * 첫 문제까지 간다.
     *
     * @return 첫 문제에 있는 앱
     */
    private suspend fun atFirstQuestion(): AdminTestHarness {
        val app = atFirstCard()
        app.learningFlow.startQuiz()
        return app
    }

    /**
     * 지금 문제에 답을 고르고 정답을 확인한 뒤 다음으로 넘어간다.
     *
     * @param app 앱
     * @param choice 고를 선택지
     */
    private suspend fun answerAndNext(app: AdminTestHarness, choice: Int) {
        app.learningFlow.selectAnswer(choice)
        app.learningFlow.checkAnswer()
        app.learningFlow.next()
    }

    /**
     * 지금 보이는 문제의 식별자.
     *
     * @param app 앱
     * @return 문항 식별자
     */
    private fun shownQuestionId(app: AdminTestHarness): String =
        (app.learningFlow.view.value as LearningView.Question).question.id

    /**
     * 출발했다가 다시 선다.
     *
     * @param app 앱
     */
    private fun driveAndStop(app: AdminTestHarness) {
        app.signals.gear.shift(Gear.D)
        app.signals.gear.shift(Gear.P)
    }

    /**
     * 앱을 끈다(파일 쓰기를 기다리고 파일을 닫는다).
     *
     * @param app 끌 앱
     */
    private suspend fun shutDown(app: AdminTestHarness) {
        app.awaitWrites()
        app.engine.stop()
        stores.close()
    }

    /** 교육 시작을 누르면 첫 카드가 보이고 본 카드로 저장된다. */
    @Test
    fun `교육 시작을 누르면 첫 학습 카드가 보이고 본 카드로 저장된다`() = runBlocking {
        // 상황 · 행동
        val app = atFirstCard()

        // 결과
        val view = app.learningFlow.view.value as LearningView.Card
        assertEquals("card.precheck", view.card.id)
        assertEquals(6, view.total)
        assertTrue("card.precheck" in app.driverSessions.session.value!!.learning.viewedCardIds)
    }

    /** 카드를 끝까지 넘긴 뒤 마지막 카드에서 핵심 퀴즈 시작을 누르면 첫 문제로 간다. */
    @Test
    fun `카드를 끝까지 넘기고 핵심 퀴즈 시작을 누르면 첫 문제로 간다`() = runBlocking {
        // 상황
        val app = atFirstCard()
        repeat(5) { app.learningFlow.nextCard() }
        val last = app.learningFlow.view.value as LearningView.Card

        // 행동
        app.learningFlow.startQuiz()

        // 결과
        assertTrue(last.isLast)
        assertSame(LearningQuiz, app.engine.state.value)
        assertEquals("quiz.lane_direction", shownQuestionId(app))
    }

    /** 답을 고르기 전에는 정답 확인을 눌러도 아무 일이 없다. */
    @Test
    fun `답을 고르기 전에는 정답을 확인할 수 없다`() = runBlocking {
        // 상황
        val app = atFirstQuestion()

        // 행동
        app.learningFlow.checkAnswer()

        // 결과
        assertSame(LearningQuiz, app.engine.state.value)
        assertFalse((app.learningFlow.view.value as LearningView.Question).canCheck)
        assertEquals(0, app.driverSessions.session.value!!.learning.answeredCount())
    }

    /** 정답 확인 전에는 고른 답을 바꿀 수 있고, 마지막에 고른 답으로 확인된다. */
    @Test
    fun `정답 확인 전에는 고른 답을 바꿀 수 있다`() = runBlocking {
        // 상황
        val app = atFirstQuestion()
        app.learningFlow.selectAnswer(1)

        // 행동
        app.learningFlow.selectAnswer(0)
        app.learningFlow.checkAnswer()

        // 결과
        assertSame(LearningExplain, app.engine.state.value)
        assertTrue((app.learningFlow.view.value as LearningView.Explanation).correct)
    }

    /** 정답을 확인하면 답이 저장되고 해설로 가며, 그 뒤에는 답을 바꿀 수 없다. */
    @Test
    fun `정답을 확인하면 답이 저장되고 해설로 가며 답을 바꿀 수 없다`() = runBlocking {
        // 상황
        val app = atFirstQuestion()
        app.learningFlow.selectAnswer(1)

        // 행동
        app.learningFlow.checkAnswer()
        app.learningFlow.selectAnswer(0)

        // 결과
        val learning = app.driverSessions.session.value!!.learning
        assertEquals(false, learning.answeredCorrectly("quiz.lane_direction"))
        assertTrue(app.learningFlow.view.value is LearningView.Explanation)
        assertEquals(listOf("quiz.lane_direction"), learning.reviewIds)
    }

    /** 건너뛴 문항은 미완료로 남아 복습 목록에 들어가고 다음 문제로 간다. */
    @Test
    fun `건너뛴 문항은 미완료로 남고 다음 문제로 간다`() = runBlocking {
        // 상황
        val app = atFirstQuestion()

        // 행동
        app.learningFlow.skip()

        // 결과
        val learning = app.driverSessions.session.value!!.learning
        assertSame(LearningQuiz, app.engine.state.value)
        assertEquals("quiz.school_zone_stop", shownQuestionId(app))
        assertEquals(0, learning.answeredCount())
        assertEquals(listOf("quiz.lane_direction"), learning.reviewIds)
    }

    /** 마지막 문항을 건너뛰면 결과·복습으로 간다. */
    @Test
    fun `마지막 문항을 건너뛰면 결과로 간다`() = runBlocking {
        // 상황
        val app = atFirstQuestion()
        listOf(0, 1, 2, 0).forEach { answerAndNext(app, it) }

        // 행동
        app.learningFlow.skip()

        // 결과
        assertSame(LearningResult, app.engine.state.value)
        assertEquals(4, (app.learningFlow.view.value as LearningView.Result).answered)
    }

    /** 다섯 문항에 모두 답하면 결과로 가고, 완료 수는 응답한 문항 수다(정답 수가 아니다). */
    @Test
    fun `모든 문항에 답하면 결과로 가고 완료 수는 응답한 문항 수다`() = runBlocking {
        // 상황
        val app = atFirstQuestion()

        // 행동: 두 문항은 틀리게 답한다
        listOf(1, 1, 2, 2, 3).forEach { answerAndNext(app, it) }

        // 결과
        val result = app.learningFlow.view.value as LearningView.Result
        assertSame(LearningResult, app.engine.state.value)
        assertEquals(5, result.answered)
        assertEquals(5, result.total)
    }

    /** 틀린 문항과 건너뛴 문항의 관련 카드가 다시 볼 규칙으로 겹치지 않게 나온다. */
    @Test
    fun `틀리거나 건너뛴 문항의 규칙이 다시 볼 규칙으로 나온다`() = runBlocking {
        // 상황
        val app = atFirstQuestion()
        answerAndNext(app, 1) // Q1 틀림 → 통행 방향
        app.learningFlow.skip() // Q2 건너뜀 → 보호구역
        answerAndNext(app, 0) // Q3 틀림 → 교차로

        // 행동
        answerAndNext(app, 1) // Q4 틀림 → 교차로(겹침)
        answerAndNext(app, 3) // Q5 정답

        // 결과
        val review = (app.learningFlow.view.value as LearningView.Result).reviewCards.map { it.id }
        assertEquals(listOf("card.lane_direction", "card.protected_zone", "card.intersection"), review)
    }

    /** 문제 중 출발하면 교육이 닫히고, 정차해서 이어 보기를 누르면 멈춘 문제로 돌아간다. */
    @Test
    fun `문제 중 출발했다가 정차해서 이어 보기를 누르면 멈춘 문제로 돌아간다`() = runBlocking {
        // 상황
        val app = atFirstQuestion()
        answerAndNext(app, 0)
        app.signals.gear.shift(Gear.D)
        val whileDriving = app.engine.state.value
        app.learningFlow.sync()

        // 행동
        app.signals.gear.shift(Gear.P)
        val offered = app.engine.state.value
        app.engine.send(DriveEvent.ResumeAccepted)
        app.learningFlow.sync()

        // 결과
        assertSame(DrivingCruising, whileDriving)
        assertSame(ParkedResumeOffer, offered)
        assertSame(LearningQuiz, app.engine.state.value)
        assertEquals("quiz.school_zone_stop", shownQuestionId(app))
    }

    /** 해설을 보던 중 앱이 다시 켜져도 답은 남아 있고, 이어 보기를 누르면 다음 미응답 문항으로 간다. */
    @Test
    fun `해설 중 앱을 다시 켜도 답은 남고 이어 보기로 다음 문항을 본다`() = runBlocking {
        // 상황
        val before = atFirstQuestion()
        before.learningFlow.selectAnswer(0)
        before.learningFlow.checkAnswer()
        shutDown(before)
        val after = newApp()
        after.startAndSit()

        // 행동
        after.engine.send(DriveEvent.ResumeAccepted)
        after.learningFlow.sync()

        // 결과
        assertEquals(true, after.driverSessions.session.value!!.learning.answeredCorrectly("quiz.lane_direction"))
        assertEquals("quiz.school_zone_stop", shownQuestionId(after))
    }

    /** 결과까지 간 뒤에도 건너뛴 문항이 있으면 다음 정차 때 이어 보기로 그 문항을 다시 볼 수 있고, 답하면 결과로 간다. */
    @Test
    fun `결과 후 이어 보기로 건너뛴 문항에 답하면 다시 결과로 간다`() = runBlocking {
        // 상황
        val app = atFirstQuestion()
        answerAndNext(app, 0)
        app.learningFlow.skip()
        listOf(2, 0, 3).forEach { answerAndNext(app, it) }
        app.learningFlow.close()
        driveAndStop(app)
        app.engine.send(DriveEvent.ResumeAccepted)
        app.learningFlow.sync()
        val resumedAt = shownQuestionId(app)

        // 행동
        answerAndNext(app, 1)

        // 결과
        assertEquals("quiz.school_zone_stop", resumedAt)
        assertSame(LearningResult, app.engine.state.value)
        assertEquals(5, (app.learningFlow.view.value as LearningView.Result).answered)
    }

    /** 모든 문항에 답한 뒤 다시 핵심 퀴즈를 시작하면 새 회차로 첫 문제부터 내고, 복습 목록은 남긴다. */
    @Test
    fun `모든 문항에 답한 뒤 다시 퀴즈를 시작하면 처음부터 내고 복습 목록은 남는다`() = runBlocking {
        // 상황
        val app = atFirstQuestion()
        listOf(1, 1, 2, 0, 3).forEach { answerAndNext(app, it) }
        app.learningFlow.close()
        app.engine.send(DriveEvent.LearningOpenRequested)
        app.learningEntry.begin()
        app.learningFlow.sync()

        // 행동
        app.learningFlow.startQuiz()

        // 결과
        val learning = app.driverSessions.session.value!!.learning
        assertEquals("quiz.lane_direction", shownQuestionId(app))
        assertEquals(0, learning.answeredCount())
        assertEquals(listOf("quiz.lane_direction"), learning.reviewIds)
    }

    /** 업체가 유종을 전기로 정하면 첫 카드가 충전 안내 변형으로 보인다. */
    @Test
    fun `업체가 유종을 전기로 정했으면 첫 카드가 충전 안내 변형으로 보인다`() = runBlocking {
        // 상황
        val app = newApp()
        app.vendorConfigs.load()
        app.vendorConfigs.update {
            it.withVehicle(it.vehicle.copy(fuelType = FuelType.ELECTRIC))
        }
        app.startAndSit()
        app.setupFlow.selectLanguage(AppLanguage.ENGLISH)
        app.setupFlow.confirmSeatBelts(true)
        app.setupFlow.completePrecheck()

        // 행동
        app.learningEntry.begin()
        app.learningFlow.sync()

        // 결과
        val card = (app.learningFlow.view.value as LearningView.Card).card
        assertEquals(R.string.learn_card_precheck_body_ev, card.texts.body.res)
    }

    /** 교육 화면이 아닐 때 들어온 퀴즈 시작·답·확인 입력은 진행도 상태도 바꾸지 않는다. */
    @Test
    fun `교육 화면이 아닐 때 들어온 퀴즈 입력은 무시된다`() = runBlocking {
        // 상황
        val app = atFirstQuestion()
        app.learningFlow.close()

        // 행동
        app.learningFlow.startQuiz()
        app.learningFlow.selectAnswer(0)
        app.learningFlow.checkAnswer()
        app.learningFlow.skip()

        // 결과
        assertSame(ParkedRest, app.engine.state.value)
        assertEquals(0, app.driverSessions.session.value!!.learning.answeredCount())
        assertTrue(app.driverSessions.session.value!!.learning.reviewIds.isEmpty())
    }

    /** 교육 시작 화면에서 바로 핵심 퀴즈 시작은 할 수 없다(학습 카드를 거친다). */
    @Test
    fun `교육 시작 화면에서는 핵심 퀴즈를 시작할 수 없다`() = runBlocking {
        // 상황
        val app = newApp()
        app.startAndSit()
        app.setupFlow.selectLanguage(AppLanguage.ENGLISH)
        app.setupFlow.confirmSeatBelts(true)
        app.setupFlow.completePrecheck()

        // 행동
        app.learningFlow.startQuiz()

        // 결과
        assertSame(LearningStart, app.engine.state.value)
    }

    /** 카드 화면에서 출발하면 교육이 닫히고 다음 정차 때 이어 보기로 보던 카드로 돌아간다. */
    @Test
    fun `카드를 보다 출발했다가 이어 보기를 누르면 보던 카드로 돌아간다`() = runBlocking {
        // 상황
        val app = atFirstCard()
        repeat(2) { app.learningFlow.nextCard() }
        driveAndStop(app)
        app.learningFlow.sync()

        // 행동
        app.engine.send(DriveEvent.ResumeAccepted)
        app.learningFlow.sync()

        // 결과
        assertSame(LearningCard, app.engine.state.value)
        assertEquals("card.protected_zone", (app.learningFlow.view.value as LearningView.Card).card.id)
    }
}
