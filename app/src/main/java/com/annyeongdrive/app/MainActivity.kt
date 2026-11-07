package com.annyeongdrive.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
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
import com.annyeongdrive.app.location.LocationService
import com.annyeongdrive.app.location.LocationState
import com.annyeongdrive.app.ui.theme.Annyeong_driveTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

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
                    LocationStatus(Modifier.padding(innerPadding))
                }
            }
        }

        val missing = REQUIRED_PERMISSIONS.filterNot(::isGranted)
        if (missing.isEmpty()) startServiceIfAllowed() else permissionLauncher.launch(missing.toTypedArray())
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
        } else {
            LocationState.fail("위치 권한이 거부되었다")
        }
    }

    companion object {
        private const val PLEOS_FUSED_LOCATION = "pleos.car.permission.FUSED_LOCATION"
        private val REQUIRED_PERMISSIONS = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            PLEOS_FUSED_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }
}

/** 개발 확인용 화면: 위치가 들어오는지 숫자로 보여 준다. */
@Composable
fun LocationStatus(modifier: Modifier = Modifier) {
    val latest by LocationState.latest.collectAsState()
    val count by LocationState.count.collectAsState()
    val error by LocationState.error.collectAsState()

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
    }
}
