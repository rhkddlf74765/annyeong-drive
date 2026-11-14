package com.annyeongdrive.app.drive

import com.annyeongdrive.app.drive.state.DrivingCruising
import com.annyeongdrive.app.drive.state.LearningStart
import com.annyeongdrive.app.drive.state.ParkedFuelGuard
import com.annyeongdrive.app.drive.state.ParkedNoParkingWarning
import com.annyeongdrive.app.drive.state.ParkedRest
import com.annyeongdrive.app.drive.state.ParkedResumeOffer
import com.annyeongdrive.app.gear.Gear
import com.annyeongdrive.app.rental.AppLanguage
import com.annyeongdrive.app.vehicle.SeatOccupancy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 정차 순간의 맥락 판단과 우선순위. 주정차 경고(T13)는 주행 중 기어를 P로 바꿔 멈춘 순간에만 판단한다.
 * 근거: docs/state-machine.md T11~T16·B1·가드 해석 보완(11/14), 기능 실행안 8번, 결정 문서 3장(정차 순간 우선순위),
 * 기능 보드 "주정차 경고" 5번, 11/14 에뮬레이터 확인(반납 뒤 같은 자리에서 다시 경고).
 */
class StopContextTest {

    /** 검수 구역에서 정차하면 주정차 경고만 나오고, 미완료 교육이 있어도 이어 보기는 나오지 않는지 확인한다(T11, T13). */
    @Test
    fun `주정차 경고 구역 안에서 P를 넣으면 주정차 경고만 나오고 이어 보기는 나오지 않는다`() {
        // 상황
        val scenario = DriveScenario().atDriving()
        scenario.stopPlaces.noParkingZoneId = "PZ1"
        scenario.learning.unfinished = true
        // 행동
        val actions = scenario.stopAt()
        // 결과
        assertEquals(listOf(DriveAction.AnnounceParkingWarning("PZ1")), actions)
        assertSame(ParkedNoParkingWarning, scenario.state)
    }

    /** 오주유 기능이 켜졌고 주유소 반경 안이면 오주유 안내가 나오는지 확인한다(T14). */
    @Test
    fun `오주유 기능이 켜져 있고 주유소 반경 안이면 오주유 안내가 나온다`() {
        // 상황
        val scenario = DriveScenario(FeatureFlags(fuelGuardEnabled = true)).atDriving()
        scenario.stopPlaces.nearGasStation = true
        // 행동
        val actions = scenario.stopAt()
        // 결과
        assertEquals(listOf(DriveAction.AnnounceFuelCheck), actions)
        assertSame(ParkedFuelGuard, scenario.state)
    }

    /** 오주유 기능이 꺼져 있으면 주유소 조회 자체를 하지 않는지 확인한다(T14 가드). */
    @Test
    fun `오주유 기능이 꺼져 있으면 주유소 여부를 묻지 않는다`() {
        // 상황
        val scenario = DriveScenario().atDriving()
        scenario.stopPlaces.nearGasStation = true
        // 행동
        scenario.stopAt()
        // 결과
        assertEquals(0, scenario.stopPlaces.gasStationQueries)
        assertSame(ParkedRest, scenario.state)
    }

    /** 교육을 시작하기 전이면 정차할 때 교육 시작 화면이 열리는지 확인한다(B1). */
    @Test
    fun `교육을 시작하기 전이면 정차할 때 교육 시작 화면이 열린다`() {
        // 상황
        val scenario = DriveScenario().atDriving()
        scenario.learning.started = false
        // 행동
        scenario.stopAt()
        // 결과
        assertSame(LearningStart, scenario.state)
    }

    /** 교육 시작 전이어도 검수 구역이면 주정차 경고가 먼저인지 확인한다(B1 분기 순서). */
    @Test
    fun `교육을 시작하기 전이어도 주정차 경고 구역이면 주정차 경고가 먼저다`() {
        // 상황
        val scenario = DriveScenario().atDriving()
        scenario.learning.started = false
        scenario.stopPlaces.noParkingZoneId = "PZ1"
        // 행동
        scenario.stopAt()
        // 결과
        assertSame(ParkedNoParkingWarning, scenario.state)
    }

