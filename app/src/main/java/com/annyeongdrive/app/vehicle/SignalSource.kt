package com.annyeongdrive.app.vehicle

/**
 * 차량·위치 신호 하나를 받아 그대로 알려 주는 입력 어댑터의 공통 모양.
 * 어댑터는 판단하지 않는다. 판단은 DriveCoordinator와 도메인 객체가 한다.
 *
 * @param T 알리는 신호 값의 타입
 */
interface SignalSource<T> {

    /**
     * 신호 구독을 시작한다. 시작 직후 현재 값을 한 번 알리고, 이후 값이 바뀔 때마다 알린다.
     *
     * @param listener 신호 값을 받을 함수
     */
    fun start(listener: (T) -> Unit)

    /** 신호 구독을 멈춘다. 멈춘 뒤에는 listener를 부르지 않는다. */
    fun stop()
}
