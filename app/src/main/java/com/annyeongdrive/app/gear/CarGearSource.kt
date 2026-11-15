package com.annyeongdrive.app.gear

import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import com.annyeongdrive.app.vehicle.AndroidSignalLog
import com.annyeongdrive.app.vehicle.MainThread
import com.annyeongdrive.app.vehicle.SignalLog
import com.annyeongdrive.app.vehicle.pleos.PleosSignalValues
import com.annyeongdrive.app.vehicle.pleos.PleosVehicle
import ai.pleos.playground.vehicle.api.Gear as PleosGear

/**
 * 실제 차량 기어 신호(Pleos Vehicle SDK의 기어 선택)를 알려 주는 입력 어댑터. release 빌드가 쓴다.
 * 값을 [PleosSignalValues.gearOf]로 옮기기만 하고 판단하지 않는다. 시작하면 알 수 없음을 먼저 알리고, 현재 값과 바뀐 값을 알린다.
 * 받지 못하면(오류) 알 수 없음을 알린다. 모르는 기어를 P로 보지 않는다.
 * SDK 기어 창구를 주입받아 JVM 테스트에서 가짜 SDK로 값을 넣는다.
 *
 * @param sdk SDK 기어 창구를 돌려주는 함수. 구독을 시작·멈출 때 부른다
 * @param main 콜백을 엔진 쪽 스레드로 옮기는 창구
 * @param log 오류를 남기는 창구
 */
class CarGearSource(
    private val sdk: () -> PleosGear,
    private val main: MainThread,
    private val log: SignalLog,
) : GearSource {

    /**
     * 앱에서 쓰는 실제 SDK 연결로 만든다.
     *
     * @param context 앱 Context
     */
    constructor(context: Context) : this(
        { PleosVehicle.obtain(context.applicationContext).getGear() },
        PleosVehicle::onMain,
        AndroidSignalLog,
    )

    private var listener: ((Gear) -> Unit)? = null

    private val callback = object : GearSelectionListener {

        /**
         * 기어가 바뀌면 알린다.
         *
         * @param gear Pleos 기어 값
         */
        override fun onSelected(gear: VehicleGear?) = notify(PleosSignalValues.gearOf(gear))

        /**
         * 구독 오류를 기록하고 알 수 없음을 알린다.
         *
         * @param exception 오류
         */
        override fun onFailed(exception: Exception) {
            log.report(TAG, "gear subscription failed", exception)
            notify(Gear.UNKNOWN)
        }
    }

    /**
     * 구독을 시작한다.
     *
     * @param listener 기어를 받을 함수
     */
    override fun start(listener: (Gear) -> Unit) {
        this.listener = listener
        listener(Gear.UNKNOWN)
        val gear = sdk()
        gear.getCurrentGearSelectionState(
            { notify(PleosSignalValues.gearOf(it)) },
            { log.report(TAG, "gear read failed", it) },
        )
        gear.registerGearSelection(callback)
    }

    /** 구독을 멈춘다. 멈춘 뒤에는 알리지 않는다. */
    override fun stop() {
        listener = null
        sdk().unregisterGearSelection(callback)
    }

    /**
     * 구독 중이면 엔진 쪽 스레드에서 기어를 알린다. 옮기는 사이 구독을 멈췄으면 알리지 않는다.
     *
     * @param gear 알릴 기어
     */
    private fun notify(gear: Gear) {
        main.post { listener?.invoke(gear) }
    }

    private companion object {
        const val TAG = "CarGearSource"
    }
}
