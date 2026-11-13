package com.annyeongdrive.app.debugtools

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.annyeongdrive.app.AnnyeongApplication
import com.annyeongdrive.app.location.LocationService
import com.annyeongdrive.app.location.LocationState
import com.annyeongdrive.app.speech.PleosTtsSpeaker
import com.annyeongdrive.app.service.DriveService
import com.annyeongdrive.app.speech.SpeechHub
import com.annyeongdrive.app.ui.theme.Annyeong_driveTheme
import java.util.Locale

/**
 * 기능 확인용 화면(debug 빌드 전용). 가짜 신호 조작과 운전 상태, 위치 수신·구역 사건·TTS 언어를 보여 준다.
 * 위치·TTS 확인은 11/8 기능 확인 때의 MainActivity를 그대로 옮긴 것이다. release APK에는 들어가지 않는다.
 */
class CheckActivity : ComponentActivity() {

    // 기능 확인 ③: TTS 언어 지원 확인용
    private var checkResultListener: SpeechHub.ResultListener? = null
    private var checkReadyListener: SpeechHub.ReadyListener? = null
    private val ttsResult = mutableStateOf("")

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            Log.d(LocationService.TAG, "permission result: $result")
            startServiceIfAllowed()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Annyeong_driveTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(Modifier.padding(innerPadding).verticalScroll(rememberScrollState())) {
                        SignalPanel((application as AnnyeongApplication).runtime)
                        LocationStatus()
                        TtsCheck(result = ttsResult.value, onClick = { runTtsCheck() })
                    }
                }
            }
        }

        val missing = REQUIRED_PERMISSIONS.filterNot(::isGranted)
        if (missing.isEmpty()) startServiceIfAllowed() else permissionLauncher.launch(missing.toTypedArray())
    }

    /** 기능 확인 ③: 서비스와 같은 Pleos TTS 객체로 세 언어를 읽어 본다. */
    private fun runTtsCheck() {
        removeCheckListeners()
        val results = linkedMapOf<Locale, String>()
        TTS_SAMPLES.forEach { (locale, _) -> results[locale] = "대기" }
        fun show() {
            ttsResult.value = results.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        }
        ttsResult.value = "Pleos TTS 준비 중…"

        val resultListener = SpeechHub.ResultListener { text, locale, err ->
            // 주행 안내 문장은 무시하고 확인용 문장 결과만 본다
            if (TTS_SAMPLES.any { it.first == locale && it.second == text }) {
                results[locale] = if (err == null) "읽기 완료" else "실패($err)"
                show()
                Log.i(PleosTtsSpeaker.TAG, "TTS check $locale -> ${err ?: "OK"}")
            }
        }
        val readyListener = SpeechHub.ReadyListener { ok, err ->
            if (!ok) ttsResult.value = "Pleos TTS 초기화 실패: $err" else show()
        }
        checkResultListener = resultListener
        checkReadyListener = readyListener
        SpeechHub.addResultListener(resultListener)
        SpeechHub.addReadyListener(readyListener)

        val speaker = SpeechHub.obtain(this)
        TTS_SAMPLES.forEach { (locale, text) -> speaker.speak(text, locale) }
    }

    private fun removeCheckListeners() {
        checkResultListener?.let(SpeechHub::removeResultListener)
        checkReadyListener?.let(SpeechHub::removeReadyListener)
        checkResultListener = null
        checkReadyListener = null
    }

    override fun onDestroy() {
        removeCheckListeners()
        super.onDestroy()
    }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun startServiceIfAllowed() {
        // location 타입 Foreground Service는 Android 위치 권한이 있어야 시작된다
        val androidLocation = isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
        val pleosLocation = isGranted(PLEOS_FUSED_LOCATION)
        Log.d(LocationService.TAG, "androidLocation=$androidLocation pleosLocation=$pleosLocation")
        if (androidLocation) {
            LocationService.start(this)
            DriveService.start(this)
        } else {
            LocationState.fail("위치 권한이 거부되었다")
        }
    }

    companion object {
        /** 확인용 짧은 문장. 일본어·번체는 원어민 검토 전 임시 문장이다. */
        private val TTS_SAMPLES = listOf(
            Locale.US to "School zone. Speed limit 30.",
            Locale.JAPAN to "スクールゾーンです。制限速度は30キロです。",
            Locale.TAIWAN to "學區路段，速限三十公里。",
        )
        private const val PLEOS_FUSED_LOCATION = "pleos.car.permission.FUSED_LOCATION"
        private const val PLEOS_TTS = "pleos.car.permission.TTS_SERVICE"
        private val REQUIRED_PERMISSIONS = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            PLEOS_FUSED_LOCATION,
            PLEOS_TTS,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }
}

@Composable
fun TtsCheck(result: String, onClick: () -> Unit) {
    Column(Modifier.padding(horizontal = 24.dp)) {
        Button(onClick = onClick) { Text("TTS 언어 확인 (영·일·번체)") }
        if (result.isNotEmpty()) Text(result)
    }
}

/** 개발 확인용 화면: 위치가 들어오는지 숫자로 보여 준다. */
@Composable
fun LocationStatus(modifier: Modifier = Modifier) {
    val latest by LocationState.latest.collectAsState()
    val count by LocationState.count.collectAsState()
    val error by LocationState.error.collectAsState()
    val events by LocationState.events.collectAsState()

    Column(modifier = modifier.padding(24.dp)) {
        Text("Annyeong Drive · 위치 확인", style = MaterialTheme.typography.titleLarge)
        Text("수신 횟수: $count")
        val loc = latest
        if (loc == null) {
            Text("위치를 기다리는 중")
        } else {
            Text(String.format(Locale.US, "위도 %.6f, 경도 %.6f", loc.latitude, loc.longitude))
            Text(String.format(Locale.US, "방향 %.1f°", loc.heading))
        }
        error?.let { Text("오류: $it", color = MaterialTheme.colorScheme.error) }

        Text("구역 사건", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        if (events.isEmpty()) Text("아직 없음")
        events.forEach { Text("· $it") }
    }
}
