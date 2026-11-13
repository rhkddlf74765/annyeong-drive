package com.annyeongdrive.app.drive

import com.annyeongdrive.app.drive.state.DrivingCruising
import com.annyeongdrive.app.drive.state.LearningStart
import com.annyeongdrive.app.drive.state.ParkedRest
import com.annyeongdrive.app.drive.state.ParkedVendorSettings
import com.annyeongdrive.app.drive.state.Returning
import com.annyeongdrive.app.gear.Gear
import com.annyeongdrive.app.vehicle.UxRestriction
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 업체 설정 상태: 정차 대기에서만 열리고, 닫거나 출발하면 나간다.
 * 근거: docs/state-machine.md 보완 전이 B9·B10과 정차 공통 규칙 T9·T31, 결정 문서 5장.
 */
class VendorSettingsStateTest {

    /** 정차 대기에서 업체 설정을 누르면 업체 설정 상태로 가는지 확인한다(B9). */
    @Test
    fun `정차 대기에서 업체 설정을 누르면 업체 설정으로 간다`() {
        // 상황
        val scenario = DriveScenario().atParkedRest()
        // 행동
        scenario.feed(DriveEvent.VendorSettingsRequested)
        // 결과
        assertSame(ParkedVendorSettings, scenario.state)
    }

    /** 업체 설정을 닫으면 정차 대기로 돌아가는지 확인한다(B10). */
    @Test
    fun `업체 설정을 닫으면 정차 대기로 돌아간다`() {
        // 상황
        val scenario = DriveScenario().atParkedRest()
        scenario.feed(DriveEvent.VendorSettingsRequested)
        // 행동
        scenario.feed(DriveEvent.VendorSettingsClosed)
        // 결과
        assertSame(ParkedRest, scenario.state)
    }

    /** 업체 설정 중 D로 바꾸면 주행으로 가는지 확인한다(T9). 교육 화면이 아니므로 학습 음성 중단은 나오지 않는다. */
    @Test
    fun `업체 설정 중 D로 바꾸면 주행으로 간다`() {
        // 상황
        val scenario = DriveScenario().atParkedRest()
        scenario.feed(DriveEvent.VendorSettingsRequested)
        // 행동
        val actions = scenario.feed(DriveEvent.GearChanged(Gear.D))
        // 결과
        assertSame(DrivingCruising, scenario.state)
        assertTrue(actions.isEmpty())
    }

    /** 주행 중에는 업체 설정을 열 수 없는지 확인한다. */
    @Test
    fun `주행 중에는 업체 설정을 열 수 없다`() {
        // 상황
        val scenario = DriveScenario().atDriving()
        // 행동
        scenario.feed(DriveEvent.VendorSettingsRequested)
        // 결과
        assertSame(DrivingCruising, scenario.state)
    }

    /** 교육·퀴즈 중에는 업체 설정 요청을 무시하는지 확인한다(정차 대기에서만 열림). */
    @Test
    fun `교육 중에는 업체 설정을 열 수 없다`() {
        // 상황
        val scenario = DriveScenario().atParkedRest()
        scenario.feed(DriveEvent.LearningOpenRequested)
        // 행동
        scenario.feed(DriveEvent.VendorSettingsRequested)
        // 결과
        assertSame(LearningStart, scenario.state)
    }

    /** 업체 설정 중 UX 제한이 걸려도 업체 설정은 그대로인지 확인한다(UX 제한은 교육·퀴즈만 닫는다, B7). */
    @Test
    fun `업체 설정 중 UX 제한이 걸려도 업체 설정은 닫히지 않는다`() {
        // 상황
        val scenario = DriveScenario().atParkedRest()
        scenario.feed(DriveEvent.VendorSettingsRequested)
        // 행동
        scenario.feed(DriveEvent.UxRestrictionChanged(UxRestriction.RESTRICTED))
        // 결과
        assertSame(ParkedVendorSettings, scenario.state)
    }

    /** 업체 설정 화면에서 반납 완료 이벤트가 오면 반납 처리로 가는지 확인한다(정차 공통 규칙 T31). */
    @Test
    fun `업체 설정 중 반납 완료가 오면 반납 처리로 간다`() {
        // 상황
        val scenario = DriveScenario().atParkedRest()
        scenario.feed(DriveEvent.VendorSettingsRequested)
        // 행동
        scenario.feed(DriveEvent.ReturnRequested)
        // 결과
        assertSame(Returning, scenario.state)
    }
}
