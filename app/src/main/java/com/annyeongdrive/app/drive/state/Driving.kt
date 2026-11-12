package com.annyeongdrive.app.drive.state

import com.annyeongdrive.app.drive.DriveContext
import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.drive.Transition

/**
 * 주행(복합 상태). 기어 P + 차속 0이 되면 정차의 맥락 판단으로 간다.
 * 상태 머신: 들어옴 T9·B4, 시작 상태 T10, 나감 T11
 */
sealed interface Driving : DriveState

/**
 * 주행 — 일반 주행. 규칙 상기(자기 전이)는 8단계에서 붙인다.
 * 상태 머신: 들어옴 T10, 나감 T11(부모 규칙)
 */
data object DrivingCruising : Driving {

    /**
     * 일반 주행 중 이벤트를 처리한다. 지금은 주행 공통 규칙만 있다.
     * 상태 머신: T11
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? =
        handleInDriving(context) // T11
}

/**
 * 주행으로 들어가는 전이를 만든다. 주행의 시작 상태는 일반 주행이다.
 * 상태 머신: T10 (T9·B4가 이 함수로 주행에 들어간다)
 *
 * @return 일반 주행으로 가는 전이
 */
internal fun enterDriving(): Transition = Transition(DrivingCruising) // T10

/**
 * 주행 하위 상태들이 함께 따르는 규칙. 기어 P + 차속 0이면 정차의 맥락 판단으로 간다.
 * 상태 머신: T11 (→ T12)
 *
 * @param context 판단 정보
 * @return 전이. 정차 조건이 아니면 null
 */
private fun handleInDriving(context: DriveContext): Transition? {
    if (!context.vehicle.isParkedAndStopped()) return null
    return Transition(ParkedContext) // T11
}
