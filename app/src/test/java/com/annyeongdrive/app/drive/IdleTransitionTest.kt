package com.annyeongdrive.app.drive

import com.annyeongdrive.app.drive.state.Idle
import com.annyeongdrive.app.drive.state.ParkedRest
import com.annyeongdrive.app.drive.state.SetupLanguage
import com.annyeongdrive.app.vehicle.SeatOccupancy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 대기 상태에서 들어오고 나가는 전이와 초기화.
 * 근거: docs/state-machine.md T1~T5와 "전이가 없음을 정한 규칙"(착좌 해제), 결정 문서 3장·5장.
 */
class IdleTransitionTest {

    /** 앱이 켜지면 대기에서 시작하는지 확인한다(T1). */
    @Test
    fun `앱을 켜면 대기 상태에서 시작한다`() {
        // 상황
        val scenario = DriveScenario()
        // 행동
        val actions = scenario.feed(DriveEvent.AppStarted)
        // 결과
        assertSame(Idle, scenario.state)
        assertTrue(actions.isEmpty())
    }

    /** 대여 설정이 없는 차에 앉으면 대여 설정의 시작 상태인 언어 선택으로 가는지 확인한다(T2, T6). */
    @Test
    fun `대여 설정이 없을 때 운전석에 앉으면 언어 선택부터 시작한다`() {
        // 상황
        val scenario = DriveScenario()
        // 행동
        scenario.feed(DriveEvent.DriverSeatChanged(SeatOccupancy.OCCUPIED))
        // 결과
        assertSame(SetupLanguage, scenario.state)
    }

    /** 대여 설정을 마친 차에 앉으면 정차의 맥락 판단을 거쳐 정차 하위 상태로 가는지 확인한다(T3, T12, T16). */
    @Test
    fun `대여 설정이 있을 때 운전석에 앉으면 정차로 간다`() {
        // 상황
        val scenario = DriveScenario()
        scenario.rental.hasRentalSetup = true
        scenario.learning.started = true
        scenario.parkSignals()
        // 행동
        scenario.feed(DriveEvent.DriverSeatChanged(SeatOccupancy.OCCUPIED))
        // 결과
        assertSame(ParkedRest, scenario.state)
    }

    /** 예비 초기화 시각이 지난 채 앱을 켜면 초기화를 거쳐 대기로 돌아오는지 확인한다(T4, T5). */
    @Test
    fun `반납 예정 시각에서 N시간이 지난 채 앱을 켜면 운전자 데이터를 지우고 대기로 돌아온다`() {
        // 상황
        val scenario = DriveScenario()
        scenario.rental.resetOverdue = true
        // 행동
        val actions = scenario.feed(DriveEvent.AppStarted)
        // 결과
        assertEquals(listOf(DriveAction.ResetDriverData), actions)
        assertSame(Idle, scenario.state)
    }

    /** 예비 초기화 시각 전에는 앱을 켜도 아무것도 지우지 않는지 확인한다(T4 가드). */
    @Test
    fun `예비 초기화 시각 전이면 앱을 켜도 운전자 데이터를 지우지 않는다`() {
        // 상황
        val scenario = DriveScenario()
        // 행동
        val actions = scenario.feed(DriveEvent.AppStarted)
        // 결과
        assertFalse(actions.contains(DriveAction.ResetDriverData))
    }

    /** 정차 중 운전석에서 일어나도 초기화하지 않고 상태도 그대로인지 확인한다(착좌 해제 규칙). */
    @Test
    fun `착좌 해제만으로는 초기화하지 않는다`() {
        // 상황
        val scenario = DriveScenario().atParkedRest()
        // 행동
        val actions = scenario.feed(DriveEvent.DriverSeatChanged(SeatOccupancy.VACANT))
        // 결과
        assertTrue(actions.isEmpty())
        assertSame(ParkedRest, scenario.state)
    }
}
