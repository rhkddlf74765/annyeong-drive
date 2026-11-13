package com.annyeongdrive.app.setup

import com.annyeongdrive.app.admin.AdminTestHarness
import com.annyeongdrive.app.drive.state.SetupPrecheck
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 안전띠 신호 경로: 차량 신호 → 엔진 → Coordinator 차량 스냅샷 → 출발 전 점검 화면. 상태 전이는 만들지 않는다.
 * 근거: 결정 문서 7장, 11/13 결정(안전띠 신호는 Coordinator 한 경로로 받는다), implementation-plan 5단계.
 */
class SeatBeltSignalTest {

    private val stores = TempDataStores()
    private val writeScope = CoroutineScope(SupervisorJob())

    /** 파일 쓰기 범위를 닫고 임시 파일을 지운다. */
    @After
    fun tearDown() {
        writeScope.cancel()
        stores.delete()
    }

    /**
     * 처음 설치한 앱에서 영어를 골라 출발 전 점검 화면까지 간다.
     *
     * @return 출발 전 점검 화면에 있는 앱
     */
    private suspend fun atPrecheck(): AdminTestHarness {
        val app = AdminTestHarness(stores, writeScope)
        app.startAndSit()
        app.setupFlow.selectLanguage(AppLanguage.ENGLISH)
        return app
    }

    /** 점검 중 조수석 경고가 들어오면 엔진 차량 스냅샷에 담기고 운전 상태는 그대로다. */
    @Test
    fun `점검 중 조수석 안전띠 경고가 들어오면 그 좌석이 경고 좌석이 되고 상태는 그대로다`() = runBlocking {
        // 상황
        val app = atPrecheck()

        // 행동
        app.signals.seatBelts.change(Seat.FRONT_PASSENGER, SeatBeltStatus.WARNING)

        // 결과
        assertEquals(listOf(Seat.FRONT_PASSENGER), app.engine.vehicle.value.seatBelts.warningSeats())
        assertSame(SetupPrecheck, app.engine.state.value)
    }

    /** 경고가 풀려 "경고 없음"이 되면 경고 좌석에서 빠질 뿐이고, 착용으로 표시할 값은 만들어지지 않는다. */
    @Test
    fun `경고가 풀리면 경고 좌석에서 빠지고 점검은 여전히 수동 확인을 기다린다`() = runBlocking {
        // 상황
        val app = atPrecheck()
        app.signals.seatBelts.change(Seat.FRONT_PASSENGER, SeatBeltStatus.WARNING)

        // 행동
        app.signals.seatBelts.change(Seat.FRONT_PASSENGER, SeatBeltStatus.NO_WARNING)
        app.setupFlow.completePrecheck()

        // 결과
        assertTrue(app.engine.vehicle.value.seatBelts.warningSeats().isEmpty())
        assertSame(SetupPrecheck, app.engine.state.value)
    }

    /** 앱을 켜기 전 신호를 받지 못한 좌석은 모두 알 수 없음이고 경고 좌석이 없다. */
    @Test
    fun `신호를 받기 전에는 모든 좌석이 알 수 없음이고 경고 좌석이 없다`() = runBlocking {
        // 상황
        val app = atPrecheck()

        // 행동
        val readings = app.engine.vehicle.value.seatBelts

        // 결과
        assertTrue(readings.statuses.values.all { it == SeatBeltStatus.UNKNOWN })
        assertTrue(readings.warningSeats().isEmpty())
    }
}
