package com.annyeongdrive.app.location

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.util.Log
import com.annyeongdrive.app.time.Clock
import java.time.Instant

/**
 * Pleos FusedLocation에서 위치를 받아 알려 주는 입력 어댑터. debug·release 모두 쓴다(에뮬레이터 GPX로 시연).
 * 판단하지 않고 값만 옮긴다. 다만 [LocationFreshness] 기준보다 오래된 위치는 알리지 않는다.
 * 위치 권한을 받은 뒤(서비스 시작 후) 구독을 시작한다.
 *
 * @param context 앱 Context
 * @param clock 지금 시각
 */
class FusedLocationSource(context: Context, private val clock: Clock) : LocationSource {

    private val appContext = context.applicationContext
    private var fused: FusedLocation? = null
    private var listener: ((GeoPoint) -> Unit)? = null

    private val callback = object : FusedLocationListener {

        /**
         * 새 위치를 받으면 새것일 때만 알린다.
         *
         * @param location 위치. 없으면 null
         */
        override fun onChanged(location: LocationData?) {
            val data = location ?: return
            if (!LocationFreshness.isFresh(Instant.ofEpochMilli(data.timestampMillis), clock.now())) return
            listener?.invoke(GeoPoint(data.latitude, data.longitude))
        }

        /**
         * 위치 오류를 기록한다. 판단에는 쓰지 않는다.
         *
         * @param exception 오류
         */
        override fun onError(exception: Exception) {
            Log.e(TAG, "location error", exception)
        }
    }

    /**
     * 구독을 시작한다. 위치는 처음에 없으므로 첫 위치가 올 때 알린다.
     *
     * @param listener 위치를 받을 함수
     */
    override fun start(listener: (GeoPoint) -> Unit) {
        this.listener = listener
        val location = FusedLocation(appContext)
        location.initialize()
        location.registerFusedLocationCallback(callback)
        fused = location
    }

    /** 구독을 멈추고 위치 서비스를 놓는다. */
    override fun stop() {
        listener = null
        fused?.unregisterFusedLocationCallback(callback)
        fused?.release()
        fused = null
    }

    private companion object {
        const val TAG = "FusedLocationSource"
    }
}
