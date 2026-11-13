package com.annyeongdrive.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.drive.state.ParkedRest
import com.annyeongdrive.app.drive.state.ParkedVendorSettings
import com.annyeongdrive.app.drive.state.Returning
import com.annyeongdrive.app.service.DriveRuntime
import com.annyeongdrive.app.service.DriveService
import com.annyeongdrive.app.ui.admin.ReturnScreen
import com.annyeongdrive.app.ui.admin.VendorSettingsActions
import com.annyeongdrive.app.ui.admin.VendorSettingsView
import com.annyeongdrive.app.ui.theme.Annyeong_driveTheme
import kotlinx.coroutines.launch

/**
 * 앱의 첫 화면. 위치·알림 권한을 받은 뒤 주행 안내 서비스(DriveService)를 시작하고,
 * Coordinator의 현재 운전 상태(engine.state)만 보고 화면을 고른다. 기어·차속을 직접 보지 않는다.
 * 지금은 정차 대기·반납 처리·업체 설정 화면이 있고, 나머지 상태의 화면은 5~7단계에서 붙인다.
 * 기능 확인용 화면은 debug 빌드의 CheckActivity에 있다.
 */
class MainActivity : ComponentActivity() {

    /** 권한 안내 문구 ID. 권한 결과에 따라 바뀐다. */
    private val messageId = mutableIntStateOf(R.string.home_preparing)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { startServiceIfAllowed() }

    /**
     * 운전 상태별 화면을 그리고, 필요한 권한을 요청한 뒤 서비스를 시작한다.
     *
     * @param savedInstanceState 저장된 화면 상태
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val runtime = (application as AnnyeongApplication).runtime
        setContent {
            Annyeong_driveTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DriveScreen(runtime, messageId.intValue, Modifier.padding(innerPadding))
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
 * 운전 상태에 맞는 화면을 고른다. 상태가 바뀌면 반납·업체 설정 흐름에 알려 첫 단계를 정하거나 닫게 한다.
 *
 * @param runtime 앱 전체에서 하나인 조립 지점
 * @param messageId 그 밖의 상태에서 보여 줄 문구 ID
 * @param modifier 바깥에서 주는 배치 설정
 */
@Composable
private fun DriveScreen(runtime: DriveRuntime, @StringRes messageId: Int, modifier: Modifier) {
    val driveState by runtime.engine.state.collectAsState()
    val returnScreen by runtime.returnFlow.screen.collectAsState()
    val vendorScreen by runtime.vendorSettingsFlow.screen.collectAsState()
    val scope = rememberCoroutineScope()
    val vendorFlow = runtime.vendorSettingsFlow

    LaunchedEffect(driveState) {
        vendorFlow.syncWithDrive()
        if (driveState == Returning) runtime.returnFlow.open()
        if (driveState == ParkedVendorSettings) vendorFlow.open()
    }

    when (driveState) {
        ParkedRest -> ParkedHome(
            onReturn = { runtime.engine.send(DriveEvent.ReturnRequested) },
            onVendorSettings = { runtime.engine.send(DriveEvent.VendorSettingsRequested) },
            modifier = modifier,
        )
        Returning -> ReturnScreen(
            state = returnScreen,
            onSubmit = { code -> scope.launch { runtime.returnFlow.submit(code) } },
            onCancel = runtime.returnFlow::cancel,
            modifier = modifier,
        )
        ParkedVendorSettings -> VendorSettingsView(
            state = vendorScreen,
            actions = VendorSettingsActions(
                onFirstCode = { code, confirm -> scope.launch { vendorFlow.setFirstCode(code, confirm) } },
                onEnterCode = { code -> scope.launch { vendorFlow.enterCode(code) } },
                onSave = { input ->
                    vendorFlow.editVehicle(input.vehicle)
                    vendorFlow.editReturnTerms(input.returnTerms)
                    vendorFlow.editContact(input.contact)
                    vendorFlow.editResetGrace(input.resetGrace)
                    scope.launch { vendorFlow.save() }
                },
                onChangeCode = { request, confirm -> scope.launch { vendorFlow.changeCode(request, confirm) } },
                onClose = vendorFlow::close,
            ),
            modifier = modifier,
        )
        else -> HomeMessage(messageId, modifier)
    }
}

/**
 * 정차 대기 임시 화면. 반납 완료(T31)와 작은 업체 설정 버튼(B9)이 있다. 5단계에서 실제 화면으로 바뀐다.
 *
 * @param onReturn 반납 완료를 눌렀을 때
 * @param onVendorSettings 업체 설정을 눌렀을 때
 * @param modifier 바깥에서 주는 배치 설정
 */
@Composable
fun ParkedHome(onReturn: () -> Unit, onVendorSettings: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.parked_home_title), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onReturn) { Text(stringResource(R.string.action_return)) }
            TextButton(onClick = onVendorSettings) { Text(stringResource(R.string.action_vendor_settings)) }
        }
    }
}

/**
 * 안내 문구 화면. 화면 문구는 strings.xml의 문구 ID로 읽는다.
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

/** 정차 대기 임시 화면 미리보기. */
@Preview(showBackground = true)
@Composable
private fun ParkedHomePreview() {
    Annyeong_driveTheme {
        ParkedHome(onReturn = {}, onVendorSettings = {})
    }
}
