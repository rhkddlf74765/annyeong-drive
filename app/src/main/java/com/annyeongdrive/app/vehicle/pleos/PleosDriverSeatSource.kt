package com.annyeongdrive.app.vehicle.pleos

import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import android.content.Context
import android.util.Log
import com.annyeongdrive.app.vehicle.DriverSeatSource
import com.annyeongdrive.app.vehicle.Seat
import com.annyeongdrive.app.vehicle.SeatOccupancy

/**
 * 실제 운전석 착좌 신호(Pleos Vehicle SDK)를 알려 주는 입력 어댑터. release 빌드가 쓴다.
 * 값을 [PleosSignalValues.occupancyOf]로 옮기기만 한다(SDK 값이 표준과 반대라 그 함수가 뒤집어 읽는다). 시작하면 알 수 없음을 먼저 알리고, 현재 값과 바뀐 값을 알린다.
 *
 * @param context 앱 Context
 */
class PleosDriverSeatSource(context: Context) : DriverSeatSource {

    private val appContext = context.applicationContext
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
            Log.e(TAG, "seat occupancy subscription failed", exception)
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
        val seat = PleosVehicle.obtain(appContext).getSeat()
        seat.getCurrentSeatOccupancy(
            driverSeat,
            { notify(PleosSignalValues.occupancyOf(it)) },
            { Log.e(TAG, "seat occupancy read failed", it) },
        )
        seat.registerSeatOccupancy(driverSeat, callback)
    }

    /** 구독을 멈춘다. 멈춘 뒤에는 알리지 않는다. */
    override fun stop() {
        listener = null
        PleosVehicle.obtain(appContext).getSeat().unregisterSeatOccupancy(driverSeat, callback)
    }

    /**
     * 구독 중이면 메인 스레드에서 착좌 상태를 알린다.
     *
     * @param occupancy 알릴 착좌 상태
     */
    private fun notify(occupancy: SeatOccupancy) {
        PleosVehicle.onMain { listener?.invoke(occupancy) }
    }

    private companion object {
        const val TAG = "PleosDriverSeatSource"
    }
}
