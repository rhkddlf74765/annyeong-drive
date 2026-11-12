package com.annyeongdrive.app.drive.state

import com.annyeongdrive.app.drive.DriveContext
import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.drive.Transition

/**
 * 대여 설정(복합 상태). 언어 선택 → 출발 전 점검 순서로 진행한다.
 * 이 상태에서의 기어 D/R·차속은 전이를 만들지 않는다(보완 규칙).
 * 상태 머신: 들어옴 T2, 시작 상태 T6, 나감 T8
 */
sealed interface Setup : DriveState

/**
 * 대여 설정 — 언어 선택.
 * 상태 머신: 들어옴 T6, 나감 T7
 */
data object SetupLanguage : Setup {

    /**
     * 언어를 고르면 출발 전 점검으로 간다. 고른 언어의 저장은 화면이 RentalSession에 맡긴다.
     * 상태 머신: T7
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? = when (event) {
        is DriveEvent.LanguageSelected -> Transition(SetupPrecheck) // T7
        else -> null
    }
}

/**
 * 대여 설정 — 출발 전 점검(안전띠·유종·하이패스·반납 조건).
 * 상태 머신: 들어옴 T7, 나감 T8
 */
data object SetupPrecheck : Setup {

    /**
     * 점검을 마치면 정차의 맥락 판단으로 간다. 교육 시작 전이면 맥락 판단이 교육 시작을 연다.
     * 상태 머신: T8 (→ T12 → B1)
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? = when (event) {
        DriveEvent.PrecheckCompleted -> Transition(ParkedContext) // T8
        else -> null
    }
}
