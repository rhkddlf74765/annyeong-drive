package com.annyeongdrive.app.vehicle.car

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import com.annyeongdrive.app.vehicle.UxRestriction
import com.annyeongdrive.app.vehicle.UxRestrictionSource

/**
 * 실제 주행 중 화면 제한 신호(android.car CarUxRestrictionsManager)를 알려 주는 입력 어댑터. release 빌드가 쓴다.
 * 값을 [UxRestriction.of]로 옮기기만 하고 판단하지 않는다. 차량 서비스에 연결되기 전이나 연결이 끊기면 알 수 없음을 알린다.
 * 차량이 아닌 기기(android.car 없음)에서는 연결하지 않고 알 수 없음만 알린다.
 * Pleos Vehicle SDK(2.3.2)에는 화면 제한 API가 없어 Android 차량 라이브러리를 쓴다(7단계).
 *
 * @param context 앱 Context
 */
class CarUxRestrictionSource(context: Context) : UxRestrictionSource {

    private val appContext = context.applicationContext
    private var listener: ((UxRestriction) -> Unit)? = null
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    /**
     * 구독을 시작한다. 연결 전 값으로 알 수 없음을 한 번 알리고, 차량 서비스에 연결되면 현재 값과 바뀐 값을 알린다.
     *
     * @param listener 제한 상태를 받을 함수
     */
    override fun start(listener: (UxRestriction) -> Unit) {
        this.listener = listener
        listener(UxRestriction.UNKNOWN)
        if (!isAutomotive()) return
        car = Car.createCar(
            appContext,
            Handler(Looper.getMainLooper()),
            Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT,
        ) { connected, ready -> onCarLifecycle(connected, ready) }
    }

    /** 구독을 멈추고 차량 서비스 연결을 끊는다. 멈춘 뒤에는 알리지 않는다. */
    override fun stop() {
        listener = null
        manager?.unregisterListener()
        manager = null
        car?.disconnect()
        car = null
    }

    /**
     * 차량 서비스 연결 상태가 바뀌면 부른다. 연결되면 제한 신호를 구독하고, 끊기면 알 수 없음을 알린다.
     *
     * @param connected 연결된 차량 서비스
     * @param ready 사용할 수 있으면 true
     */
    private fun onCarLifecycle(connected: Car, ready: Boolean) {
        if (!ready) {
            manager = null
            notify(UxRestriction.UNKNOWN)
            return
        }
        val uxManager = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
        manager = uxManager
        if (uxManager == null) {
            notify(UxRestriction.UNKNOWN)
            return
        }
        uxManager.registerListener { notify(restrictionOf(it)) }
        notify(restrictionOf(uxManager.currentCarUxRestrictions))
    }

    /**
     * 플랫폼 제한 값을 앱의 제한 상태로 옮긴다.
     *
     * @param restrictions 플랫폼 제한 값. 없으면 null
     * @return 제한 상태
     */
    private fun restrictionOf(restrictions: CarUxRestrictions?): UxRestriction =
        UxRestriction.of(restrictions?.isRequiresDistractionOptimization)

    /**
     * 구독 중이면 제한 상태를 알린다.
     *
     * @param restriction 알릴 제한 상태
     */
    private fun notify(restriction: UxRestriction) {
        listener?.invoke(restriction)
    }

    /**
     * 차량용 기기인지 알려 준다. 차량용이 아니면 android.car 클래스를 쓰지 않는다.
     *
     * @return 차량용 기기면 true
     */
    private fun isAutomotive(): Boolean =
        appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
}
