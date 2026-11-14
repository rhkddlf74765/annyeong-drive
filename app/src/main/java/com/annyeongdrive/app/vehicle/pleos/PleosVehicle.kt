package com.annyeongdrive.app.vehicle.pleos

import ai.pleos.playground.vehicle.Vehicle
import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * 앱 전체에서 하나인 Pleos Vehicle SDK 연결. 기어·착좌·안전띠 어댑터가 함께 쓴다.
 * 처음 쓸 때 연결하고 앱이 끝날 때까지 둔다(어댑터의 stop은 구독만 멈춘다).
 * SDK 콜백은 다른 스레드에서 올 수 있어, 엔진에 넘기기 전에 [onMain]으로 메인 스레드에 옮긴다.
 */
object PleosVehicle {

    private var vehicle: Vehicle? = null
    private val main = Handler(Looper.getMainLooper())

    /**
     * 연결된 SDK 객체를 돌려준다. 처음이면 연결한다.
     *
     * @param context 앱 Context
     * @return Pleos Vehicle SDK 객체
     */
    @Synchronized
    fun obtain(context: Context): Vehicle {
        val existing = vehicle
        if (existing != null) return existing
        val created = Vehicle(context.applicationContext)
        created.initialize()
        vehicle = created
        return created
    }

    /**
     * 할 일을 메인 스레드에서 실행한다. 엔진은 한 스레드에서만 이벤트를 받는다.
     *
     * @param block 실행할 일
     */
    fun onMain(block: () -> Unit) {
        main.post(block)
    }
}
