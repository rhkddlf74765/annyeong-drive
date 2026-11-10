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
import com.annyeongdrive.app.guide.EncounterBasedPolicy
import com.annyeongdrive.app.guide.EnglishPhrasebook
import com.annyeongdrive.app.guide.Utterance
import com.annyeongdrive.app.speech.SpeechHub
import com.annyeongdrive.app.speech.Speaker
import com.annyeongdrive.app.session.DriveSession
import com.annyeongdrive.app.session.JumpTripBoundary
import com.annyeongdrive.app.session.TripBoundary
import com.annyeongdrive.app.zones.ZoneData
import com.annyeongdrive.app.zones.ZoneEvent
import com.annyeongdrive.app.zones.ZoneLoader
import java.util.Locale

/**
 * 앱이 화면에 없어도 위치를 계속 받는 Foreground Service.
 * Pleos FusedLocation에서 위치를 받아 LocationState에 넣고 로그로 남긴다.
 */
class LocationService : Service() {

    private var fusedLocation: FusedLocation? = null
    private var zones: ZoneData? = null
    private var speaker: Speaker? = null
    private val tripBoundary: TripBoundary = JumpTripBoundary()
    private var session: DriveSession? = null
    private var sessionCount = 0

    private val ttsReadyListener = SpeechHub.ReadyListener { ok, err ->
        if (!ok) LocationState.fail("Pleos TTS 사용 불가: $err")
    }

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
            onLocation(data.latitude, data.longitude, data.timestampMillis)
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

        zones = try {
            ZoneLoader.load(applicationContext).also {
                Log.d(
                    ZONE_TAG,
                    "zones loaded: school=${it.schoolZones.size} speed=${it.speedCameras.size} parking=${it.parkingCameras.size}"
                )
            }
        } catch (e: Exception) {
            Log.e(ZONE_TAG, "zones.json load failed", e)
            null
        }

        // TTS가 실패해도 서비스(위치·판단기)는 계속 돌아야 한다.
        // 에뮬레이터에는 Android 기본 TTS 엔진이 없어 Pleos TTS를 쓴다(11/8 확인).
        // Pleos TTS 객체는 앱 전체에서 하나만 쓴다(SpeechHub)
        val sp = SpeechHub.obtain(applicationContext)
        SpeechHub.addReadyListener(ttsReadyListener)
        speaker = sp

        val fl = FusedLocation(applicationContext)
        fusedLocation = fl
        fl.initialize()
        Log.d(TAG, "FusedLocation initialized, supported=${fl.isSupported()}")
        fl.registerFusedLocationCallback(listener)
    }

    /** 위치마다: 새 주행인지 보고, 현재 주행에 위치를 넘긴다. */
    private fun onLocation(lat: Double, lon: Double, timeMs: Long) {
        val z = zones ?: return
        val sp = speaker ?: return
        if (tripBoundary.isNewTrip(lat, lon, timeMs) || session == null) {
            sessionCount += 1
            // 이전 주행의 안내는 새 주행과 상관없으므로 바로 끊는다
            if (session != null) sp.stopAll()
            // 문장 길이 규칙은 정책으로 바꿔 끼운다. 기본: 주행마다 처음 1번 2문장, 이후 1문장
            session = DriveSession(sessionCount, timeMs, z, EnglishPhrasebook, EncounterBasedPolicy(), sp)
            val jump = (tripBoundary as? JumpTripBoundary)?.lastJumpM?.toInt() ?: 0
            val text = "새 주행 #$sessionCount 시작 (위치 이동 ${jump}m)"
            Log.i(ZONE_TAG, text)
            LocationState.addEvent(text)
        }
        session?.onLocation(lat, lon, timeMs)?.forEach { (event, utterance) -> onZoneEvent(event, utterance) }
    }

    private fun onZoneEvent(event: ZoneEvent, utterance: Utterance?) {
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

        utterance?.let { u ->
            Log.i(GUIDE_TAG, "[${u.rule} #${u.encounter} ${u.roles}] ${u.text}")
            LocationState.addEvent("🔊 ${u.text}")
            updateNotification(u.text)
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        fusedLocation?.let {
            it.unregisterFusedLocationCallback(listener)
            it.release()
        }
        fusedLocation = null
        SpeechHub.removeReadyListener(ttsReadyListener)
        SpeechHub.release()
        speaker = null
        session = null
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

    private fun buildNotification(text: String = "주행 안내 준비 중") =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Annyeong Drive")
            .setContentText(text)
            .setOngoing(true)
            .build()

    companion object {
        const val TAG = "AnnyeongLoc"
        const val ZONE_TAG = "AnnyeongZone"
        const val GUIDE_TAG = "AnnyeongGuide"
        private const val CHANNEL_ID = "drive_guide"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, LocationService::class.java))
        }
    }
}
