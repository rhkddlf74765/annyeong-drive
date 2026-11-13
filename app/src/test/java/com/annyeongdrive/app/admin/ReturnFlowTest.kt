package com.annyeongdrive.app.admin

import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.drive.state.DrivingCruising
import com.annyeongdrive.app.drive.state.Idle
import com.annyeongdrive.app.drive.state.ParkedRest
import com.annyeongdrive.app.drive.state.Returning
import com.annyeongdrive.app.drive.state.SetupLanguage
import com.annyeongdrive.app.gear.Gear
import com.annyeongdrive.app.storage.StorageFixtures.ADMIN_CODE
import com.annyeongdrive.app.storage.StorageFixtures.NOW
import com.annyeongdrive.app.storage.TempDataStores
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/**
 * 반납 처리 흐름: 정차 중 반납 완료 → 관리자 코드 → 운전자 데이터 초기화 → 대기. 예외 상황(잠금, 재시작, 입력 중 출발,
 * 늦게 온 확인 결과, 취소, 코드 미설정, 업체 설정 파일 손상, 지난 화면의 입력)을 함께 확인한다.
 * 실제 DataStore 파일·엔진·Coordinator를 연결해 확인한다.
 * 근거: 결정 문서 5장, docs/state-machine.md T31·B8·T32·T5·B3·B4·T2, implementation-plan 4단계.
 */
class ReturnFlowTest {

    private val stores = TempDataStores()
    private val writeScope = CoroutineScope(SupervisorJob())

    /** 파일 쓰기 범위를 닫고 임시 파일을 지운다. */
    @After
    fun tearDown() {
        writeScope.cancel()
        stores.delete()
    }

    /**
     * 대여와 관리자 코드가 저장된 앱을 켜고 운전석에 앉아 반납 처리 화면까지 간다.
     *
     * @param adminCode 지난 실행에서 정해 둔 관리자 코드. 정하지 않았으면 null
     * @return 반납 처리 화면에 있는 앱
     */
    private suspend fun atReturning(adminCode: String? = ADMIN_CODE): AdminTestHarness {
        AdminTestHarness.seedPreviousRun(stores, adminCode = adminCode)
        val app = AdminTestHarness(stores, writeScope)
        app.startAndSit()
        app.engine.send(DriveEvent.ReturnRequested)
        app.returnFlow.open()
        return app
    }

    /**
     * 같은 저장 파일로 앱을 다시 켠다. 이어서 끝나는 파일 쓰기를 기다리고 파일을 닫은 뒤 새로 조립한다.
     *
     * @param app 지금 앱
     * @return 다시 켠 앱(아직 시작 전)
     */
    private suspend fun restart(app: AdminTestHarness): AdminTestHarness {
        app.awaitWrites()
        stores.close()
        return AdminTestHarness(stores, writeScope, app.clock)
    }

    /** 맞는 코드를 넣으면 운전자 데이터를 지우고 대기로 가며 업체 설정은 남는지 확인한다(T31 → B8 → T32 → T5). */
    @Test
    fun `정차 중 반납 완료 후 맞는 관리자 코드를 넣으면 운전자 데이터를 지우고 대기로 간다`() = runBlocking {
        // 상황
        val app = atReturning()
        // 행동
        app.returnFlow.submit(ADMIN_CODE)
        // 결과
        assertSame(Idle, app.engine.state.value)
        assertNull(app.driverSessions.session.value)
        assertTrue(app.vendorConfigs.config.value.hasAdminCode())
    }

    /** 틀린 코드를 5번 넣으면 잠기고, 잠긴 동안에는 맞는 코드를 넣어도 반납되지 않는지 확인한다. */
    @Test
    fun `틀린 코드를 5번 넣으면 잠기고 잠긴 동안에는 맞는 코드도 반납되지 않는다`() = runBlocking {
        // 상황
        val app = atReturning()
        repeat(5) { app.returnFlow.submit(WRONG) }
        // 행동
        app.returnFlow.submit(ADMIN_CODE)
        // 결과
        assertEquals(ReturnScreenState.Locked(NOW.plusSeconds(30)), app.returnFlow.screen.value)
        assertSame(Returning, app.engine.state.value)
        assertNotNull(app.driverSessions.session.value)
    }

    /** 잠금 시간(30초)이 지나면 맞는 코드로 반납되는지 확인한다. */
    @Test
    fun `잠금 시간이 지나면 맞는 코드로 반납된다`() = runBlocking {
        // 상황
        val app = atReturning()
        repeat(5) { app.returnFlow.submit(WRONG) }
        app.clock.advance(Duration.ofSeconds(30))
        // 행동
        app.returnFlow.submit(ADMIN_CODE)
        // 결과
        assertSame(Idle, app.engine.state.value)
    }

    /** 잠긴 채 앱을 껐다 켜도 잠금이 풀리지 않는지 확인한다(껐다 켜서 잠금을 피할 수 없음). */
    @Test
    fun `잠긴 채 앱을 다시 켜도 잠금이 유지된다`() = runBlocking {
        // 상황
        val first = atReturning()
        repeat(5) { first.returnFlow.submit(WRONG) }
        val app = restart(first)
        app.startAndSit()
        app.engine.send(DriveEvent.ReturnRequested)
        app.returnFlow.open()
        // 행동
        app.returnFlow.submit(ADMIN_CODE)
        // 결과
        assertEquals(ReturnScreenState.Locked(NOW.plusSeconds(30)), app.returnFlow.screen.value)
        assertSame(Returning, app.engine.state.value)
    }

