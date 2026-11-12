package com.annyeongdrive.app.drive.state

import com.annyeongdrive.app.drive.DriveContext
import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.drive.Transition

/**
 * 정차(복합 상태). 들어오면 [ParkedContext]에서 맥락을 판단한다(T12).
 * 정차 안 어디서든 기어 D/R 또는 차속 발생이면 주행으로 간다(T9). 정차 중 "반납 완료"는 반납 처리로 간다(T31).
 */
sealed interface Parked : DriveState

/**
 * 정차 — 맥락 판단과 분기. 머무르지 않는다.
 * 분기 순서: 검수된 주정차 구역(T13) → 주유소[기능 켜짐](T14) → 교육 시작 전[교육 진입 조건](B1) → 미완료 교육(T15) → 그 외(T16).
 */
data object ParkedContext : Parked, TransientState {

    /**
     * 거쳐 가는 상태는 이벤트를 받지 않는다.
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 항상 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? = null

    /**
     * [구현 예정] 정차 순간의 맥락을 보고 정차 하위 상태 하나를 고른다.
     * implementation-plan.md 1단계 1-b에서 T13~T16과 B1을 구현한다.
     *
     * @param context 판단 정보
     * @return 고른 하위 상태로 가는 전이
     */
    override fun resolve(context: DriveContext): Transition =
        TODO("구현 예정: 1-b T13~T16·B1")
}

/** 정차 — 주정차 경고(검수 구역). */
data object ParkedNoParkingWarning : Parked {

    /**
     * [구현 예정] 경고 확인 이벤트를 처리한다.
     * implementation-plan.md 1단계 1-b에서 T17을 구현한다.
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? =
        TODO("구현 예정: 1-b T17")
}

/** 정차 — 오주유 방지(뼈대, 기능 플래그). */
data object ParkedFuelGuard : Parked {

    /**
     * [구현 예정] 오주유 안내 확인 이벤트를 처리한다.
     * implementation-plan.md 1단계 1-b에서 T18을 구현한다.
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? =
        TODO("구현 예정: 1-b T18")
}

/** 정차 — 이어 보기 제안. */
data object ParkedResumeOffer : Parked {

    /**
     * [구현 예정] 이어 보기·나중에 보기 이벤트를 처리한다.
     * implementation-plan.md 1단계 1-b에서 T19·T20·B2를 구현한다.
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? =
        TODO("구현 예정: 1-b T19·T20·B2")
}

/** 정차 — 정차 대기. */
data object ParkedRest : Parked {

    /**
     * [구현 예정] 교육 열기·도움 메뉴 이벤트를 처리한다.
     * implementation-plan.md 1단계 1-b에서 T21·T22를 구현한다.
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? =
        TODO("구현 예정: 1-b T21·T22")
}

/** 정차 — 수동 도움 카드. 화면 내용은 P1이다. */
data object ParkedHelp : Parked {

    /**
     * [구현 예정] 도움 카드 닫기 이벤트를 처리한다.
     * implementation-plan.md 1단계 1-b에서 B5를 구현한다.
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? =
        TODO("구현 예정: 1-b B5")
}
