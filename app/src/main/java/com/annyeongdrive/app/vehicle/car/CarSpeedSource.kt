package com.annyeongdrive.app.vehicle.car

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.annyeongdrive.app.vehicle.SpeedSource
import com.annyeongdrive.app.vehicle.SpeedUnits

/**
 * 실제 차속 신호(android.car PERF_VEHICLE_SPEED)를 알려 주는 입력 어댑터. release 빌드가 쓴다.
 * Pleos Vehicle SDK(2.3.2)에는 차속 API가 없어 Android 차량 라이브러리를 쓴다(11/14 확인).
 * 값을 km/h로 바꾸기만 한다([SpeedUnits.kmhOf]). 차속을 모르면 알리지 않는다(엔진은 차속을 받기 전까지 정차·주행으로 보지 않는다).
 * 차속 권한(CAR_SPEED, 실행 중 허용)이 없거나 차량용 기기가 아니면 구독하지 않는다.
 *
 * @param context 앱 Context
 */
class CarSpeedSource(context: Context) : SpeedSource {

    private val appContext = context.applicationContext
    private var listener: ((Float) -> Unit)? = null
    private var car: Car? = null
    private var manager: CarPropertyManager? = null

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {

        /**
         * 차속이 바뀌면 km/h로 알린다. 값이 비었거나 사용할 수 없는 상태면 알리지 않는다.
         *
         * @param value 차속 값(m/s)
         */
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            if (value.status != CarPropertyValue.STATUS_AVAILABLE) return
            val metersPerSecond = value.value as? Float ?: return
            listener?.invoke(SpeedUnits.kmhOf(metersPerSecond))
        }

        /**
         * 차속을 읽지 못하면 기록한다.
         *
         * @param propertyId 속성 ID
         * @param zone 영역
         */
        override fun onErrorEvent(propertyId: Int, zone: Int) {
            Log.e(TAG, "speed error: $propertyId/$zone")
        }
    }

    /**
     * 구독을 시작한다. 차량 서비스에 연결되면 현재 값과 바뀐 값을 알린다.
     *
     * @param listener 차속(km/h)을 받을 함수
     */
    override fun start(listener: (Float) -> Unit) {
        this.listener = listener
        if (!canRead()) {
            Log.w(TAG, "speed not available: no automotive feature or CAR_SPEED permission")
            return
        }
        car = Car.createCar(
            appContext,
            Handler(Looper.getMainLooper()),
            Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT,
        ) { connected, ready -> onCarLifecycle(connected, ready) }
    }

    /** 구독을 멈추고 차량 서비스 연결을 끊는다. 멈춘 뒤에는 알리지 않는다. */
    override fun stop() {
        listener = null
        manager?.unregisterCallback(callback)
        manager = null
        car?.disconnect()
        car = null
    }

    /**
     * 차량 서비스 연결 상태가 바뀌면 부른다. 연결되면 차속을 구독하고, 끊기면 구독을 놓는다.
     *
     * @param connected 연결된 차량 서비스
     * @param ready 사용할 수 있으면 true
     */
    @Suppress("DEPRECATION")
    private fun onCarLifecycle(connected: Car, ready: Boolean) {
        if (!ready) {
            manager = null
            return
        }
        val properties = connected.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager ?: return
        manager = properties
        properties.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_ONCHANGE)
    }

    /**
     * 차속을 읽을 수 있는지 알려 준다.
     *
     * @return 차량용 기기이고 차속 권한이 있으면 true
     */
    private fun canRead(): Boolean {
        val automotive = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        val granted = ContextCompat.checkSelfPermission(appContext, Car.PERMISSION_SPEED) == PackageManager.PERMISSION_GRANTED
        return automotive && granted
    }

    private companion object {
        const val TAG = "CarSpeedSource"
    }
}
