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
     * [구현 예정] 언어 선택 이벤트를 처리한다.
     * implementation-plan.md 1단계 1-b에서 T7을 구현한다.
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? =
        TODO("구현 예정: 1-b T7")
}

/** 대여 설정 — 출발 전 점검(안전띠·유종·하이패스·반납 조건). */
data object SetupPrecheck : Setup {

    /**
     * [구현 예정] 출발 전 점검 완료 이벤트를 처리한다.
     * implementation-plan.md 1단계 1-b에서 T8을 구현한다.
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? =
        TODO("구현 예정: 1-b T8")
}