    /** 코드를 입력하던 중 출발하면 운전자 데이터는 그대로이고 주행으로 가는지 확인한다(B4). */
    @Test
    fun `코드를 입력하던 중 출발하면 운전자 데이터는 그대로이고 주행으로 간다`() = runBlocking {
        // 상황
        val app = atReturning()
        // 행동
        app.signals.gear.shift(Gear.D)
        // 결과
        assertSame(DrivingCruising, app.engine.state.value)
        assertNotNull(app.driverSessions.session.value)
    }

    /** 코드를 확인하는 사이에 출발했다면, 늦게 온 확인 결과는 Coordinator가 무시하고 데이터가 남는지 확인한다. */
    @Test
    fun `코드를 확인하는 사이 출발하면 늦게 온 확인 결과는 무시된다`() = runBlocking {
        // 상황
        val app = atReturning()
        val submit = launch { app.returnFlow.submit(ADMIN_CODE) }
        yield()
        // 행동
        app.signals.gear.shift(Gear.D)
        submit.join()
        // 결과
        assertSame(DrivingCruising, app.engine.state.value)
        assertNotNull(app.driverSessions.session.value)
    }

    /** 반납을 취소하면 정차 대기로 돌아가고 데이터는 그대로인지 확인한다(B3). */
    @Test
    fun `반납을 취소하면 정차 대기로 돌아가고 데이터는 그대로다`() = runBlocking {
        // 상황
        val app = atReturning()
        // 행동
        app.returnFlow.cancel()
        // 결과
        assertSame(ParkedRest, app.engine.state.value)
        assertNotNull(app.driverSessions.session.value)
    }

    /** 관리자 코드를 정하지 않았으면 안내만 보이고 반납되지 않는지 확인한다(결정 4). */
    @Test
    fun `관리자 코드를 정하지 않았으면 안내만 보이고 반납되지 않는다`() = runBlocking {
        // 상황
        val app = atReturning(adminCode = null)
        // 행동
        app.returnFlow.submit("Anything1")
        // 결과
        assertEquals(ReturnScreenState.AdminCodeNotSet, app.returnFlow.screen.value)
        assertSame(Returning, app.engine.state.value)
        assertNotNull(app.driverSessions.session.value)
    }

    /** 업체 설정 파일을 읽지 못하면 문의 안내만 보이고 반납되지 않으며 파일도 덮어쓰지 않는지 확인한다. */
    @Test
    fun `업체 설정 파일을 읽지 못하면 문의 안내만 보이고 반납되지 않는다`() = runBlocking {
        // 상황
        AdminTestHarness.seedPreviousRun(stores)
        stores.fileOf(AdminTestHarness.VENDOR).writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        val app = AdminTestHarness(stores, writeScope)
        app.startAndSit()
        app.engine.send(DriveEvent.ReturnRequested)
        // 행동
        app.returnFlow.open()
        app.returnFlow.submit(ADMIN_CODE)
        // 결과
        assertEquals(ReturnScreenState.ConfigUnreadable, app.returnFlow.screen.value)
        assertSame(Returning, app.engine.state.value)
        assertEquals(5, stores.fileOf(AdminTestHarness.VENDOR).length())
    }

    /** 반납 처리 화면이 아닐 때 보낸 코드는 확인하지도, 실패로 세지도 않는지 확인한다(지난 화면의 입력). */
    @Test
    fun `반납 처리 상태가 아닐 때 보낸 코드는 무시된다`() = runBlocking {
        // 상황
        val app = atReturning()
        app.returnFlow.cancel()
        // 행동
        app.returnFlow.submit(WRONG)
        // 결과
        assertSame(ParkedRest, app.engine.state.value)
        assertEquals(0, app.vendorConfigs.config.value.credential?.failedAttempts)
    }

    /** 반납 뒤 운전자가 내리고 다음 손님이 앉으면 새 대여 설정(언어 선택)부터 시작하는지 확인한다(T5 → 대기 → T2). */
    @Test
    fun `반납 뒤 다음 손님이 앉으면 언어 선택부터 시작한다`() = runBlocking {
        // 상황
        val app = atReturning()
        app.returnFlow.submit(ADMIN_CODE)
        // 행동
        app.signals.driverSeat.leave()
        app.signals.driverSeat.sit()
        // 결과
        assertSame(SetupLanguage, app.engine.state.value)
    }

    /** 반납 뒤 앱을 다시 켜도 이전 손님의 대여가 남아 있지 않은지 확인한다. */
    @Test
    fun `반납 뒤 앱을 다시 켜도 이전 손님의 대여는 남아 있지 않다`() = runBlocking {
        // 상황
        val first = atReturning()
        first.returnFlow.submit(ADMIN_CODE)
        val app = restart(first)
        // 행동
        app.startup.start()
        // 결과
        assertNull(app.driverSessions.session.value)
        assertTrue(app.vendorConfigs.config.value.hasAdminCode())
    }

    private companion object {
        const val WRONG = "Wrong123"
    }
}
