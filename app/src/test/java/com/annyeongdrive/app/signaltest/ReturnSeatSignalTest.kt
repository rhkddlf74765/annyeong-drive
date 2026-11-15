package com.annyeongdrive.app.signaltest

import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.drive.state.Idle
import com.annyeongdrive.app.drive.state.SetupLanguage
import com.annyeongdrive.app.signaltest.FakePleosSeat.Companion.SDK_OCCUPIED
import com.annyeongdrive.app.signaltest.FakePleosSeat.Companion.SDK_VACANT
import com.annyeongdrive.app.vehicle.Seat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 반납 뒤 착좌 신호 시나리오: 반납 처리 뒤 SDK가 보낸 운전석 착좌 값(표준과 반대)으로 다음 대여가 시작된다.
 * 근거: CLAUDE.md 아키텍처(초기화는 착좌 해제가 아니라 반납 처리 완료로만), 상태 머신 T3·T5,
 * 11/14 사용자 결정(SDK 착좌 값 뒤집기), 11/15 에뮬레이터 확인(반납 → 비움 → 앉음 → 언어 선택),
 * 11/15 사용자 결정(SDK 값을 임의로 넣어 확인하는 신호 테스트).
 */
class ReturnSeatSignalTest {

    private val rig = SignalRig().startRented()

    /** 관리자 코드로 반납을 마친다. */
    private fun returnCar() {
        rig.engine.send(DriveEvent.ReturnRequested)
        rig.engine.send(DriveEvent.AdminCodeVerified)
    }

    /** 반납 뒤 SDK가 운전석 비움(true) 다음 앉음(false)을 보내면 다음 대여의 언어 선택이 시작되는지 확인한다. */
    @Test
    fun `반납 뒤 SDK가 비움 다음 앉음을 보내면 다음 대여의 언어 선택이 시작된다`() {
        // 상황
        returnCar()
        val afterReturn = rig.state
        // 행동
        rig.seat.report(Seat.DRIVER, SDK_VACANT)
        rig.seat.report(Seat.DRIVER, SDK_OCCUPIED)
        // 결과
        assertSame(Idle, afterReturn)
        assertEquals(1, rig.driverDataErased)
        assertSame(SetupLanguage, rig.state)
    }

    /** 반납 뒤 SDK가 비움만 보내면 다음 대여를 시작하지 않는지 확인한다. */
    @Test
    fun `반납 뒤 SDK가 비움만 보내면 다음 대여를 시작하지 않는다`() {
        // 상황
        returnCar()
        // 행동
        rig.seat.report(Seat.DRIVER, SDK_VACANT)
        // 결과
        assertSame(Idle, rig.state)
    }

    /** 반납 뒤 착좌 구독이 오류로 알 수 없음이 되면 다음 대여를 시작하지 않는지 확인한다. */
    @Test
    fun `반납 뒤 착좌 신호가 오류로 알 수 없음이면 다음 대여를 시작하지 않는다`() {
        // 상황
        returnCar()
        // 행동
        rig.seat.fail(Seat.DRIVER, IllegalStateException("vehicle service died"))
        // 결과
        assertSame(Idle, rig.state)
    }

    /** 대여 중 운전자가 내렸다 타도(착좌 해제) 운전자 데이터를 지우지 않는지 확인한다. */
    @Test
    fun `대여 중 운전석이 비었다가 다시 앉아도 운전자 데이터를 지우지 않는다`() {
        // 상황 · 행동
        rig.seat.report(Seat.DRIVER, SDK_VACANT)
        rig.seat.report(Seat.DRIVER, SDK_OCCUPIED)
        // 결과
        assertEquals(0, rig.driverDataErased)
    }
}
