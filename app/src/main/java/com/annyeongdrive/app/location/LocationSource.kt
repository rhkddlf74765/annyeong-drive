package com.annyeongdrive.app.location

import com.annyeongdrive.app.vehicle.SignalSource

/** 현재 위치를 알려 주는 입력 어댑터. 실제 구현은 Pleos FusedLocation을 감싼다(3단계). */
interface LocationSource : SignalSource<GeoPoint>

/**
 * 개발·테스트용 가짜 위치 신호.
 * 위치는 처음에 없으므로 [moveTo]를 부르기 전에는 구독자에게 아무것도 알리지 않는다.
 */
class FakeLocationSource : LocationSource {

    private var current: GeoPoint? = null
    private var listener: ((GeoPoint) -> Unit)? = null

    /**
     * 구독을 시작한다. 이미 위치가 있으면 바로 한 번 알린다.
     *
     * @param listener 위치를 받을 함수
     */
    override fun start(listener: (GeoPoint) -> Unit) {
        this.listener = listener
        current?.let(listener)
    }

    /** 구독을 멈춘다. */
    override fun stop() {
        listener = null
    }

    /**
     * 위치를 바꾸고 구독 중이면 알린다.
     *
     * @param point 새 위치
     */
    fun moveTo(point: GeoPoint) {
        current = point
        listener?.invoke(point)
    }
}
