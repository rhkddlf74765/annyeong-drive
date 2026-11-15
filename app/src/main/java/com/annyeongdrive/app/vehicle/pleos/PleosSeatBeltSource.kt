package com.annyeongdrive.app.vehicle.pleos

import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.listener.SeatBeltNotBuckledWarningListener
import android.content.Context
import com.annyeongdrive.app.vehicle.AndroidSignalLog
import com.annyeongdrive.app.vehicle.MainThread
import com.annyeongdrive.app.vehicle.Seat
import com.annyeongdrive.app.vehicle.SeatBeltSource
import com.annyeongdrive.app.vehicle.SeatBeltStatus
import com.annyeongdrive.app.vehicle.SignalLog
import ai.pleos.playground.vehicle.api.Safety as PleosSafety

/**
 * 실제 좌석별 안전띠 미착용 경고 신호(Pleos Vehicle SDK Safety)를 알려 주는 입력 어댑터. release 빌드가 쓴다.
 * 좌석마다 값을 [PleosSignalValues.seatBeltOf]로 옮겨 다섯 좌석 묶음으로 알린다. 경고 없음은 착용이 아니다.
 * 시작하면 모든 좌석 알 수 없음을 먼저 알리고, 좌석 값이 바뀔 때마다 묶음 전체를 다시 알린다.
 * SDK 안전 창구를 주입받아 JVM 테스트에서 가짜 SDK로 값을 넣는다.
 *
 * @param sdk SDK 안전 창구를 돌려주는 함수. 구독을 시작·멈출 때 부른다
 * @param main 콜백을 엔진 쪽 스레드로 옮기는 창구
 * @param log 오류를 남기는 창구
 */
class PleosSeatBeltSource(
    private val sdk: () -> PleosSafety,
    private val main: MainThread,
    private val log: SignalLog,
) : SeatBeltSource {

    /**
     * 앱에서 쓰는 실제 SDK 연결로 만든다.
     *
     * @param context 앱 Context
     */
    constructor(context: Context) : this(
        { PleosVehicle.obtain(context.applicationContext).getSafety() },
        PleosVehicle::onMain,
        AndroidSignalLog,
    )

    private val statuses = Seat.entries.associateWith { SeatBeltStatus.UNKNOWN }.toMutableMap()
    private val callbacks = Seat.entries.associateWith(::callbackFor)
    private var listener: ((Map<Seat, SeatBeltStatus>) -> Unit)? = null

    /**
     * 구독을 시작한다.
     *
     * @param listener 좌석별 경고 묶음을 받을 함수
     */
    override fun start(listener: (Map<Seat, SeatBeltStatus>) -> Unit) {
        this.listener = listener
        listener(statuses.toMap())
        val safety = sdk()
        callbacks.forEach { (seat, callback) -> safety.registerSeatBeltNotBuckledWarning(areaOf(seat), callback) }
    }

    /** 구독을 멈춘다. 멈춘 뒤에는 알리지 않는다. */
    override fun stop() {
        listener = null
        val safety = sdk()
        callbacks.forEach { (seat, callback) -> safety.unregisterSeatBeltNotBuckledWarning(areaOf(seat), callback) }
    }

    /**
     * 좌석 하나의 경고를 받는 콜백을 만든다.
     *
     * @param seat 좌석
     * @return 그 좌석의 콜백
     */
    private fun callbackFor(seat: Seat): SeatBeltNotBuckledWarningListener = object : SeatBeltNotBuckledWarningListener {

        /**
         * 그 좌석의 경고가 바뀌면 묶음을 갱신해 알린다.
         *
         * @param warning 경고가 있으면 true. 모르면 null
         */
        override fun onSeatBeltNotBuckledWarningUpdated(warning: Boolean?) = update(seat, PleosSignalValues.seatBeltOf(warning))

        /**
         * 구독 오류를 기록하고 그 좌석을 알 수 없음으로 둔다.
         *
         * @param exception 오류
         */
        override fun onFailed(exception: Exception) {
            log.report(TAG, "seat belt subscription failed: $seat", exception)
            update(seat, SeatBeltStatus.UNKNOWN)
        }
    }

    /**
     * 엔진 쪽 스레드에서 좌석 하나의 값을 바꾸고 묶음을 알린다.
     *
     * @param seat 좌석
     * @param status 새 경고 상태
     */
    private fun update(seat: Seat, status: SeatBeltStatus) {
        main.post {
            statuses[seat] = status
            listener?.invoke(statuses.toMap())
        }
    }

    /**
     * 앱 좌석을 Pleos 좌석 위치로 바꾼다.
     *
     * @param seat 앱 좌석
     * @return Pleos 좌석 위치
     */
    private fun areaOf(seat: Seat): SeatArea = SeatArea(PleosSignalValues.SEAT_AREAS.getValue(seat))

    private companion object {
        const val TAG = "PleosSeatBeltSource"
    }
}
