package com.annyeongdrive.app.drive.state

import com.annyeongdrive.app.drive.DriveContext
import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.drive.Transition

/**
 * 대여 설정(복합 상태). 언어 선택 → 출발 전 점검 순서로 진행한다.
 * 시작 상태는 [SetupLanguage](T6)이고, 점검을 마치면 정차로 간다(T8).
 * 이 상태에서의 기어 D/R·차속은 전이를 만들지 않는다(보완 규칙).
 */
sealed interface Setup : DriveState

/** 대여 설정 — 언어 선택. */
data object SetupLanguage : Setup {

    /**
     * 언어를 고르면 출발 전 점검으로 간다(T7). 고른 언어의 저장은 화면이 RentalSession에 맡긴다.
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? = when (event) {
        is DriveEvent.LanguageSelected -> Transition(SetupPrecheck)
        else -> null
    }
}

/** 대여 설정 — 출발 전 점검(안전띠·유종·하이패스·반납 조건). */
data object SetupPrecheck : Setup {

    /**
     * 점검을 마치면 정차의 맥락 판단으로 간다(T8). 교육 시작 전이면 맥락 판단이 교육 시작을 연다(B1).
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? = when (event) {
        DriveEvent.PrecheckCompleted -> Transition(ParkedContext)
        else -> null
    }
}
