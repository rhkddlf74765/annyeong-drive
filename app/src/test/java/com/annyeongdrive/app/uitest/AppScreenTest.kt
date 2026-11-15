package com.annyeongdrive.app.uitest

import com.annyeongdrive.app.drive.state.DriveState
import com.annyeongdrive.app.drive.state.DrivingCruising
import com.annyeongdrive.app.drive.state.Idle
import com.annyeongdrive.app.drive.state.LearningCard
import com.annyeongdrive.app.drive.state.LearningExplain
import com.annyeongdrive.app.drive.state.LearningQuiz
import com.annyeongdrive.app.drive.state.LearningResult
import com.annyeongdrive.app.drive.state.LearningStart
import com.annyeongdrive.app.drive.state.ParkedContext
import com.annyeongdrive.app.drive.state.ParkedFuelGuard
import com.annyeongdrive.app.drive.state.ParkedHelp
import com.annyeongdrive.app.drive.state.ParkedNoParkingWarning
import com.annyeongdrive.app.drive.state.ParkedResumeOffer
import com.annyeongdrive.app.drive.state.ParkedRest
import com.annyeongdrive.app.drive.state.ParkedVendorSettings
import com.annyeongdrive.app.drive.state.Reset
import com.annyeongdrive.app.drive.state.Returning
import com.annyeongdrive.app.drive.state.SetupLanguage
import com.annyeongdrive.app.drive.state.SetupPrecheck
import com.annyeongdrive.app.ui.AppScreen
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 화면 고르기: 운전 상태마다 정해진 화면이 나오고, 화면이 있는 상태가 "준비 중" 화면으로 빠지지 않는다.
 * 화면 작업(feature/ui)에서 화면 고르기를 바꿔도 이 표가 지켜지는지 UI Test 워크플로가 확인한다.
 * 근거: docs/state-machine.md 상태 목록, CLAUDE.md 아키텍처(화면은 Coordinator가 내보낸 상태만 읽는다),
 * 노션 "화면 작업 안내서 (11/15)" 5장 화면 구조.
 */
class AppScreenTest {

    /** 화면이 있는 상태가 각자의 화면으로 가는지 상태별 표로 확인한다. */
    @Test
    fun `화면이 있는 상태는 각자의 화면으로 간다`() {
        // 상황
        val expected = mapOf<DriveState, AppScreen>(
            Idle to AppScreen.WAITING,
            SetupLanguage to AppScreen.LANGUAGE,
            SetupPrecheck to AppScreen.PRECHECK,
            ParkedRest to AppScreen.PARKED_HOME,
            LearningStart to AppScreen.LEARNING_START,
            ParkedResumeOffer to AppScreen.RESUME_OFFER,
            ParkedNoParkingWarning to AppScreen.PARKING_WARNING,
            DrivingCruising to AppScreen.DRIVING,
            Returning to AppScreen.RETURN,
            ParkedVendorSettings to AppScreen.VENDOR_SETTINGS,
        )

        // 행동
        val actual = expected.keys.associateWith(AppScreen::of)

        // 결과
        assertEquals(expected, actual)
    }

    /** 교육 카드·퀴즈·해설·결과 네 단계는 모두 같은 교육 화면으로 가는지 확인한다(교육 시작은 따로). */
    @Test
    fun `교육 네 단계는 모두 교육 화면으로 간다`() {
        // 상황
        val steps = listOf(LearningCard, LearningQuiz, LearningExplain, LearningResult)

        // 행동
        val screens = steps.map(AppScreen::of).toSet()

        // 결과
        assertEquals(setOf(AppScreen.LEARNING), screens)
    }

    /** 아직 화면이 없는 상태(도움·오주유)와 순간 상태만 "준비 중" 화면으로 가는지 확인한다. */
    @Test
    fun `화면이 아직 없는 상태와 순간 상태만 준비 중 화면이다`() {
        // 상황
        val withoutScreen = listOf(ParkedHelp, ParkedFuelGuard, ParkedContext, Reset)

        // 행동
        val screens = withoutScreen.map(AppScreen::of).toSet()

        // 결과
        assertEquals(setOf(AppScreen.PREPARING), screens)
    }
}
