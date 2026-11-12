package com.annyeongdrive.app.drive

import com.annyeongdrive.app.drive.state.LearningStart
import com.annyeongdrive.app.drive.state.SetupLanguage
import com.annyeongdrive.app.drive.state.SetupPrecheck
import com.annyeongdrive.app.gear.Gear
import com.annyeongdrive.app.rental.AppLanguage
import com.annyeongdrive.app.vehicle.SeatOccupancy
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 대여 설정: 언어 선택 → 출발 전 점검 → 교육 시작.
 * 근거: docs/state-machine.md T6~T8·B1과 "전이가 없음을 정한 규칙", 기능 실행안 1번, 결정 문서 3장.
 */
class RentalSetupTest {

    /**
     * 착좌해 언어 선택 화면에 있는 시나리오를 만든다.
     *
     * @return 언어 선택 상태의 시나리오
     */
    private fun atLanguage(): DriveScenario {
        val scenario = DriveScenario()
        scenario.parkSignals()
        scenario.feed(DriveEvent.DriverSeatChanged(SeatOccupancy.OCCUPIED))
        return scenario
    }

    /** 언어를 고르면 출발 전 점검으로 가는지 확인한다(T7). */
    @Test
    fun `언어를 고르면 출발 전 점검으로 넘어간다`() {
        // 상황
        val scenario = atLanguage()
        // 행동
        scenario.feed(DriveEvent.LanguageSelected(AppLanguage.ENGLISH))
        // 결과
        assertSame(SetupPrecheck, scenario.state)
    }

    /** 점검을 마치면 교육을 시작하기 전이므로 교육 시작 화면이 열리는지 확인한다(T8, B1). */
    @Test
    fun `대여 설정이 끝나면 교육 시작 화면 열기가 나온다`() {
        // 상황
        val scenario = atLanguage()
        scenario.feed(DriveEvent.LanguageSelected(AppLanguage.ENGLISH))
        // 행동
        scenario.feed(DriveEvent.PrecheckCompleted)
        // 결과
        assertSame(LearningStart, scenario.state)
    }

    /** 대여 설정 중에는 기어를 D로 바꿔도 상태가 바뀌지 않는지 확인한다(보완 규칙). */
    @Test
    fun `대여 설정 중에는 기어를 D로 바꿔도 상태가 바뀌지 않는다`() {
        // 상황
        val scenario = atLanguage()
        // 행동
        scenario.feed(DriveEvent.GearChanged(Gear.D))
        // 결과
        assertSame(SetupLanguage, scenario.state)
    }
}
