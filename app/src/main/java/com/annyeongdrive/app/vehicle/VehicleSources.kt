package com.annyeongdrive.app.vehicle

/** 차속 신호(km/h)를 알려 주는 입력 어댑터. */
interface SpeedSource : SignalSource<Float>

/** 주행 중 화면 제한(CarUxRestrictions) 신호를 알려 주는 입력 어댑터. */
interface UxRestrictionSource : SignalSource<UxRestriction>

/** 운전석 착좌 신호를 알려 주는 입력 어댑터. */
interface DriverSeatSource : SignalSource<SeatOccupancy>

/** 좌석별 안전띠 미착용 경고 신호를 알려 주는 입력 어댑터. 출발 전 점검에서만 쓴다. */
interface SeatBeltSource : SignalSource<Map<Seat, SeatBeltStatus>>

/** 이 차량의 유종을 알려 주는 입력 어댑터. 지금은 업체 설정 값을 돌려준다. */
interface FuelTypeSource {

    /**
     * 이 차량의 유종을 돌려준다.
     *
     * @return 유종. 알 수 없으면 [FuelType.UNKNOWN]
     */
    fun fuelType(): FuelType
}
