package com.annyeongdrive.app.drive

import com.annyeongdrive.app.drive.state.DrivingCruising
import com.annyeongdrive.app.drive.state.Idle
import com.annyeongdrive.app.drive.state.ParkedRest
import com.annyeongdrive.app.drive.state.Returning
import com.annyeongdrive.app.gear.Gear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 반납 처리: 진입, 관리자 코드 확인, 취소, 코드 확인 전 출발.
 * 근거: docs/state-machine.md T31·T32·T5·B3·B4, 결정 문서 5장.
 */
class ReturnTest {

    /** 정차 중 반납 완료를 누르면 반납 처리로 가는지 확인한다(T31). */
    @Test
    fun `정차 중 반납 완료를 누르면 반납 처리로 간다`() {
        // 상황
        val scenario = DriveScenario().atParkedRest()
        // 행동
        scenario.feed(DriveEvent.ReturnRequested)
        // 결과
        assertSame(Returning, scenario.state)
    }

    /** 주행 중에는 반납 완료 요청을 무시하는지 확인한다(T31은 정차에서만). */
    @Test
    fun `주행 중에는 반납 완료를 눌러도 반납 처리로 가지 않는다`() {
        // 상황
        val scenario = DriveScenario().atDriving()
        // 행동
        scenario.feed(DriveEvent.ReturnRequested)
        // 결과
        assertSame(DrivingCruising, scenario.state)
    }

    /** 관리자 코드가 확인되면 운전자 데이터를 지우고 대기로 돌아가는지 확인한다(T32, T5). */
    @Test
    fun `관리자 코드가 확인되면 운전자 데이터를 지우고 대기로 돌아간다`() {
        // 상황
        val scenario = DriveScenario().atParkedRest()
        scenario.feed(DriveEvent.ReturnRequested)
        // 행동
        val actions = scenario.feed(DriveEvent.AdminCodeVerified)
        // 결과
        assertEquals(listOf(DriveAction.ResetDriverData), actions)
        assertSame(Idle, scenario.state)
    }

    /** 반납 처리를 취소하면 정차 대기로 돌아가는지 확인한다(B3). */
    @Test
    fun `반납 처리를 취소하면 정차 대기로 돌아간다`() {
        // 상황
        val scenario = DriveScenario().atParkedRest()
        scenario.feed(DriveEvent.ReturnRequested)
        // 행동
        scenario.feed(DriveEvent.ReturnCancelled)
        // 결과
        assertSame(ParkedRest, scenario.state)
    }

    /** 코드 확인 전에 출발하면 운전자 데이터를 지우지 않고 주행으로 가는지 확인한다(B4). */
    @Test
    fun `반납 처리 중 D로 바꾸면 운전자 데이터를 지우지 않고 주행으로 간다`() {
        // 상황
        val scenario = DriveScenario().atParkedRest()
        scenario.feed(DriveEvent.ReturnRequested)
        // 행동
        val actions = scenario.feed(DriveEvent.GearChanged(Gear.D))
        // 결과
        assertTrue(actions.isEmpty())
        assertSame(DrivingCruising, scenario.state)
    }

    /** 반납이 확정돼 대기로 간 뒤에는 D로 바꿔도 주행으로 가지 않는지 확인한다(대기의 보완 규칙). */
    @Test
    fun `반납이 확정된 뒤에는 D로 바꿔도 주행으로 가지 않는다`() {
        // 상황
        val scenario = DriveScenario().atParkedRest()
        scenario.feed(DriveEvent.ReturnRequested, DriveEvent.AdminCodeVerified)
        // 행동
        scenario.feed(DriveEvent.GearChanged(Gear.D))
        // 결과
        assertSame(Idle, scenario.state)
    }
}
