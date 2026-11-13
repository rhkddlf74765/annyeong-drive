package com.annyeongdrive.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.annyeongdrive.app.AnnyeongApplication
import com.annyeongdrive.app.R
import kotlinx.coroutines.launch

/**
 * 앱이 화면에 없어도 운전 판단을 계속하는 Foreground Service(location 타입).
 * 상주 알림을 띄우고 엔진의 시작·멈춤만 맡는다. 판단은 엔진과 Coordinator가 한다.
 */
class DriveService : Service() {

    /**
     * 서비스를 앞으로 올리고 앱 시작 순서(저장소 읽기 → AppStarted → 신호 구독)를 진행한다.
     * 위치 권한이 없으면 시작하지 않고 멈춘다.
     */
    override fun onCreate() {
        super.onCreate()
        createChannel()
        val started = startInForeground()
        if (!started) {
            stopSelf()
            return
        }
        val runtime = runtime()
        runtime.scope.launch { runtime.startup.start() }
    }

    /**
     * 시스템이 서비스를 죽였다가 다시 살릴 수 있게 한다.
     *
     * @param intent 시작 요청
     * @param flags 시작 플래그
     * @param startId 시작 번호
     * @return 다시 살리기 방식
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    /** 신호 구독을 멈춘다. 저장소 내용과 운전 상태는 프로세스가 살아 있는 동안 남는다. */
    override fun onDestroy() {
        runtime().startup.stop()
        super.onDestroy()
    }

    /**
     * 바인딩은 쓰지 않는다.
     *
     * @param intent 바인딩 요청
     * @return 항상 null
     */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 상주 알림과 함께 서비스를 앞으로 올린다.
     *
     * @return 올렸으면 true. 위치 권한이 없어 실패하면 false
     */
    private fun startInForeground(): Boolean = try {
        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        true
    } catch (e: SecurityException) {
        Log.e(TAG, "startForeground failed: location permission missing", e)
        false
    }

    /** 상주 알림 채널을 만든다. 이미 있으면 그대로 둔다. */
    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.drive_service_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * 상주 알림을 만든다.
     *
     * @return 알림
     */
    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.drive_service_running))
            .setOngoing(true)
            .build()

    /**
     * 조립 지점을 가져온다.
     *
     * @return 앱 전체에서 하나인 DriveRuntime
     */
    private fun runtime(): DriveRuntime = (application as AnnyeongApplication).runtime

    companion object {
        private const val TAG = "AnnyeongDrive"
        private const val CHANNEL_ID = "drive_service"
        private const val NOTIFICATION_ID = 2001

        /**
         * 서비스를 시작한다. 위치 권한을 받은 뒤에 부른다.
         *
         * @param context 아무 Context
         */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, DriveService::class.java))
        }
    }
}
