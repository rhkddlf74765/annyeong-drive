package com.annyeongdrive.app.vehicle.car

/**
 * 차속 원값(m/s)을 받는 창구. 차량 서비스 연결은 release([CarSpeedFeed])가, 가짜 값은 JVM 테스트가 맡는다.
 * 판단하지 않고 값만 넘긴다.
 */
interface SpeedFeed {

    /**
     * 차속을 읽을 수 있는지 알려 준다.
     *
     * @return 차량용 기기이고 차속 권한이 있으면 true
     */
    fun canRead(): Boolean

    /**
     * 차속 구독을 시작한다. 연결되면 현재 값과 바뀐 값을 엔진 쪽 스레드에서 넘긴다.
     *
     * @param onMetersPerSecond 차속 원값(m/s)을 받을 함수. 사용할 수 없는 상태면 null
     */
    fun subscribe(onMetersPerSecond: (Float?) -> Unit)

    /** 구독을 멈추고 연결을 끊는다. */
    fun unsubscribe()
}
