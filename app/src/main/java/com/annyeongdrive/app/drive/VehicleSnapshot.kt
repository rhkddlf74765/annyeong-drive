package com.annyeongdrive.app.drive

import com.annyeongdrive.app.gear.Gear
import com.annyeongdrive.app.location.GeoPoint
import com.annyeongdrive.app.vehicle.SeatBeltReadings
import com.annyeongdrive.app.vehicle.UxRestriction

/**
 * Coordinator가 지금까지 받은 차량·위치 신호의 최신 값.
 * 상태 객체는 데이터를 갖지 않고, 전이 가드는 이 스냅샷의 판정 함수를 쓴다.
 *
 * @property gear 현재 기어
 * @property speedKmh 현재 차속(km/h). 아직 받지 못했으면 null
 * @property ux 현재 화면 제한
 * @property location 마지막 위치. 아직 받지 못했으면 null
 * @property seatBelts 좌석별 안전띠 경고 신호. 상태 전이에는 쓰지 않고 출발 전 점검 화면이 읽는다
 * @property shiftedIntoPark 마지막 기어 변경이 P가 아닌 기어(D·R·N)에서 P로 바꾼 것인지. 앱을 켤 때 처음 받은 P는 아니다(T13 가드)
 */
data class VehicleSnapshot(
    val gear: Gear,
    val speedKmh: Float?,
    val ux: UxRestriction,
    val location: GeoPoint?,
    val seatBelts: SeatBeltReadings = SeatBeltReadings.UNKNOWN,
    val shiftedIntoPark: Boolean = false,
) {

    /**
     * 이벤트가 차량·위치 신호면 그 값을 반영한 새 스냅샷을 돌려준다. 다른 이벤트면 그대로 돌려준다.
     *
     * @param event 받은 이벤트
     * @return 갱신된 스냅샷
     */
    fun updatedBy(event: DriveEvent): VehicleSnapshot = when (event) {
        is DriveEvent.GearChanged -> copy(gear = event.gear, shiftedIntoPark = isShiftIntoPark(event.gear))
        is DriveEvent.SpeedChanged -> copy(speedKmh = event.kmh)
        is DriveEvent.UxRestrictionChanged -> copy(ux = event.restriction)
        is DriveEvent.LocationChanged -> copy(location = event.point)
        is DriveEvent.SeatBeltChanged -> copy(seatBelts = event.readings)
        else -> this
    }

    /**
     * 교육·퀴즈를 열 수 있는지 판단한다. 기어 P + 차속 0 + UX 제한 없음일 때만 참이다.
     * 상태 머신: B1, T19, T21 가드
     *
     * @return 교육 진입 조건을 모두 만족하면 true
     */
    fun canOpenLearning(): Boolean = isParkedAndStopped() && ux == UxRestriction.NONE

    /**
     * 교육·퀴즈를 열 수 있는지와 열 수 없는 이유를 돌려준다. 정차 대기·이어 보기 화면이 보여 준다.
     * 열 수 있는 조건은 [canOpenLearning]과 같고, 열 수 없을 때 아직 받지 못한 신호가 있으면 확인 중으로 본다.
     * 상태 머신: T19, T21 가드의 화면 안내
     *
     * @return 교육을 열 수 있는지
     */
    fun learningAvailability(): LearningAvailability = when {
        canOpenLearning() -> LearningAvailability.AVAILABLE
        hasUnknownSignal() -> LearningAvailability.CHECKING_VEHICLE
        else -> LearningAvailability.RESTRICTED
    }

    /**
     * 주행 진입 신호가 있는지 판단한다. 기어 D/R 또는 차속 발생이다.
     * 상태 머신: T9, B4 가드
     *
     * @return 주행 진입 신호가 하나라도 있으면 true
     */
    fun hasDrivingSignal(): Boolean = isDrivingGear() || isMoving()

    /**
     * 정차 진입 조건인지 판단한다. 기어 P + 차속 0이다.
     * 상태 머신: T11 가드
     *
     * @return 기어가 P이고 차속이 0이면 true
     */
    fun isParkedAndStopped(): Boolean = gear == Gear.P && isStopped()

    /**
     * 화면 제한이 걸려 있는지 판단한다.
     * 상태 머신: B7 가드
     *
     * @return 제한이 확인되면 true. 알 수 없음은 false
     */
    fun isUxRestricted(): Boolean = ux == UxRestriction.RESTRICTED

    /**
     * 교육 진입 조건에 쓰는 신호(기어·차속·화면 제한) 중 아직 받지 못한 것이 있는지 판단한다.
     *
     * @return 하나라도 알 수 없으면 true
     */
    private fun hasUnknownSignal(): Boolean =
        gear == Gear.UNKNOWN || speedKmh == null || ux == UxRestriction.UNKNOWN

    /**
     * 새 기어가 P이고 지금 기어가 P가 아닌 알려진 기어(D·R·N)인지 판단한다. 기어를 모르다가 처음 받은 P는 바꾼 것이 아니다.
     *
     * @param next 새 기어
     * @return P가 아닌 기어에서 P로 바꿨으면 true
     */
    private fun isShiftIntoPark(next: Gear): Boolean {
        if (next != Gear.P) return false
        return gear == Gear.D || gear == Gear.R || gear == Gear.N
    }

    /**
     * 기어가 주행 기어(D 또는 R)인지 판단한다. 주행 중 규칙 안내는 이때만 한다.
     * 상태 머신: T9 가드, B11 가드
     *
     * @return D나 R이면 true
     */
    fun isDrivingGear(): Boolean = gear == Gear.D || gear == Gear.R

    /**
     * 차속이 0인지 판단한다. 차속을 아직 받지 못했으면 정차로 보지 않는다.
     * 실차 신호의 잡음 임계값은 7단계에서 조정한다.
     *
     * @return 차속이 정확히 0이면 true
     */
    private fun isStopped(): Boolean = speedKmh == STOPPED_KMH

    /**
     * 차속이 발생했는지 판단한다. 차속을 아직 받지 못했으면 주행으로 보지 않는다.
     *
     * @return 차속이 0보다 크면 true
     */
    private fun isMoving(): Boolean {
        val speed = speedKmh ?: return false
        return speed > STOPPED_KMH
    }

    companion object {
        private const val STOPPED_KMH = 0f

        /** 앱 시작 직후, 아무 신호도 받지 못한 상태. */
        val UNKNOWN = VehicleSnapshot(
            gear = Gear.UNKNOWN,
            speedKmh = null,
            ux = UxRestriction.UNKNOWN,
            location = null,
        )
    }
}
