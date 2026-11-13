package com.annyeongdrive.app.zones

import com.annyeongdrive.app.admin.AdminTestHarness
import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.drive.state.DrivingCruising
import com.annyeongdrive.app.drive.state.ParkedNoParkingWarning
import com.annyeongdrive.app.drive.state.ParkedRest
import com.annyeongdrive.app.drive.state.ParkedResumeOffer
import com.annyeongdrive.app.gear.Gear
import com.annyeongdrive.app.learning.CORE_CATALOG
import com.annyeongdrive.app.location.GeoPoint
import com.annyeongdrive.app.storage.TempDataStores
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
 * 주정차 경고: P로 바꾼 순간 100m 안에 검수된 주정차 단속 구역(카메라)이 있으면 확인을 요청하는 경고를 내고,
 * 미완료 교육 이어 보기보다 먼저다. 멀리 떨어졌거나, D로 신호 대기 중이거나, 앱을 켤 때 이미 P였으면 경고하지 않는다.
 * 경고 화면에서 "확인했어요"를 누르면 정차 대기로 간다(경고 화면이 아닐 때 누른 것은 무시).
 * 실제 DataStore 파일·엔진·Coordinator·구역 조회를 연결해 확인한다(시연 경로의 카메라 좌표).
 * 근거: 기능 보드 "주정차 경고" 1·4·5·7번, 결정 문서 3장(정차 순간 우선순위), docs/state-machine.md T11·T13·T15·T17, implementation-plan 8·9단계.
 */
class ParkingWarningFlowTest {

    private val stores = TempDataStores()
    private val writeScope = CoroutineScope(SupervisorJob())

    /** 시연 경로 끝(동문아파트 앞 근처)의 주정차 단속 카메라. */
    private val camera = ParkingCamera("PC5", 37.6251787, 127.0330457, "")

    /** 카메라에서 약 50m 떨어진 곳. */
    private val nearCamera = GeoPoint(37.6256287, 127.0330457)

    /** 카메라에서 약 330m 떨어진 곳. */
    private val farFromCamera = GeoPoint(37.6281787, 127.0330457)

    /** 파일 쓰기 범위를 닫고 임시 파일을 지운다. */
    @After
    fun tearDown() {
        writeScope.cancel()
        stores.delete()
    }

    /**
     * 교육을 시작만 한(미완료) 지난 대여로 앱을 켜고 앉은 뒤, 이어 보기를 미루고 출발한 앱을 만든다.
     *
     * @return 기어 D로 달리는 앱
     */
    private suspend fun driving(): AdminTestHarness {
        AdminTestHarness.seedPreviousRun(stores)
        val app = AdminTestHarness(stores, writeScope, catalog = CORE_CATALOG, zones = ZoneData(emptyList(), emptyList(), listOf(camera)))
        app.startAndSit()
        app.engine.send(DriveEvent.ResumePostponed)
        app.signals.gear.shift(Gear.D)
        app.signals.speed.changeSpeed(30f)
        return app
    }

    /**
     * 그 위치에서 멈춘다(위치 → 차속 0 → 기어 P).
     *
     * @param app 앱
     * @param point 멈출 위치
     */
    private fun stopAt(app: AdminTestHarness, point: GeoPoint) {
        app.signals.location.moveTo(point)
        app.signals.speed.changeSpeed(0f)
        app.signals.gear.shift(Gear.P)
    }

    /** 카메라 100m 안에서 P를 넣으면 주정차 경고가 나오고 이어 보기 제안은 나오지 않는다. */
    @Test
    fun `카메라 근처에서 P를 넣으면 주정차 경고가 이어 보기보다 먼저 나온다`() = runBlocking {
        // 상황
        val app = driving()

        // 행동
        stopAt(app, nearCamera)

        // 결과
        assertSame(ParkedNoParkingWarning, app.engine.state.value)
        assertEquals(listOf("PC5"), app.ports.parkingWarnings)
    }

    /** 카메라에서 멀리 떨어진 곳에서 P를 넣으면 경고하지 않고 다음 순서(이어 보기)로 간다. */
    @Test
    fun `카메라에서 멀리 떨어진 곳에서는 경고하지 않는다`() = runBlocking {
        // 상황
        val app = driving()

        // 행동
        stopAt(app, farFromCamera)

        // 결과
        assertSame(ParkedResumeOffer, app.engine.state.value)
        assertTrue(app.ports.parkingWarnings.isEmpty())
    }

    /** D를 유지한 채 카메라 앞에 서 있으면(신호 대기) 경고하지 않는다. */
    @Test
    fun `D로 신호 대기 중에는 경고하지 않는다`() = runBlocking {
        // 상황
        val app = driving()

        // 행동
        app.signals.location.moveTo(nearCamera)
        app.signals.speed.changeSpeed(0f)

        // 결과
        assertSame(DrivingCruising, app.engine.state.value)
        assertTrue(app.ports.parkingWarnings.isEmpty())
    }

    /** 같은 자리에서 P → D → P면 다시 경고한다. */
    @Test
    fun `같은 자리에서 다시 P를 넣으면 다시 경고한다`() = runBlocking {
        // 상황
        val app = driving()
        stopAt(app, nearCamera)

        // 행동
        app.signals.gear.shift(Gear.D)
        app.signals.gear.shift(Gear.P)

        // 결과
        assertSame(ParkedNoParkingWarning, app.engine.state.value)
        assertEquals(listOf("PC5", "PC5"), app.ports.parkingWarnings)
    }

    /** 카메라 근처에서 이미 P인 채로 앱을 켜고 앉으면 경고하지 않는다(P로 바꾼 순간이 아니다). */
    @Test
    fun `앱을 켤 때 이미 P면 카메라 근처라도 경고하지 않는다`() = runBlocking {
        // 상황
        AdminTestHarness.seedPreviousRun(stores)
        val app = AdminTestHarness(stores, writeScope, catalog = CORE_CATALOG, zones = ZoneData(emptyList(), emptyList(), listOf(camera)))
        app.signals.location.moveTo(nearCamera)

        // 행동
        app.startAndSit()

        // 결과
        assertSame(ParkedResumeOffer, app.engine.state.value)
        assertTrue(app.ports.parkingWarnings.isEmpty())
    }

    /** 경고 화면에서 확인을 누르면 정차 대기로 간다. */
    @Test
    fun `경고를 확인하면 정차 대기로 간다`() = runBlocking {
        // 상황
        val app = driving()
        stopAt(app, nearCamera)

        // 행동
        app.parkingWarning.acknowledge()

        // 결과
        assertSame(ParkedRest, app.engine.state.value)
    }

    /** 주행 중에 늦게 눌린 확인은 상태를 바꾸지 않는다. */
    @Test
    fun `경고 화면이 아닐 때 누른 확인은 무시한다`() = runBlocking {
        // 상황
        val app = driving()
        stopAt(app, nearCamera)
        app.signals.gear.shift(Gear.D)

        // 행동
        app.parkingWarning.acknowledge()

        // 결과
        assertSame(DrivingCruising, app.engine.state.value)
    }
}
