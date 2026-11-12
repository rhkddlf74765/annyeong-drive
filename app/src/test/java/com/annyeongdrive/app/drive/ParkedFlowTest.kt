package com.annyeongdrive.app.drive

import com.annyeongdrive.app.drive.state.LearningQuiz
import com.annyeongdrive.app.drive.state.LearningStart
import com.annyeongdrive.app.drive.state.ParkedHelp
import com.annyeongdrive.app.drive.state.ParkedRest
import com.annyeongdrive.app.vehicle.UxRestriction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 정차 안의 흐름: 경고 확인, 이어 보기, 교육 열기·닫기, 도움 카드.
 * 근거: docs/state-machine.md T17~T24·B2·B5, 기능 실행안 부록 B.
 */
class ParkedFlowTest {

    /** 주정차 경고를 확인하면 정차 대기로 가는지 확인한다(T17). */
    @Test
    fun `주정차 경고를 확인하면 정차 대기로 간다`() {
        // 상황
        val scenario = DriveScenario().atDriving()
        scenario.stopPlaces.noParkingZoneId = "PZ1"
        scenario.stopAt()
        // 행동
        scenario.feed(DriveEvent.WarningAcknowledged)
        // 결과
        assertSame(ParkedRest, scenario.state)
    }

    /** 오주유 안내를 확인하면 정차 대기로 가는지 확인한다(T18). */
    @Test
    fun `오주유 안내를 확인하면 정차 대기로 간다`() {
        // 상황
        val scenario = DriveScenario(FeatureFlags(fuelGuardEnabled = true)).atDriving()
        scenario.stopPlaces.nearGasStation = true
        scenario.stopAt()
        // 행동
        scenario.feed(DriveEvent.WarningAcknowledged)
        // 결과
        assertSame(ParkedRest, scenario.state)
    }

    /** 이어 보기를 누르면 교육 시작이 아니라 저장된 문제 위치로 들어가는지 확인한다(T19, B2). */
    @Test
    fun `이어 보기를 누르면 저장된 문제 위치로 들어간다`() {
        // 상황
        val scenario = DriveScenario().atDriving()
        scenario.learning.unfinished = true
        scenario.learning.resumeAt = LearningResumePoint.QUIZ
        scenario.stopAt()
        // 행동
        scenario.feed(DriveEvent.ResumeAccepted)
        // 결과
        assertSame(LearningQuiz, scenario.state)
    }

    /** 이어 보기 제안에서 나중에 보기를 누르면 정차 대기로 가는지 확인한다(T20). */
    @Test
    fun `이어 보기 제안에서 나중에 보기를 누르면 정차 대기로 간다`() {
        // 상황
        val scenario = DriveScenario().atDriving()
        scenario.learning.unfinished = true
        scenario.stopAt()
        // 행동
        scenario.feed(DriveEvent.ResumePostponed)
        // 결과
        assertSame(ParkedRest, scenario.state)
    }

    /** 정차 대기에서 교육을 열면 교육 시작 화면부터 보여 주는지 확인한다(T21, T24). */
    @Test
    fun `정차 대기에서 교육을 열면 교육 시작 화면부터 보여 준다`() {
        // 상황
        val scenario = DriveScenario().atParkedRest()
        // 행동
        scenario.feed(DriveEvent.LearningOpenRequested)
        // 결과
        assertSame(LearningStart, scenario.state)
    }

    /** UX 제한이 있으면 정차 대기에서 교육 열기 요청을 무시하는지 확인한다(교육 진입 조건). */
    @Test
    fun `UX 제한이 있으면 정차 대기에서 교육을 열 수 없다`() {
        // 상황
        val scenario = DriveScenario().atParkedRest()
        scenario.feed(DriveEvent.UxRestrictionChanged(UxRestriction.RESTRICTED))
        // 행동
        scenario.feed(DriveEvent.LearningOpenRequested)
        // 결과
        assertSame(ParkedRest, scenario.state)
    }

    /** 도움 메뉴를 고르면 도움 카드로 가는지 확인한다(T22). */
    @Test
    fun `도움 메뉴를 고르면 도움 카드로 간다`() {
        // 상황
        val scenario = DriveScenario().atParkedRest()
        // 행동
        scenario.feed(DriveEvent.HelpRequested)
        // 결과
        assertSame(ParkedHelp, scenario.state)
    }

    /** 도움 카드를 닫으면 정차 대기로 돌아오는지 확인한다(B5). */
    @Test
    fun `도움 카드를 닫으면 정차 대기로 돌아온다`() {
        // 상황
        val scenario = DriveScenario().atParkedRest()
        scenario.feed(DriveEvent.HelpRequested)
        // 행동
        scenario.feed(DriveEvent.HelpClosed)
        // 결과
        assertSame(ParkedRest, scenario.state)
    }

    /** 교육을 닫으면 학습 음성을 멈추고 정차 대기로 가는지 확인한다(T23). */
    @Test
    fun `교육을 닫으면 학습 음성을 멈추고 정차 대기로 간다`() {
        // 상황
        val scenario = DriveScenario().atLearningQuiz()
        // 행동
        val actions = scenario.feed(DriveEvent.LearningClosed)
        // 결과
        assertEquals(listOf(DriveAction.StopLearningSpeech), actions)
        assertSame(ParkedRest, scenario.state)
    }
}
