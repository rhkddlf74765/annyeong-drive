package com.annyeongdrive.app.learning

import com.annyeongdrive.app.admin.AdminTestHarness
import com.annyeongdrive.app.drive.state.LearningCard
import com.annyeongdrive.app.drive.state.LearningStart
import com.annyeongdrive.app.drive.state.ParkedRest
import com.annyeongdrive.app.drive.state.SetupPrecheck
import com.annyeongdrive.app.gear.Gear
import com.annyeongdrive.app.rental.AppLanguage
import com.annyeongdrive.app.storage.TempDataStores
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 교육 시작 화면 흐름: 점검을 마치면 열리고, "교육 시작"이나 "나중에 보기"로 나간다. 교육을 시작하지 않으면 다음 정차 때 다시 열린다.
 * 학습 카드·퀴즈 내용은 6단계에서 확인한다.
 * 근거: 기능 실행안 3번, docs/state-machine.md B1·T23·T25·T9, implementation-plan 5단계, 11/13 결정(교육 시작 화면만 먼저).
 */
class LearningEntryFlowTest {

    private val stores = TempDataStores()
    private val writeScope = CoroutineScope(SupervisorJob())

    /** 파일 쓰기 범위를 닫고 임시 파일을 지운다. */
    @After
    fun tearDown() {
        writeScope.cancel()
        stores.delete()
    }

    /**
     * 처음 설치한 앱에서 언어를 고르고 점검을 마쳐 교육 시작 화면까지 간다.
     *
     * @return 교육 시작 화면에 있는 앱
     */
    private suspend fun atLearningStart(): AdminTestHarness {
        val app = AdminTestHarness(stores, writeScope)
        app.startAndSit()
        app.setupFlow.selectLanguage(AppLanguage.ENGLISH)
        app.setupFlow.confirmSeatBelts(true)
        app.setupFlow.completePrecheck()
        assertSame(LearningStart, app.engine.state.value)
        return app
    }

    /**
     * D로 출발했다가 P로 다시 선다.
     *
     * @param app 앱
     */
    private fun driveAndStop(app: AdminTestHarness) {
        app.signals.gear.shift(Gear.D)
        app.signals.gear.shift(Gear.P)
    }

    /** "교육 시작"을 누르면 시작 기록이 저장되고 학습 카드로 간다. */
    @Test
    fun `교육 시작을 누르면 시작 기록이 저장되고 학습 카드로 간다`() = runBlocking {
        // 상황
        val app = atLearningStart()

        // 행동
        app.learningEntry.begin()

        // 결과
        assertTrue(app.driverSessions.session.value!!.learning.begun)
        assertSame(LearningCard, app.engine.state.value)
    }

    /** "나중에 보기"를 누르면 정차 대기로 가고 교육 시작 기록은 남지 않는다. */
    @Test
    fun `나중에 보기를 누르면 정차 대기로 간다`() = runBlocking {
        // 상황
        val app = atLearningStart()

        // 행동
        app.learningEntry.later()

        // 결과
        assertSame(ParkedRest, app.engine.state.value)
        assertFalse(app.driverSessions.session.value!!.learning.begun)
    }

    /** 나중에 보기를 누른 뒤 주행하고 다시 서면 교육 시작 화면이 다시 열린다. */
    @Test
    fun `나중에 보기 후 다음 정차 때 교육 시작 화면이 다시 열린다`() = runBlocking {
        // 상황
        val app = atLearningStart()
        app.learningEntry.later()

        // 행동
        driveAndStop(app)

        // 결과
        assertSame(LearningStart, app.engine.state.value)
    }

    /** 교육 시작 화면에서 출발하면 교육 화면이 닫히고, 다음 정차 때 다시 열린다. */
    @Test
    fun `교육 시작 화면에서 출발하면 닫히고 다음 정차 때 다시 열린다`() = runBlocking {
        // 상황
        val app = atLearningStart()

        // 행동
        app.signals.gear.shift(Gear.D)
        val whileDriving = app.engine.state.value
        app.signals.gear.shift(Gear.P)

        // 결과
        assertNotSame(LearningStart, whileDriving)
        assertSame(LearningStart, app.engine.state.value)
    }

    /** 교육 시작 화면이 아닐 때 들어온 "교육 시작"은 기록도 상태도 바꾸지 않는다. */
    @Test
    fun `교육 시작 화면이 아닐 때 누른 교육 시작은 무시된다`() = runBlocking {
        // 상황
        val app = AdminTestHarness(stores, writeScope)
        app.startAndSit()
        app.setupFlow.selectLanguage(AppLanguage.ENGLISH)

        // 행동
        app.learningEntry.begin()

        // 결과
        assertSame(SetupPrecheck, app.engine.state.value)
        assertFalse(app.driverSessions.session.value!!.learning.begun)
    }
}