    /** 미완료 교육이 있으면 정차할 때 이어 보기 제안이 나오는지 확인한다(T15). */
    @Test
    fun `미완료 교육이 있는 상태로 정차하면 이어 보기 제안이 나온다`() {
        // 상황
        val scenario = DriveScenario().atDriving()
        scenario.learning.unfinished = true
        // 행동
        scenario.stopAt()
        // 결과
        assertSame(ParkedResumeOffer, scenario.state)
    }

    /** 아무 조건에도 해당하지 않으면 정차 대기로 가는지 확인한다(T16). */
    @Test
    fun `아무 조건도 없으면 정차 대기로 간다`() {
        // 상황
        val scenario = DriveScenario().atDriving()
        // 행동
        val actions = scenario.stopAt()
        // 결과
        assertTrue(actions.isEmpty())
        assertSame(ParkedRest, scenario.state)
    }

    /** 기어가 P여도 차속이 0이 아니면 정차로 보지 않는지 확인한다(T11 가드). */
    @Test
    fun `기어가 P여도 차속이 남아 있으면 정차로 보지 않는다`() {
        // 상황
        val scenario = DriveScenario().atDriving()
        // 행동
        scenario.feed(DriveEvent.GearChanged(Gear.P))
        // 결과
        assertSame(DrivingCruising, scenario.state)
    }

    /** 차속이 남은 채 P를 넣고 나중에 차속이 0이 되어 멈춰도 P로 바꾼 정차로 보는지 확인한다(T11, T13). */
    @Test
    fun `P를 먼저 넣고 차속이 나중에 0이 되어도 주정차 경고가 나온다`() {
        // 상황
        val scenario = DriveScenario().atDriving()
        scenario.stopPlaces.noParkingZoneId = "PZ1"
        scenario.feed(DriveEvent.LocationChanged(DriveScenario.SOMEWHERE), DriveEvent.GearChanged(Gear.P))
        // 행동
        val actions = scenario.feed(DriveEvent.SpeedChanged(0f))
        // 결과
        assertEquals(listOf(DriveAction.AnnounceParkingWarning("PZ1")), actions)
        assertSame(ParkedNoParkingWarning, scenario.state)
    }

    /** 경고 구역에서 반납한 뒤 같은 자리에서 다음 손님이 점검을 마쳐도(T8) 경고하지 않는지 확인한다. */
    @Test
    fun `경고 구역에서 반납한 뒤 다음 손님이 같은 자리에서 점검을 마치면 경고하지 않는다`() {
        // 상황
        val scenario = DriveScenario().atDriving()
        scenario.stopPlaces.noParkingZoneId = "PZ1"
        scenario.stopAt()
        scenario.feed(DriveEvent.WarningAcknowledged, DriveEvent.ReturnRequested, DriveEvent.AdminCodeVerified)
        scenario.rental.hasRentalSetup = false
        scenario.feed(DriveEvent.DriverSeatChanged(SeatOccupancy.OCCUPIED), DriveEvent.LanguageSelected(AppLanguage.ENGLISH))
        // 행동
        val actions = scenario.feed(DriveEvent.PrecheckCompleted)
        // 결과
        assertTrue(actions.isEmpty())
        assertSame(ParkedRest, scenario.state)
    }

    /** 정차 중에 N에서 P로 바꾼 것은 주행을 마친 정차가 아니므로, 나중에 다시 앉아도 경고하지 않는지 확인한다(T3, T13). */
    @Test
    fun `정차 중 N에서 P로 바꾼 뒤 다시 앉아도 경고하지 않는다`() {
        // 상황
        val scenario = DriveScenario().atParkedRest()
        scenario.stopPlaces.noParkingZoneId = "PZ1"
        scenario.feed(DriveEvent.LocationChanged(DriveScenario.SOMEWHERE), DriveEvent.GearChanged(Gear.N), DriveEvent.GearChanged(Gear.P))
        scenario.feed(DriveEvent.ReturnRequested, DriveEvent.AdminCodeVerified)
        // 행동
        val actions = scenario.feed(DriveEvent.DriverSeatChanged(SeatOccupancy.OCCUPIED))
        // 결과
        assertTrue(actions.isEmpty())
        assertSame(ParkedRest, scenario.state)
    }
}
