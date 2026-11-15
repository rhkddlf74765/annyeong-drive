package com.annyeongdrive.app.vehicle.pleos

import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import android.content.Context
import com.annyeongdrive.app.vehicle.AndroidSignalLog
import com.annyeongdrive.app.vehicle.DriverSeatSource
import com.annyeongdrive.app.vehicle.MainThread
import com.annyeongdrive.app.vehicle.Seat
import com.annyeongdrive.app.vehicle.SeatOccupancy
import com.annyeongdrive.app.vehicle.SignalLog
import ai.pleos.playground.vehicle.api.Seat as PleosSeat

/**
 * 실제 운전석 착좌 신호(Pleos Vehicle SDK)를 알려 주는 입력 어댑터. release 빌드가 쓴다.
 * 값을 [PleosSignalValues.occupancyOf]로 옮기기만 한다(SDK 값이 표준과 반대라 그 함수가 뒤집어 읽는다). 시작하면 알 수 없음을 먼저 알리고, 현재 값과 바뀐 값을 알린다.
 * SDK 좌석 창구를 주입받아 JVM 테스트에서 가짜 SDK로 값을 넣는다.
 *
 * @param sdk SDK 좌석 창구를 돌려주는 함수. 구독을 시작·멈출 때 부른다
 * @param main 콜백을 엔진 쪽 스레드로 옮기는 창구
 * @param log 오류를 남기는 창구
 */
class PleosDriverSeatSource(
    private val sdk: () -> PleosSeat,
    private val main: MainThread,
    private val log: SignalLog,
) : DriverSeatSource {

    /**
     * 앱에서 쓰는 실제 SDK 연결로 만든다.
     *
     * @param context 앱 Context
     */
    constructor(context: Context) : this(
        { PleosVehicle.obtain(context.applicationContext).getSeat() },
        PleosVehicle::onMain,
        AndroidSignalLog,
    )

    private val driverSeat = SeatArea(PleosSignalValues.SEAT_AREAS.getValue(Seat.DRIVER))
    private var listener: ((SeatOccupancy) -> Unit)? = null

    private val callback = object : SeatOccupancyListener {

        /**
         * 착좌가 바뀌면 알린다.
         *
         * @param sdkOccupied SDK가 준 착좌 값(표준과 반대, [PleosSignalValues.occupancyOf] 참고). 모르면 null
         */
        override fun onSeatOccupancyUpdated(sdkOccupied: Boolean?) = notify(PleosSignalValues.occupancyOf(sdkOccupied))

        /**
         * 구독 오류를 기록하고 알 수 없음을 알린다.
         *
         * @param exception 오류
         */
        override fun onFailed(exception: Exception) {
            log.report(TAG, "seat occupancy subscription failed", exception)
            notify(SeatOccupancy.UNKNOWN)
        }
    }

    /**
     * 구독을 시작한다.
     *
     * @param listener 착좌 상태를 받을 함수
     */
    override fun start(listener: (SeatOccupancy) -> Unit) {
        this.listener = listener
        listener(SeatOccupancy.UNKNOWN)
        val seat = sdk()
        seat.getCurrentSeatOccupancy(
            driverSeat,
            { notify(PleosSignalValues.occupancyOf(it)) },
            { log.report(TAG, "seat occupancy read failed", it) },
        )
        seat.registerSeatOccupancy(driverSeat, callback)
    }

    /** 구독을 멈춘다. 멈춘 뒤에는 알리지 않는다. */
    override fun stop() {
        listener = null
        sdk().unregisterSeatOccupancy(driverSeat, callback)
    }

    /**
     * 구독 중이면 엔진 쪽 스레드에서 착좌 상태를 알린다. 옮기는 사이 구독을 멈췄으면 알리지 않는다.
     *
     * @param occupancy 알릴 착좌 상태
     */
    private fun notify(occupancy: SeatOccupancy) {
        main.post { listener?.invoke(occupancy) }
    }

    private companion object {
        const val TAG = "PleosDriverSeatSource"
    }
}
