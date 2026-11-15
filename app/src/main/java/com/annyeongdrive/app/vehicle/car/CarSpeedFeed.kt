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

/**
 * android.car의 PERF_VEHICLE_SPEED(m/s)를 받아 넘기는 차량 연결. [CarSpeedSource]가 release에서 쓴다.
 * 콜백은 메인 스레드에서 온다(연결할 때 메인 Handler를 넘긴다).
 *
 * @param context 앱 Context
 */
class CarSpeedFeed(context: Context) : SpeedFeed {

    private val appContext = context.applicationContext
    private var onValue: ((Float?) -> Unit)? = null
    private var car: Car? = null
    private var manager: CarPropertyManager? = null

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {

        /**
         * 차속이 바뀌면 m/s 값을 넘긴다. 값이 비었거나 사용할 수 없는 상태면 null을 넘긴다.
         *
         * @param value 차속 값(m/s)
         */
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val available = value.status == CarPropertyValue.STATUS_AVAILABLE
            val metersPerSecond = value.value as? Float
            val reading = metersPerSecond.takeIf { available }
            onValue?.invoke(reading)
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
     * 차속을 읽을 수 있는지 알려 준다.
     *
     * @return 차량용 기기이고 차속 권한이 있으면 true
     */
    override fun canRead(): Boolean {
        val automotive = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        val granted = ContextCompat.checkSelfPermission(appContext, Car.PERMISSION_SPEED) == PackageManager.PERMISSION_GRANTED
        return automotive && granted
    }

    /**
     * 차량 서비스에 연결해 차속 구독을 시작한다.
     *
     * @param onMetersPerSecond 차속 원값(m/s)을 받을 함수
     */
    override fun subscribe(onMetersPerSecond: (Float?) -> Unit) {
        onValue = onMetersPerSecond
        car = Car.createCar(
            appContext,
            Handler(Looper.getMainLooper()),
            Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT,
        ) { connected, ready -> onCarLifecycle(connected, ready) }
    }

    /** 구독을 멈추고 차량 서비스 연결을 끊는다. */
    override fun unsubscribe() {
        onValue = null
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

    private companion object {
        const val TAG = "CarSpeedFeed"
    }
}
