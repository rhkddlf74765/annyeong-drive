package com.annyeongdrive.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.annyeongdrive.app.service.DriveService
import com.annyeongdrive.app.ui.theme.Annyeong_driveTheme

/**
 * 앱의 첫 화면. 지금은 임시 홈이다.
 * 위치·알림 권한을 받은 뒤 주행 안내 서비스(DriveService)를 시작한다. 위치 권한이 없으면 서비스를 시작하지 않는다.
 * 5단계부터 Coordinator의 현재 상태(engine.state)를 받아 상태별 실제 화면을 그린다.
 * 기능 확인용 화면은 debug 빌드의 CheckActivity에 있다.
 */
class MainActivity : ComponentActivity() {

    /** 임시 홈에 보여 줄 문구 ID. 권한 결과에 따라 바뀐다. */
    private val messageId = mutableIntStateOf(R.string.home_preparing)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { startServiceIfAllowed() }

    /**
     * 임시 홈을 그리고, 필요한 권한을 요청한 뒤 서비스를 시작한다.
     *
     * @param savedInstanceState 저장된 화면 상태
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Annyeong_driveTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeMessage(messageId.intValue, Modifier.padding(innerPadding))
                }
            }
        }
        val missing = REQUIRED_PERMISSIONS.filterNot(::isGranted)
        if (missing.isEmpty()) {
            startServiceIfAllowed()
            return
        }
        permissionLauncher.launch(missing.toTypedArray())
    }

    /** 위치 권한이 있으면 주행 안내 서비스를 시작하고, 없으면 권한이 필요하다는 문구를 보여 준다. */
    private fun startServiceIfAllowed() {
        if (!hasLocationPermission()) {
            messageId.intValue = R.string.home_location_needed
            return
        }
        messageId.intValue = R.string.home_preparing
        DriveService.start(this)
    }

    /**
     * location 타입 Foreground Service를 시작할 수 있는지 판단한다. 정밀·대략 위치 중 하나면 된다.
     *
     * @return 위치 권한이 있으면 true
     */
    private fun hasLocationPermission(): Boolean =
        isGranted(Manifest.permission.ACCESS_FINE_LOCATION) || isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

    /**
     * 권한을 받았는지 알려 준다.
     *
     * @param permission 권한 이름
     * @return 받았으면 true
     */
    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val PLEOS_FUSED_LOCATION = "pleos.car.permission.FUSED_LOCATION"
        const val PLEOS_TTS = "pleos.car.permission.TTS_SERVICE"

        /** 서비스 시작 전에 요청하는 권한. 위치는 location 타입 서비스, 알림은 상주 알림, Pleos는 위치·음성 SDK용이다. */
        val REQUIRED_PERMISSIONS = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
            PLEOS_FUSED_LOCATION,
            PLEOS_TTS,
        )
    }
}

/**
 * 임시 홈 문구. 화면 문구는 strings.xml의 문구 ID로 읽는다.
 *
 * @param messageId 보여 줄 문구 ID
 * @param modifier 바깥에서 주는 배치 설정
 */
@Composable
fun HomeMessage(@StringRes messageId: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(messageId),
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(24.dp),
    )
}

/** 임시 홈 미리보기. */
@Preview(showBackground = true)
@Composable
private fun HomeMessagePreview() {
    Annyeong_driveTheme {
        HomeMessage(R.string.home_preparing)
    }
}
