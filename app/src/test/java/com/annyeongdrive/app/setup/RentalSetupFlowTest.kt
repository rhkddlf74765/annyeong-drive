package com.annyeongdrive.app.setup

import com.annyeongdrive.app.admin.AdminTestHarness
import com.annyeongdrive.app.admin.AdminTestHarness.Companion.NEW_RENTAL_ID
import com.annyeongdrive.app.drive.state.LearningStart
import com.annyeongdrive.app.drive.state.SetupLanguage
import com.annyeongdrive.app.drive.state.SetupPrecheck
import com.annyeongdrive.app.gear.Gear
import com.annyeongdrive.app.rental.AppLanguage
import com.annyeongdrive.app.storage.TempDataStores
import com.annyeongdrive.app.vehicle.Seat
import com.annyeongdrive.app.vehicle.SeatBeltStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/**
 * 대여 시작 흐름: 착좌 → 언어 선택 → 출발 전 점검 → 교육 시작 화면. 예외 상황(수동 확인 없이 마침, 점검 도중 재시작,
 * 점검 화면에서 출발, 지난 화면의 입력)을 함께 확인한다. 실제 DataStore 파일·엔진·Coordinator를 연결해 확인한다.
 * 근거: 기능 실행안 1·2번, 결정 문서 7장, docs/state-machine.md T2·T6·T7·T8·B1·T3, implementation-plan 5단계, 11/13 결정.
 */
class RentalSetupFlowTest {

    private val stores = TempDataStores()
    private val writeScope = CoroutineScope(SupervisorJob())

    /** 파일 쓰기 범위를 닫고 임시 파일을 지운다. */
    @After
    fun tearDown() {
        writeScope.cancel()
        stores.delete()
    }

    /**
     * 처음 설치한 앱을 켜고 운전석에 앉아 언어 선택 화면까지 간다.
     *
     * @return 언어 선택 화면에 있는 앱
     */
    private suspend fun atLanguage(): AdminTestHarness {
        val app = AdminTestHarness(stores, writeScope)
        app.startAndSit()
        return app
    }

