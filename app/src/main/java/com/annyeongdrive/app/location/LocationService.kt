package com.annyeongdrive.app.location

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.annyeongdrive.app.R
import com.annyeongdrive.app.zones.ZoneDetector
import com.annyeongdrive.app.zones.ZoneEvent
import com.annyeongdrive.app.zones.ZoneLoader
import java.util.Locale

/**
 * 앱이 화면에 없어도 위치를 계속 받는 Foreground Service.
 * Pleos FusedLocation에서 위치를 받아 LocationState에 넣고 로그로 남긴다.
 */
class LocationService : Service() {

    private var fusedLocation: FusedLocation? = null
    private var zoneDetector: ZoneDetector? = null

    private val listener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            val data = location ?: return
            LocationState.update(data)
            Log.d(
                TAG,
                String.format(
                    Locale.US, "loc lat=%.6f lon=%.6f heading=%.1f conf=%.1f",
                    data.latitude, data.longitude, data.heading, data.confidence
                )
            )
            zoneDetector
                ?.update(data.latitude, data.longitude, data.timestampMillis)
                ?.forEach { onZoneEvent(it) }
        }

        override fun onError(e: Exception) {
            Log.e(TAG, "location error", e)
            LocationState.fail(e.message ?: e.javaClass.simpleName)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        try {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } catch (e: SecurityException) {
            // 위치 권한 없이 location 타입으로 시작하면 여기로 온다
            Log.e(TAG, "startForeground failed: location permission missing", e)
            LocationState.fail("위치 권한이 없어 서비스를 시작하지 못했다")
            stopSelf()
            return
        }

        zoneDetector = try {
            val zones = ZoneLoader.load(applicationContext)
            Log.d(
                ZONE_TAG,
                "zones loaded: school=${zones.schoolZones.size} speed=${zones.speedCameras.size} parking=${zones.parkingCameras.size}"
            )
            ZoneDetector(zones)
        } catch (e: Exception) {
            Log.e(ZONE_TAG, "zones.json load failed", e)
            null
        }

        val fl = FusedLocation(applicationContext)
        fusedLocation = fl
        fl.initialize()
        Log.d(TAG, "FusedLocation initialized, supported=${fl.isSupported()}")
        fl.registerFusedLocationCallback(listener)
    }

    /** 지금은 로그와 화면 기록만 남긴다. 다음 단계에서 안내 관리자로 넘긴다. */
    private fun onZoneEvent(event: ZoneEvent) {
        val text = when (event) {
            is ZoneEvent.SchoolZoneEntered ->
                "어린이보호구역 진입: ${event.zone.name} (${event.distanceM.toInt()}m), 제한 ${event.zone.speedLimitKmh}"
            ZoneEvent.SchoolZoneExited -> "어린이보호구역 이탈"
            is ZoneEvent.SpeedCameraAhead ->
                "과속 카메라 ${event.camera.speedLimitKmh}km/h 앞 ${event.distanceM.toInt()}m: ${event.camera.place}"
            is ZoneEvent.StoppedNearParkingCamera ->
                "정차, 주정차 단속 카메라 ${event.distanceM.toInt()}m: ${event.camera.place}"
        }
        Log.i(ZONE_TAG, text)
        LocationState.addEvent(text)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        fusedLocation?.let {
            it.unregisterFusedLocationCallback(listener)
            it.release()
        }
        fusedLocation = null
        Log.d(TAG, "LocationService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "주행 안내", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Annyeong Drive")
            .setContentText("주행 안내 준비 중")
            .setOngoing(true)
            .build()

    companion object {
        const val TAG = "AnnyeongLoc"
        const val ZONE_TAG = "AnnyeongZone"
        private const val CHANNEL_ID = "drive_guide"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, LocationService::class.java))
        }
    }
}
