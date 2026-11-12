package com.annyeongdrive.app.drive.state

import com.annyeongdrive.app.drive.DriveAction
import com.annyeongdrive.app.drive.DriveContext
import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.drive.ReturnCheck
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
     * 정차 순간의 맥락을 보고 정차 하위 상태 하나를 고른다. 위에서부터 처음 맞는 하나만 고른다.
     *
     * @param context 판단 정보
     * @return 고른 하위 상태로 가는 전이
     */
    override fun resolve(context: DriveContext): Transition {
        val zoneId = verifiedNoParkingZoneAt(context)
        return when {
            zoneId != null -> Transition(ParkedNoParkingWarning, listOf(DriveAction.AnnounceParkingWarning(zoneId)))
            isAtGasStationWithGuardOn(context) -> Transition(ParkedFuelGuard, listOf(DriveAction.AnnounceFuelCheck))
            shouldOpenLearningStart(context) -> Transition(LearningStart)
            context.learning.hasUnfinished() -> Transition(ParkedResumeOffer)
            else -> Transition(ParkedRest)
        }
    }

    /**
     * 정차한 위치가 검수된 주정차 금지 구역 안이면 구역 식별자를 돌려준다(T13).
     *
     * @param context 판단 정보
     * @return 구역 식별자. 위치를 모르거나 구역 밖이면 null
     */
    private fun verifiedNoParkingZoneAt(context: DriveContext): String? {
        val point = context.vehicle.location ?: return null
        return context.stopPlaces.verifiedNoParkingZoneNear(point)
    }

    /**
     * 오주유 기능이 켜졌고 정차한 위치가 주유소 반경 안인지 판단한다(T14).
     * 기능이 꺼져 있으면 주유소 조회를 하지 않는다.
     *
     * @param context 판단 정보
     * @return 기능이 켜졌고 주유소 반경 안이면 true
     */
    private fun isAtGasStationWithGuardOn(context: DriveContext): Boolean {
        if (!context.flags.fuelGuardEnabled) return false
        val point = context.vehicle.location ?: return false
        return context.stopPlaces.isNearGasStation(point)
    }

    /**
     * 아직 교육을 시작하지 않았고 교육 진입 조건을 만족하는지 판단한다(B1).
     *
     * @param context 판단 정보
     * @return 교육 시작 화면을 열어야 하면 true
     */
    private fun shouldOpenLearningStart(context: DriveContext): Boolean =
        !context.learning.hasStarted() && context.vehicle.canOpenLearning()
}

/** 정차 — 주정차 경고(검수 구역). */
data object ParkedNoParkingWarning : Parked {

    /**
     * 경고를 확인하면 정차 대기로 간다(T17).
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? =
        handleInParked(event, context) ?: when (event) {
            DriveEvent.WarningAcknowledged -> Transition(ParkedRest)
            else -> null
        }
}

/** 정차 — 오주유 방지(뼈대, 기능 플래그). */
data object ParkedFuelGuard : Parked {

    /**
     * 오주유 안내를 확인하면 정차 대기로 간다(T18).
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? =
        handleInParked(event, context) ?: when (event) {
            DriveEvent.WarningAcknowledged -> Transition(ParkedRest)
            else -> null
        }
}

/** 정차 — 이어 보기 제안. */
data object ParkedResumeOffer : Parked {

    /**
     * 이어 보기면 저장된 위치로 교육에 들어가고(T19, B2), 나중에 보기면 정차 대기로 간다(T20).
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? =
        handleInParked(event, context) ?: when (event) {
            DriveEvent.ResumeAccepted -> resumeLearning(context)
            DriveEvent.ResumePostponed -> Transition(ParkedRest)
            else -> null
        }
}

/** 정차 — 정차 대기. */
data object ParkedRest : Parked {

    /**
     * 교육·퀴즈를 열면 교육 시작부터(T21, T24), 도움 메뉴를 고르면 도움 카드로 간다(T22).
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? =
        handleInParked(event, context) ?: when (event) {
            DriveEvent.LearningOpenRequested -> openLearning(context)
            DriveEvent.HelpRequested -> Transition(ParkedHelp)
            else -> null
        }
}

/** 정차 — 수동 도움 카드. 화면 내용은 P1이다. */
data object ParkedHelp : Parked {

    /**
     * 도움 카드를 닫으면 정차 대기로 간다(B5).
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? =
        handleInParked(event, context) ?: when (event) {
            DriveEvent.HelpClosed -> Transition(ParkedRest)
            else -> null
        }
}

/**
 * 정차 하위 상태들이 함께 따르는 규칙. 하위 상태의 규칙보다 먼저 본다.
 * 기어 D/R 또는 차속 발생이면 주행으로(T9), "반납 완료"면 반납 처리로(T31) 간다.
 *
 * @param event 받은 이벤트
 * @param context 판단 정보
 * @return 전이. 해당하지 않으면 null
 */
internal fun handleInParked(event: DriveEvent, context: DriveContext): Transition? = when {
    context.vehicle.hasDrivingSignal() -> enterDriving()
    event == DriveEvent.ReturnRequested -> startReturn(context)
    else -> null
}

/**
 * 반납 처리 진입 확인을 거쳐 반납 처리로 간다(T31).
 * 확인 결과가 늘어나면(예: 반납 장소 밖) 이 분기에서 처리한다.
 *
 * @param context 판단 정보
 * @return 반납 처리로 가는 전이
 */
private fun startReturn(context: DriveContext): Transition =
    when (context.returnGate.check(context.vehicle)) {
        ReturnCheck.Passed -> Transition(Returning)
    }

/**
 * 정차 대기에서 교육·퀴즈를 교육 시작부터 연다(T21, T24). 교육 진입 조건이 아니면 열지 않는다.
 *
 * @param context 판단 정보
 * @return 교육 시작으로 가는 전이. 조건이 안 되면 null
 */
private fun openLearning(context: DriveContext): Transition? {
    if (!context.vehicle.canOpenLearning()) return null
    return Transition(LearningStart)
}

/**
 * 이어 보기로 저장된 위치에 들어간다(T19, B2). 교육 진입 조건이 아니면 열지 않는다.
 *
 * @param context 판단 정보
 * @return 저장된 위치로 가는 전이. 조건이 안 되면 null
 */
private fun resumeLearning(context: DriveContext): Transition? {
    if (!context.vehicle.canOpenLearning()) return null
    return Transition(learningStateAt(context.learning.resumePoint()))
}
