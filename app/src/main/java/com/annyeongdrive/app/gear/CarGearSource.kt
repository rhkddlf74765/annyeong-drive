package com.annyeongdrive.app.gear

import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.util.Log
import com.annyeongdrive.app.vehicle.pleos.PleosSignalValues
import com.annyeongdrive.app.vehicle.pleos.PleosVehicle

/**
 * 실제 차량 기어 신호(Pleos Vehicle SDK의 기어 선택)를 알려 주는 입력 어댑터. release 빌드가 쓴다.
 * 값을 [PleosSignalValues.gearOf]로 옮기기만 하고 판단하지 않는다. 시작하면 알 수 없음을 먼저 알리고, 현재 값과 바뀐 값을 알린다.
 * 받지 못하면(오류) 알 수 없음을 알린다. 모르는 기어를 P로 보지 않는다.
 *
 * @param context 앱 Context
 */
class CarGearSource(context: Context) : GearSource {

    private val appContext = context.applicationContext
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
            Log.e(TAG, "gear subscription failed", exception)
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
        val gear = PleosVehicle.obtain(appContext).getGear()
        gear.getCurrentGearSelectionState(
            { notify(PleosSignalValues.gearOf(it)) },
            { Log.e(TAG, "gear read failed", it) },
        )
        gear.registerGearSelection(callback)
    }

    /** 구독을 멈춘다. 멈춘 뒤에는 알리지 않는다. */
    override fun stop() {
        listener = null
        PleosVehicle.obtain(appContext).getGear().unregisterGearSelection(callback)
    }

    /**
     * 구독 중이면 메인 스레드에서 기어를 알린다.
     *
     * @param gear 알릴 기어
     */
    private fun notify(gear: Gear) {
        PleosVehicle.onMain { listener?.invoke(gear) }
    }

    private companion object {
        const val TAG = "CarGearSource"
    }
}