    /**
     * 처음 설치한 앱에서 영어를 골라 출발 전 점검 화면까지 간다.
     *
     * @return 출발 전 점검 화면에 있는 앱
     */
    private suspend fun atPrecheck(): AdminTestHarness {
        val app = atLanguage()
        app.setupFlow.selectLanguage(AppLanguage.ENGLISH)
        return app
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

    /** 대여가 없는 앱에 앉으면 언어 선택이 열린다. */
    @Test
    fun `대여가 없는 앱에서 운전석에 앉으면 언어 선택이 열린다`() = runBlocking {
        // 상황
        val app = AdminTestHarness(stores, writeScope)

        // 행동
        app.startAndSit()

        // 결과
        assertSame(SetupLanguage, app.engine.state.value)
    }

    /** 언어를 고르면 대여가 저장되고 출발 전 점검으로 간다. */
    @Test
    fun `언어를 고르면 대여가 저장되고 출발 전 점검으로 간다`() = runBlocking {
        // 상황
        val app = atLanguage()

        // 행동
        app.setupFlow.selectLanguage(AppLanguage.ENGLISH)

        // 결과
        val session = app.driverSessions.session.value!!
        assertEquals(NEW_RENTAL_ID, session.rentalId)
        assertEquals(AppLanguage.ENGLISH, session.language)
        assertFalse(session.precheckCompleted)
        assertSame(SetupPrecheck, app.engine.state.value)
    }

    /** 안전띠를 직접 확인하고 점검을 마치면 점검 완료가 저장되고 교육 시작 화면이 열린다. */
    @Test
    fun `안전띠를 확인하고 점검을 마치면 점검 완료가 저장되고 교육 시작 화면이 열린다`() = runBlocking {
        // 상황
        val app = atPrecheck()

        // 행동
        app.setupFlow.confirmSeatBelts(true)
        app.setupFlow.completePrecheck()

        // 결과
        assertTrue(app.driverSessions.session.value!!.precheckCompleted)
        assertSame(LearningStart, app.engine.state.value)
    }

    /** 차량 안전띠 경고가 하나도 없어도 수동 확인 없이는 점검을 마칠 수 없고 확인 안내가 나온다. */
    @Test
    fun `안전띠 경고가 없어도 수동 확인 없이는 점검을 마칠 수 없다`() = runBlocking {
        // 상황
        val app = atPrecheck()
        Seat.entries.forEach {
            app.signals.seatBelts.change(it, SeatBeltStatus.NO_WARNING)
        }

        // 행동
        app.setupFlow.completePrecheck()

        // 결과
        assertSame(SetupPrecheck, app.engine.state.value)
        assertFalse(app.driverSessions.session.value!!.precheckCompleted)
        assertTrue(app.setupFlow.precheck.value.needsSeatBeltConfirmation)
    }

    /** 체크했다가 다시 풀면 점검을 마칠 수 없다. */
    @Test
    fun `안전띠 체크를 다시 풀면 점검을 마칠 수 없다`() = runBlocking {
        // 상황
        val app = atPrecheck()
        app.setupFlow.confirmSeatBelts(true)

        // 행동
        app.setupFlow.confirmSeatBelts(false)
        app.setupFlow.completePrecheck()

        // 결과
        assertSame(SetupPrecheck, app.engine.state.value)
        assertFalse(app.driverSessions.session.value!!.precheckCompleted)
    }

    /** 점검을 마친 뒤 앱을 다시 켜고 앉으면 언어 선택 없이 정차로 가고, 교육을 시작하지 않았으므로 교육 시작이 열린다. */
    @Test
    fun `점검을 마친 뒤 앱을 다시 켜고 앉으면 언어 선택 없이 교육 시작이 열린다`() = runBlocking {
        // 상황
        val before = atPrecheck()
        before.setupFlow.confirmSeatBelts(true)
        before.setupFlow.completePrecheck()
        shutDown(before)

        // 행동
        val after = AdminTestHarness(stores, writeScope)
        after.startAndSit()

        // 결과
        assertSame(LearningStart, after.engine.state.value)
        assertEquals(NEW_RENTAL_ID, after.driverSessions.session.value!!.rentalId)
    }

    /** 점검 도중 앱을 다시 켜면 언어 선택부터 다시 하고, 다시 골라도 대여는 처음 것 하나만 남고 언어만 바뀐다. */
    @Test
    fun `점검 도중 앱을 다시 켜면 언어 선택부터 다시 하고 대여는 하나만 남는다`() = runBlocking {
        // 상황
        val before = atPrecheck()
        val firstStartedAt = before.driverSessions.session.value!!.startedAt
        shutDown(before)
        val after = AdminTestHarness(stores, writeScope)
        after.clock.advance(Duration.ofMinutes(10))
        after.startAndSit()
        assertSame(SetupLanguage, after.engine.state.value)

        // 행동
        after.setupFlow.selectLanguage(AppLanguage.KOREAN)

        // 결과
        val session = after.driverSessions.session.value!!
        assertEquals(NEW_RENTAL_ID, session.rentalId)
        assertEquals(firstStartedAt, session.startedAt)
        assertEquals(AppLanguage.KOREAN, session.language)
        assertSame(SetupPrecheck, after.engine.state.value)
    }

    /** 점검 화면에서 D로 출발해도 대여 설정 상태는 그대로이고, 다시 P로 서면 이어서 점검을 마칠 수 있다. */
    @Test
    fun `점검 화면에서 출발해도 대여 설정 상태는 그대로이고 정차하면 이어서 점검한다`() = runBlocking {
        // 상황
        val app = atPrecheck()
        app.setupFlow.confirmSeatBelts(true)

        // 행동
        app.signals.gear.shift(Gear.D)
        val whileDriving = app.engine.state.value
        app.signals.gear.shift(Gear.P)
        app.setupFlow.completePrecheck()

        // 결과
        assertSame(SetupPrecheck, whileDriving)
        assertSame(LearningStart, app.engine.state.value)
    }

    /** 점검 화면에 늦게 도착한 지난 언어 선택 입력은 대여를 바꾸지 않는다. */
    @Test
    fun `점검 화면에서 들어온 언어 선택 입력은 무시된다`() = runBlocking {
        // 상황
        val app = atPrecheck()

        // 행동
        app.setupFlow.selectLanguage(AppLanguage.KOREAN)

        // 결과
        assertEquals(AppLanguage.ENGLISH, app.driverSessions.session.value!!.language)
        assertSame(SetupPrecheck, app.engine.state.value)
    }

    /** 언어 선택 화면에서 들어온 점검 마침 입력은 무시되고 대여도 만들지 않는다. */
    @Test
    fun `언어 선택 화면에서 들어온 점검 입력은 무시된다`() = runBlocking {
        // 상황
        val app = atLanguage()

        // 행동
        app.setupFlow.confirmSeatBelts(true)
        app.setupFlow.completePrecheck()

        // 결과
        assertNull(app.driverSessions.session.value)
        assertSame(SetupLanguage, app.engine.state.value)
    }

    /** 언어를 다시 고르면 지난번 점검 화면의 안전띠 체크가 남지 않는다. */
    @Test
    fun `점검 도중 재시작 후 다시 들어온 점검 화면은 안전띠 체크가 비어 있다`() = runBlocking {
        // 상황
        val before = atPrecheck()
        before.setupFlow.confirmSeatBelts(true)
        shutDown(before)
        val after = AdminTestHarness(stores, writeScope)
        after.startAndSit()

        // 행동
        after.setupFlow.selectLanguage(AppLanguage.ENGLISH)
        after.setupFlow.completePrecheck()

        // 결과
        assertFalse(after.setupFlow.precheck.value.seatBeltConfirmed)
        assertSame(SetupPrecheck, after.engine.state.value)
    }
}
