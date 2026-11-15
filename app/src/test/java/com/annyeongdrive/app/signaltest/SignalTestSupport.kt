package com.annyeongdrive.app.signaltest

import com.annyeongdrive.app.vehicle.MainThread
import com.annyeongdrive.app.vehicle.SignalLog
import com.annyeongdrive.app.vehicle.car.SpeedFeed

/** 가짜 차속 창구. 테스트가 차량이 주는 원값(m/s)을 그대로 넣는다. */
class FakeSpeedFeed : SpeedFeed {

    /** 차속을 읽을 수 있는지(차량용 기기 + 차속 권한). false면 권한 거부와 같다. */
    var readable = true

    /** 구독을 시작할 때 바로 넘길 현재 값(m/s). null이면 넘기지 않는다. */
    var initialMetersPerSecond: Float? = 0f

    private var onValue: ((Float?) -> Unit)? = null

    /** 지금 구독 중인지. */
    val subscribed: Boolean
        get() = onValue != null

    /**
     * 차속을 읽을 수 있는지 알려 준다.
     *
     * @return [readable]
     */
    override fun canRead(): Boolean = readable

    /**
     * 구독을 시작하고 현재 값을 넘긴다.
     *
     * @param onMetersPerSecond 차속 원값을 받을 함수
     */
    override fun subscribe(onMetersPerSecond: (Float?) -> Unit) {
        onValue = onMetersPerSecond
        val initial = initialMetersPerSecond ?: return
        onMetersPerSecond(initial)
    }

    /** 구독을 멈춘다. */
    override fun unsubscribe() {
        onValue = null
    }

    /**
     * 차량이 차속 원값을 보내는 것처럼 넘긴다.
     *
     * @param metersPerSecond 차속(m/s). 사용할 수 없는 상태면 null
     */
    fun send(metersPerSecond: Float?) {
        onValue?.invoke(metersPerSecond)
    }

    /**
     * 차량이 km/h 값을 m/s로 보내는 것처럼 넘긴다. 시나리오를 km/h로 읽기 쉽게 쓴다.
     *
     * @param kmh 차속(km/h)
     */
    fun sendKmh(kmh: Float) = send(kmh / KMH_PER_MPS)

    private companion object {
        const val KMH_PER_MPS = 3.6f
    }
}

/** 메인 스레드로 옮길 일을 모아 두었다가 테스트가 원할 때 실행한다. 콜백이 늦게 도착하는 순간을 만든다. */
class QueuedMainThread : MainThread {

    private val pending = ArrayDeque<() -> Unit>()

    /**
     * 할 일을 모아 둔다.
     *
     * @param block 실행할 일
     */
    override fun post(block: () -> Unit) {
        pending.addLast(block)
    }

    /** 모아 둔 일을 들어온 순서대로 모두 실행한다. */
    fun runAll() {
        while (pending.isNotEmpty()) pending.removeFirst().invoke()
    }
}

/** 옮길 일을 바로 실행하는 스레드 창구. 시나리오 테스트가 쓴다. */
val IMMEDIATE = MainThread { it() }

/** 어댑터가 남긴 문제를 기록한다. */
class RecordingSignalLog : SignalLog {

    /** 남긴 메시지 목록. */
    val messages = mutableListOf<String>()

    /**
     * 문제 하나를 기록한다.
     *
     * @param tag 어댑터 이름
     * @param message 무엇이 잘못됐는지
     * @param error 원인 예외. 없으면 null
     */
    override fun report(tag: String, message: String, error: Throwable?) {
        messages += "$tag: $message"
    }
}
