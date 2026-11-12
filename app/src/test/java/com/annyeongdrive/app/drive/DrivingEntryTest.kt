package com.annyeongdrive.app.drive

import com.annyeongdrive.app.drive.state.DrivingCruising
import com.annyeongdrive.app.drive.state.ParkedRest
import com.annyeongdrive.app.gear.Gear
import com.annyeongdrive.app.vehicle.UxRestriction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 주행 진입과 UX 제한 시 교육 중단.
 * 근거: docs/state-machine.md T9·T10·B7, 기능 실행안 공통 퀴즈·부록 E, 결정 문서 3장.
 */
class DrivingEntryTest {

    /** 퀴즈 중 D로 바꾸면 닫기와 음성 중단이 같은 전이에서 함께 나오고 주행으로 가는지 확인한다(T9, T10). */
    @Test
    fun `퀴즈 중 D로 바꾸면 교육 화면 닫기와 학습 음성 중단이 함께 나온다`() {
        // 상황
        val scenario = DriveScenario().atLearningQuiz()
        // 행동
        val actions = scenario.feed(DriveEvent.GearChanged(Gear.D))
        // 결과
        assertEquals(listOf(DriveAction.CloseLearningScreen, DriveAction.StopLearningSpeech), actions)
        assertSame(DrivingCruising, scenario.state)
    }

    /** 기어가 P인 채로 UX 제한이 걸리면 교육을 닫고 정차 대기로 가는지 확인한다(B7). */
    @Test
    fun `UX 제한이 걸리면 기어와 상관없이 교육 화면이 닫힌다`() {
        // 상황
        val scenario = DriveScenario().atLearningQuiz()
        // 행동
        val actions = scenario.feed(DriveEvent.UxRestrictionChanged(UxRestriction.RESTRICTED))
        // 결과
        assertEquals(listOf(DriveAction.CloseLearningScreen, DriveAction.StopLearningSpeech), actions)
        assertSame(ParkedRest, scenario.state)
    }

    /** 교육 중 차속이 생기면 주행으로 가고 교육을 닫는지 확인한다(T9). */
    @Test
    fun `교육 중 차속이 생기면 주행으로 가고 교육 화면이 닫힌다`() {
        // 상황
        val scenario = DriveScenario().atLearningQuiz()
        // 행동
        val actions = scenario.feed(DriveEvent.SpeedChanged(3f))
        // 결과
        assertEquals(listOf(DriveAction.CloseLearningScreen, DriveAction.StopLearningSpeech), actions)
        assertSame(DrivingCruising, scenario.state)
    }

    /** 교육이 열려 있지 않은 정차 대기에서 R로 바꾸면 주행으로만 가는지 확인한다(T9). */
    @Test
    fun `정차 대기에서 R로 바꾸면 주행으로 간다`() {
        // 상황
        val scenario = DriveScenario().atParkedRest()
        // 행동
        val actions = scenario.feed(DriveEvent.GearChanged(Gear.R))
        // 결과
        assertTrue(actions.isEmpty())
        assertSame(DrivingCruising, scenario.state)
    }

    /** 주행 중에는 교육 열기 요청이 무시되는지 확인한다(교육 진입 조건). */
    @Test
    fun `주행 중에는 교육·퀴즈를 열 수 없다`() {
        // 상황
        val scenario = DriveScenario().atDriving()
        // 행동
        scenario.feed(DriveEvent.LearningOpenRequested)
        // 결과
        assertSame(DrivingCruising, scenario.state)
    }
}
