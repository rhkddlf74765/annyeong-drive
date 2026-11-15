package com.annyeongdrive.app.signaltest

import com.annyeongdrive.app.signaltest.FakePleosSeat.Companion.SDK_OCCUPIED
import com.annyeongdrive.app.signaltest.FakePleosSeat.Companion.SDK_VACANT
import com.annyeongdrive.app.vehicle.Seat
import com.annyeongdrive.app.vehicle.SeatOccupancy
import com.annyeongdrive.app.vehicle.pleos.PleosDriverSeatSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 운전석 착좌 어댑터: 가짜 Pleos SDK가 보낸 착좌 값(표준과 반대)이 앱 착좌 상태로 바뀌어 나간다.
 * 근거: 11/14 에뮬레이터 확인(SDK는 원값 1일 때만 true, 앉음 원값은 2), 11/14 사용자 결정(SDK 값 뒤집기),
 * vehicle/SignalSource 계약, 11/15 사용자 결정(SDK 값을 임의로 넣어 확인하는 신호 테스트).
 */
class DriverSeatAdapterSignalTest {

    private val sdk = FakePleosSeat()
    private val log = RecordingSignalLog()
    private val received = mutableListOf<SeatOccupancy>()

    /**
     * 바로 실행하는 스레드 창구로 어댑터를 만들고 구독을 시작한다.
     *
     * @return 구독 중인 어댑터
     */
    private fun started(): PleosDriverSeatSource {
        val source = PleosDriverSeatSource({ sdk }, IMMEDIATE, log)
        source.start { received += it }
        return source
    }

    /** 시작하면 알 수 없음을 먼저 알리고 SDK 현재 착좌(앉음 = false)를 알리는지 확인한다. */
    @Test
    fun `시작하면 알 수 없음을 먼저 알리고 SDK 현재 착좌를 알린다`() {
        // 상황
        sdk.setCurrent(Seat.DRIVER, SDK_OCCUPIED)
        // 행동
        started()
        // 결과
        assertEquals(listOf(SeatOccupancy.UNKNOWN, SeatOccupancy.OCCUPIED), received)
    }

    /** SDK 착좌 true는 비어 있음, false는 앉음, 빈 값은 모름으로 옮기는지 확인한다. */
    @Test
    fun `SDK 착좌 true는 비어 있음 false는 앉음으로 옮긴다`() {
        // 상황
        started()
        received.clear()
        // 행동
        sdk.report(Seat.DRIVER, SDK_VACANT)
        sdk.report(Seat.DRIVER, SDK_OCCUPIED)
        sdk.report(Seat.DRIVER, null)
        // 결과
        assertEquals(listOf(SeatOccupancy.VACANT, SeatOccupancy.OCCUPIED, SeatOccupancy.UNKNOWN), received)
    }

    /** 다른 좌석의 착좌가 바뀌어도 운전석 어댑터는 알리지 않는지 확인한다. */
    @Test
    fun `다른 좌석의 착좌는 알리지 않는다`() {
        // 상황
        started()
        received.clear()
        // 행동
        sdk.report(Seat.FRONT_PASSENGER, SDK_OCCUPIED)
        // 결과
        assertTrue(received.isEmpty())
    }

    /** SDK 구독 오류가 나면 알 수 없음을 알리고 오류를 남기는지 확인한다. */
    @Test
    fun `SDK 구독 오류가 나면 알 수 없음을 알린다`() {
        // 상황
        sdk.setCurrent(Seat.DRIVER, SDK_OCCUPIED)
        started()
        received.clear()
        // 행동
        sdk.fail(Seat.DRIVER, IllegalStateException("vehicle service died"))
        // 결과
        assertEquals(listOf(SeatOccupancy.UNKNOWN), received)
        assertEquals(1, log.messages.size)
    }

    /** 멈추면 SDK 구독을 놓고, 그 뒤 SDK가 값을 보내도 알리지 않는지 확인한다. */
    @Test
    fun `멈추면 SDK 구독을 놓고 이후 값은 알리지 않는다`() {
        // 상황
        val source = started()
        received.clear()
        // 행동
        source.stop()
        sdk.report(Seat.DRIVER, SDK_OCCUPIED)
        // 결과
        assertEquals(0, sdk.subscribers)
        assertTrue(received.isEmpty())
    }
}
