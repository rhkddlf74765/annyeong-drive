package com.annyeongdrive.app.signaltest

import com.annyeongdrive.app.vehicle.Seat
import com.annyeongdrive.app.vehicle.SeatBeltStatus
import com.annyeongdrive.app.vehicle.pleos.PleosSeatBeltSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 안전띠 어댑터: 가짜 Pleos SDK가 좌석별로 보낸 미착용 경고 값이 다섯 좌석 묶음으로 나간다. 경고 없음은 착용이 아니다.
 * 근거: CLAUDE.md 도메인 원칙(경고 없음은 알 수 없음과 같다, 전원 착용으로 처리하지 않는다), 11/14 사용자 결정(실제 안전띠 신호 연결),
 * 11/15 사용자 결정(SDK 값을 임의로 넣어 확인하는 신호 테스트). 에뮬레이터 콘솔에 좌석별 경고가 없어 이 테스트로만 확인한다.
 */
class SeatBeltAdapterSignalTest {

    private val sdk = FakePleosSafety()
    private val log = RecordingSignalLog()
    private val received = mutableListOf<Map<Seat, SeatBeltStatus>>()

    /**
     * 바로 실행하는 스레드 창구로 어댑터를 만들고 구독을 시작한다.
     *
     * @return 구독 중인 어댑터
     */
    private fun started(): PleosSeatBeltSource {
        val source = PleosSeatBeltSource({ sdk }, IMMEDIATE, log)
        source.start { received += it }
        return source
    }

    /** 시작하면 다섯 좌석 모두 알 수 없음으로 알리고 좌석마다 구독하는지 확인한다. */
    @Test
    fun `시작하면 다섯 좌석 모두 알 수 없음으로 알린다`() {
        // 상황 · 행동
        started()
        // 결과
        assertEquals(Seat.entries.associateWith { SeatBeltStatus.UNKNOWN }, received.single())
        assertEquals(Seat.entries.size, sdk.subscribers)
    }

    /** 한 좌석에 경고가 오면 그 좌석만 바뀐 다섯 좌석 묶음을 알리는지 확인한다. */
    @Test
    fun `한 좌석에 경고가 오면 그 좌석만 바뀐 묶음을 알린다`() {
        // 상황
        started()
        // 행동
        sdk.report(Seat.REAR_LEFT, true)
        // 결과
        val latest = received.last()
        assertEquals(SeatBeltStatus.WARNING, latest[Seat.REAR_LEFT])
        assertEquals(SeatBeltStatus.UNKNOWN, latest[Seat.DRIVER])
        assertEquals(Seat.entries.size, latest.size)
    }

    /** 모든 좌석이 경고 없음이어도 착용이 아니라 경고 없음으로 남는지 확인한다. */
    @Test
    fun `모든 좌석이 경고 없음이어도 착용이 아니라 경고 없음이다`() {
        // 상황
        started()
        // 행동
        Seat.entries.forEach { sdk.report(it, false) }
        // 결과
        assertEquals(Seat.entries.associateWith { SeatBeltStatus.NO_WARNING }, received.last())
    }

    /** 한 좌석의 구독 오류는 그 좌석만 알 수 없음으로 되돌리는지 확인한다. */
    @Test
    fun `한 좌석의 구독 오류는 그 좌석만 알 수 없음으로 둔다`() {
        // 상황
        started()
        sdk.report(Seat.DRIVER, false)
        sdk.report(Seat.FRONT_PASSENGER, true)
        // 행동
        sdk.fail(Seat.DRIVER, IllegalStateException("sensor"))
        // 결과
        val latest = received.last()
        assertEquals(SeatBeltStatus.UNKNOWN, latest[Seat.DRIVER])
        assertEquals(SeatBeltStatus.WARNING, latest[Seat.FRONT_PASSENGER])
        assertEquals(1, log.messages.size)
    }

    /** 멈추면 다섯 좌석 구독을 모두 놓고, 그 뒤 값은 알리지 않는지 확인한다. */
    @Test
    fun `멈추면 다섯 좌석 구독을 모두 놓고 이후 값은 알리지 않는다`() {
        // 상황
        val source = started()
        received.clear()
        // 행동
        source.stop()
        sdk.report(Seat.DRIVER, true)
        // 결과
        assertEquals(0, sdk.subscribers)
        assertTrue(received.isEmpty())
    }
}
