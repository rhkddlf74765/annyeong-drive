package com.annyeongdrive.app.drive.state

import com.annyeongdrive.app.drive.DriveAction
import com.annyeongdrive.app.drive.DriveContext
import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.drive.Transition

/**
 * 주행(복합 상태). 기어 P + 차속 0이 되면 정차의 맥락 판단으로 간다.
 * 상태 머신: 들어옴 T9·B4, 시작 상태 T10, 나감 T11
 */
sealed interface Driving : DriveState

/**
 * 주행 — 일반 주행. 기어 D/R로 달리는 동안 위치가 바뀌면 규칙(보호구역·제한속도·단속 카메라)을 다시 알려 준다.
 * 상태 머신: 들어옴 T10, 자기 전이 B11, 나감 T11(부모 규칙)
 */
data object DrivingCruising : Driving {

    /**
     * 일반 주행 중 이벤트를 처리한다. 정차 조건이면 정차로 가고, 아니면 위치가 바뀔 때 규칙 안내를 낸다.
     * 상태 머신: T11, B11
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? =
        handleInDriving(context) ?: remindRoadRules(event, context) // T11, B11
}

/**
 * 기어 D/R로 달리는 중 위치가 바뀌면 알려 줄 규칙을 찾아 안내 할 일로 낸다. 상태는 그대로다.
 * N이나 기어를 모를 때(차속만 있는 경우)는 말하지 않는다(기능 보드 "주행 중 규칙 안내" 6번).
 * 상태 머신: B11
 *
 * @param event 받은 이벤트
 * @param context 판단 정보
 * @return 규칙 안내 자기 전이. 알릴 규칙이 없으면 null
 */
private fun remindRoadRules(event: DriveEvent, context: DriveContext): Transition? {
    if (event !is DriveEvent.LocationChanged) return null
    if (!context.vehicle.isDrivingGear()) return null
    val reminders = context.roadRules.observe(event.point)
    if (reminders.isEmpty()) return null
    return Transition(DrivingCruising, reminders.map(DriveAction::RemindRoadRule)) // B11
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
