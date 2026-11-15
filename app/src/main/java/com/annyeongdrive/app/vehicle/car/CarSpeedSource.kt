package com.annyeongdrive.app.vehicle.car

import android.content.Context
import com.annyeongdrive.app.vehicle.AndroidSignalLog
import com.annyeongdrive.app.vehicle.SignalLog
import com.annyeongdrive.app.vehicle.SpeedSource
import com.annyeongdrive.app.vehicle.SpeedUnits

/**
 * 실제 차속 신호(android.car PERF_VEHICLE_SPEED)를 알려 주는 입력 어댑터. release 빌드가 쓴다.
 * Pleos Vehicle SDK(2.3.2)에는 차속 API가 없어 Android 차량 라이브러리를 쓴다(11/14 확인).
 * 값을 km/h로 바꾸기만 한다([SpeedUnits.kmhOf]). 차속을 모르면 알리지 않는다(엔진은 차속을 받기 전까지 정차·주행으로 보지 않는다).
 * 차속 권한(CAR_SPEED, 실행 중 허용)이 없거나 차량용 기기가 아니면 구독하지 않는다.
 * 차량 연결([SpeedFeed])을 주입받아 JVM 테스트에서 가짜 차속(m/s)을 넣는다.
 *
 * @param feed 차속 원값(m/s)을 받는 창구
 * @param log 사용 불가를 남기는 창구
 */
class CarSpeedSource(
    private val feed: SpeedFeed,
    private val log: SignalLog,
) : SpeedSource {

    /**
     * 앱에서 쓰는 실제 차량 연결로 만든다.
     *
     * @param context 앱 Context
     */
    constructor(context: Context) : this(CarSpeedFeed(context), AndroidSignalLog)

    private var listener: ((Float) -> Unit)? = null

    /**
     * 구독을 시작한다. 차량 서비스에 연결되면 현재 값과 바뀐 값을 알린다.
     *
     * @param listener 차속(km/h)을 받을 함수
     */
    override fun start(listener: (Float) -> Unit) {
        this.listener = listener
        if (!feed.canRead()) {
            log.report(TAG, "speed not available: no automotive feature or CAR_SPEED permission", null)
            return
        }
        feed.subscribe(::notify)
    }

    /** 구독을 멈추고 차량 연결을 끊는다. 멈춘 뒤에는 알리지 않는다. */
    override fun stop() {
        listener = null
        feed.unsubscribe()
    }

    /**
     * 구독 중이면 차속을 km/h로 알린다. 사용할 수 없는 값(null)은 알리지 않는다.
     *
     * @param metersPerSecond 차속 원값(m/s). 사용할 수 없으면 null
     */
    private fun notify(metersPerSecond: Float?) {
        val value = metersPerSecond ?: return
        listener?.invoke(SpeedUnits.kmhOf(value))
    }

    private companion object {
        const val TAG = "CarSpeedSource"
    }
}
