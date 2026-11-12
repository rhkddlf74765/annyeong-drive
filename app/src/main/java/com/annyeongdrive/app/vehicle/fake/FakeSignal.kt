package com.annyeongdrive.app.vehicle.fake

import com.annyeongdrive.app.vehicle.SignalSource

/**
 * 값을 직접 넣어 바꾸는 가짜 신호. 가짜 입력 어댑터들이 구성(위임)으로 쓴다.
 * 구독자는 한 번에 하나만 둔다. 메인 스레드에서만 쓴다고 가정한다.
 *
 * @param initial 구독을 시작할 때 처음 알릴 값
 */
class FakeSignal<T>(initial: T) : SignalSource<T> {

    private var current: T = initial
    private var listener: ((T) -> Unit)? = null

    /**
     * 구독을 시작하고 현재 값을 바로 한 번 알린다.
     *
     * @param listener 신호 값을 받을 함수
     */
    override fun start(listener: (T) -> Unit) {
        this.listener = listener
        listener(current)
    }

    /** 구독을 멈춘다. 이후 [emit]은 값만 바꾸고 알리지 않는다. */
    override fun stop() {
        listener = null
    }

    /**
     * 값을 바꾸고 구독 중이면 알린다.
     *
     * @param value 새 신호 값
     */
    fun emit(value: T) {
        current = value
        listener?.invoke(value)
    }
}
