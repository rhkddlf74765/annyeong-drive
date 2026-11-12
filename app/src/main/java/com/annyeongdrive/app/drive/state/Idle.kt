package com.annyeongdrive.app.drive.state

import com.annyeongdrive.app.drive.DriveContext
import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.drive.Transition
import com.annyeongdrive.app.vehicle.SeatOccupancy

/**
 * 대기. 앱 시작 직후와 초기화 뒤의 상태다. 운전자 데이터가 없거나 아직 착좌 전이다.
 * 기어 D/R·차속과 착좌 해제는 전이를 만들지 않는다.
 * 상태 머신: 들어옴 T1·T5, 나감 T2·T3·T4
 */
data object Idle : DriveState {

    /**
     * 대기 상태에서 앱 시작·착좌 이벤트를 처리한다.
     * 상태 머신: T2, T3, T4
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? = when (event) {
        DriveEvent.AppStarted -> resetIfOverdue(context) // T4
        is DriveEvent.DriverSeatChanged -> leaveOnSeated(event.occupancy, context) // T2, T3
        else -> null
    }

    /**
     * 예비 초기화 시각이 지났으면 초기화로 간다.
     * 상태 머신: T4
     *
     * @param context 판단 정보
     * @return 초기화로 가는 전이. 시각 전이면 null
     */
    private fun resetIfOverdue(context: DriveContext): Transition? {
        if (!context.rental.isResetOverdue()) return null
        return Transition(Reset) // T4
    }

    /**
     * 운전석에 앉으면 대여 설정 여부에 따라 정차 또는 대여 설정으로 간다.
     * 상태 머신: T2, T3
     *
     * @param occupancy 바뀐 착좌 상태
     * @param context 판단 정보
     * @return 전이. 착좌가 아니면 null
     */
    private fun leaveOnSeated(occupancy: SeatOccupancy, context: DriveContext): Transition? {
        if (occupancy != SeatOccupancy.OCCUPIED) return null
        return when {
            context.rental.hasRentalSetup() -> Transition(ParkedContext) // T3
            else -> Transition(SetupLanguage) // T2 (→ T6 시작 상태)
        }
    }
}
