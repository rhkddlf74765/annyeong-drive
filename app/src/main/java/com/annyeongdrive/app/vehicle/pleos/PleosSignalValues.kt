package com.annyeongdrive.app.vehicle.pleos

import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import com.annyeongdrive.app.gear.Gear
import com.annyeongdrive.app.vehicle.Seat
import com.annyeongdrive.app.vehicle.SeatBeltStatus
import com.annyeongdrive.app.vehicle.SeatOccupancy

/**
 * Pleos Vehicle SDK 값을 앱의 신호 값으로 옮긴다. 판단 없이 값만 바꾼다. 모르는 값은 알 수 없음으로 둔다.
 * 어댑터(CarGearSource 등)와 떨어뜨려 JVM 테스트로 확인한다.
 */
object PleosSignalValues {

    /** 앱 좌석 → Pleos 좌석 위치. 왼쪽 운전석 차량(국내) 기준이다. */
    val SEAT_AREAS: Map<Seat, SeatValue> = mapOf(
        Seat.DRIVER to SeatValue.ROW_1_LEFT,
        Seat.FRONT_PASSENGER to SeatValue.ROW_1_RIGHT,
        Seat.REAR_LEFT to SeatValue.ROW_2_LEFT,
        Seat.REAR_CENTER to SeatValue.ROW_2_CENTER,
        Seat.REAR_RIGHT to SeatValue.ROW_2_RIGHT,
    )

    /**
     * Pleos 기어 값을 앱 기어로 옮긴다. 수동 변속 전진 단(1~9단)은 전진(D)으로 본다.
     *
     * @param gear Pleos 기어 값. 받지 못했으면 null
     * @return 앱 기어. 모르면 [Gear.UNKNOWN]
     */
    fun gearOf(gear: VehicleGear?): Gear = when (gear) {
        VehicleGear.GEAR_PARK -> Gear.P
        VehicleGear.GEAR_REVERSE -> Gear.R
        VehicleGear.GEAR_NEUTRAL -> Gear.N
        VehicleGear.GEAR_DRIVE -> Gear.D
        null, VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
        else -> Gear.D
    }

    /**
     * Pleos 착좌 값을 앱 착좌 상태로 옮긴다. SDK 값을 뒤집어 읽는다.
     * SDK 2.3.2는 차량 원값(SEAT_OCCUPANCY)이 1일 때만 true를 준다. Android 표준에서 1은 비어 있음(VACANT), 2가 앉음(OCCUPIED)이고,
     * 에뮬레이터 차량 계층도 앉음을 2로 기록한다(11/14 확인). 그래서 true는 비어 있음, false는 앉음(원값 2)으로 본다.
     * false에는 원값 0(모름)도 섞이므로 모름도 앉음으로 보게 된다(11/14 사용자 결정: 표준 값 기준으로 뒤집기).
     *
     * @param sdkOccupied SDK가 준 착좌 값. 받지 못했으면 null
     * @return 착좌 상태
     */
    fun occupancyOf(sdkOccupied: Boolean?): SeatOccupancy = when (sdkOccupied) {
        true -> SeatOccupancy.VACANT
        false -> SeatOccupancy.OCCUPIED
        null -> SeatOccupancy.UNKNOWN
    }

    /**
     * Pleos 안전띠 미착용 경고 값을 앱 경고 상태로 옮긴다. 경고 없음은 착용이 아니다([SeatBeltStatus.NO_WARNING]).
     *
     * @param warning 경고가 있으면 true, 없으면 false. 모르면 null
     * @return 안전띠 경고 상태
     */
    fun seatBeltOf(warning: Boolean?): SeatBeltStatus = when (warning) {
        true -> SeatBeltStatus.WARNING
        false -> SeatBeltStatus.NO_WARNING
        null -> SeatBeltStatus.UNKNOWN
    }
}
