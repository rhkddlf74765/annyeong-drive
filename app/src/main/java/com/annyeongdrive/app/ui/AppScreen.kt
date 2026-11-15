package com.annyeongdrive.app.ui

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

/**
 * 앱이 보여 줄 화면 종류. 운전 상태를 화면으로 고르는 일만 한다(판단 없음). MainActivity가 이 값으로 화면을 그린다.
 * Android·Compose를 쓰지 않아 JVM에서 테스트한다(uitest/AppScreenTest).
 */
enum class AppScreen {
    /** 대기 문구(운전석에 앉기 전). */
    WAITING,

    /** 언어 선택. */
    LANGUAGE,

    /** 출발 전 점검. */
    PRECHECK,

    /** 정차 대기(교육 열기·반납·업체 설정). */
    PARKED_HOME,

    /** 교육 시작 제안. */
    LEARNING_START,

    /** 교육 카드·퀴즈·해설·결과. */
    LEARNING,

    /** 이어서 확인할까요. */
    RESUME_OFFER,

    /** 주정차 경고. */
    PARKING_WARNING,

    /** 주행 중. */
    DRIVING,

    /** 반납 처리(관리자 코드). */
    RETURN,

    /** 업체 설정. */
    VENDOR_SETTINGS,

    /** 아직 화면이 없는 상태(도움·오주유)와 순간 상태. */
    PREPARING,
    ;

    companion object {
        /**
         * 운전 상태에 맞는 화면을 고른다. 상태를 빠짐없이 적어, 새 상태가 생기면 컴파일 오류로 드러나게 한다.
         *
         * @param state 지금 운전 상태
         * @return 보여 줄 화면
         */
        fun of(state: DriveState): AppScreen = when (state) {
            Idle -> WAITING
            SetupLanguage -> LANGUAGE
            SetupPrecheck -> PRECHECK
            ParkedRest -> PARKED_HOME
            LearningStart -> LEARNING_START
            LearningCard, LearningQuiz, LearningExplain, LearningResult -> LEARNING
            ParkedResumeOffer -> RESUME_OFFER
            ParkedNoParkingWarning -> PARKING_WARNING
            DrivingCruising -> DRIVING
            Returning -> RETURN
            ParkedVendorSettings -> VENDOR_SETTINGS
            ParkedHelp, ParkedFuelGuard, ParkedContext, Reset -> PREPARING
        }
    }
}
