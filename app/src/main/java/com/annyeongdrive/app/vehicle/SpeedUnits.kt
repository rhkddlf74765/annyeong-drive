package com.annyeongdrive.app.vehicle

import kotlin.math.abs

/** 차량 속도 단위 바꾸기. 차량 신호(PERF_VEHICLE_SPEED)는 m/s, 앱은 km/h를 쓴다. */
object SpeedUnits {

    private const val KMH_PER_MPS = 3.6f

    /**
     * m/s를 km/h로 바꾼다. 후진은 음수로 올 수 있어 크기만 쓴다(차속 발생 여부만 판단에 쓰인다).
     *
     * @param metersPerSecond 차량 속도(m/s)
     * @return 차속(km/h, 0 이상)
     */
    fun kmhOf(metersPerSecond: Float): Float = abs(metersPerSecond) * KMH_PER_MPS
}
