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
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.drive.state.ParkedVendorSettings
import com.annyeongdrive.app.drive.state.Returning
import com.annyeongdrive.app.service.DriveRuntime
import com.annyeongdrive.app.service.DriveService
import com.annyeongdrive.app.ui.admin.ReturnScreen
import com.annyeongdrive.app.ui.admin.ReturnScreenModel
import com.annyeongdrive.app.ui.admin.VendorSettingsActions
import com.annyeongdrive.app.ui.admin.VendorSettingsView
import com.annyeongdrive.app.setup.PrecheckView
import com.annyeongdrive.app.ui.AppScreen
import com.annyeongdrive.app.ui.RentalLanguage
import com.annyeongdrive.app.ui.WaitingMessage
import com.annyeongdrive.app.ui.home.DrivingHome
import com.annyeongdrive.app.ui.home.ParkingWarningScreen
import com.annyeongdrive.app.ui.learning.LearningAccessModel
import com.annyeongdrive.app.ui.learning.LearningActions
import com.annyeongdrive.app.ui.learning.LearningScreen
import com.annyeongdrive.app.ui.learning.LearningScreenModel
import com.annyeongdrive.app.ui.learning.LearningStartScreen
import com.annyeongdrive.app.ui.learning.ResumeOfferActions
import com.annyeongdrive.app.ui.learning.ResumeOfferScreen
import com.annyeongdrive.app.ui.parked.ParkedHome
import com.annyeongdrive.app.ui.parked.ParkedHomeActions
import com.annyeongdrive.app.ui.setup.LanguageScreen
import com.annyeongdrive.app.ui.setup.PrecheckActions
import com.annyeongdrive.app.ui.setup.PrecheckScreen
import com.annyeongdrive.app.ui.setup.PrecheckScreenModel
import com.annyeongdrive.app.ui.theme.Annyeong_driveTheme
import com.annyeongdrive.app.ui.theme.ScreenHeading
import com.annyeongdrive.app.ui.theme.VehicleScreen
import java.time.ZoneId
import kotlinx.coroutines.launch

/**
 * 앱의 첫 화면. 위치·알림 권한을 받은 뒤 주행 안내 서비스(DriveService)를 시작하고,
 * Coordinator의 현재 운전 상태(engine.state)만 보고 화면을 고른다. 기어·차속을 직접 보지 않는다.
 * 대기·대여 설정·교육·이어 보기 제안·정차 대기·주정차 경고·반납 처리·업체 설정·주행 중 화면이 있다.
 * 화면 문구는 대여 언어를 따른다(RentalLanguage). 대여가 없으면 기기 언어다.
 * 화면은 하나만 둔다(launchMode singleTask). 아이콘이나 알림을 다시 눌러도 새로 쌓이지 않는다.
 * 기능 확인용 화면은 debug 빌드의 CheckActivity에 있다.
 */
class MainActivity : ComponentActivity() {

    /** 대기 화면 문구 ID. 위치 권한이 없으면 권한 안내로 바뀐다. */
    private val messageId = mutableIntStateOf(R.string.home_idle)

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
            val session by runtime.driverSessions.session.collectAsState()
            Annyeong_driveTheme {
                RentalLanguage(session?.language) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        DriveScreen(runtime, messageId.intValue, Modifier.padding(innerPadding))
                    }
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

    /**
     * 다른 화면(설정 등)에 갔다가 돌아오면 위치 권한을 다시 확인한다. 그사이 허용했으면 서비스를 시작한다.
     * 화면을 하나만 두므로(singleTask) 아이콘으로 돌아와도 onCreate가 다시 불리지 않기 때문이다(11/14 에뮬레이터 확인).
     * 권한이 여전히 없으면 다시 묻지 않고 안내 문구를 그대로 둔다.
     */
    override fun onRestart() {
        super.onRestart()
        if (!hasLocationPermission()) return
        startServiceIfAllowed()
    }

    /** 위치 권한이 있으면 주행 안내 서비스를 시작하고, 없으면 권한이 필요하다는 문구를 보여 준다. */
    private fun startServiceIfAllowed() {
        if (!hasLocationPermission()) {
            messageId.intValue = R.string.home_location_needed
            return
        }
        messageId.intValue = R.string.home_idle
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
        const val CAR_SPEED = "android.car.permission.CAR_SPEED"

        /**
         * 서비스 시작 전에 요청하는 권한. 위치는 location 타입 서비스, 알림은 상주 알림, Pleos는 위치·음성 SDK용,
         * 차속은 release의 실제 차속 신호용이다. 차속을 거부하면 서비스는 시작하지만 정차·주행을 판단하지 못한다.
         */
        val REQUIRED_PERMISSIONS = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
            PLEOS_FUSED_LOCATION,
            PLEOS_TTS,
            CAR_SPEED,
        )
    }
}

/**
 * 운전 상태에 맞는 화면을 그린다. 어떤 화면인지는 [AppScreen.of]가 고른다. 상태가 바뀌면 반납·업체 설정 흐름에 알려 첫 단계를 정하거나 닫게 한다.
 *
 * @param runtime 앱 전체에서 하나인 조립 지점
 * @param messageId 대기 상태에서 보여 줄 문구 ID(위치 권한이 없으면 권한 안내)
 * @param modifier 바깥에서 주는 배치 설정
 */
@Composable
private fun DriveScreen(runtime: DriveRuntime, @StringRes messageId: Int, modifier: Modifier) {
    val driveState by runtime.engine.state.collectAsState()
    val returnScreen by runtime.returnFlow.screen.collectAsState()
    val vendorScreen by runtime.vendorSettingsFlow.screen.collectAsState()
    val vehicle by runtime.engine.vehicle.collectAsState()
    val vendorConfig by runtime.vendorConfigs.config.collectAsState()
    val precheck by runtime.rentalSetupFlow.precheck.collectAsState()
    val setupFlow = runtime.rentalSetupFlow
    val learningEntry = runtime.learningEntryFlow
    val learningFlow = runtime.learningFlow
    val learningView by learningFlow.view.collectAsState()
    val resumeOffer = runtime.resumeOfferFlow
    val scope = rememberCoroutineScope()
    val vendorFlow = runtime.vendorSettingsFlow
    val parkingWarning = runtime.parkingWarningFlow

    LaunchedEffect(driveState) {
        vendorFlow.syncWithDrive()
        learningFlow.sync()
        if (driveState == Returning) runtime.returnFlow.open()
        if (driveState == ParkedVendorSettings) vendorFlow.open()
    }

    when (AppScreen.of(driveState)) {
        AppScreen.WAITING -> WaitingMessage(messageId, modifier)
        AppScreen.DRIVING -> DrivingHome(modifier)
        AppScreen.PARKING_WARNING -> ParkingWarningScreen(onAcknowledge = parkingWarning::acknowledge, modifier = modifier)
        AppScreen.LANGUAGE -> LanguageScreen(
            onSelect = { language -> scope.launch { setupFlow.selectLanguage(language) } },
            modifier = modifier,
        )
        AppScreen.PRECHECK -> PrecheckScreen(
            model = PrecheckScreenModel.of(
                PrecheckView.of(vehicle.seatBelts, vendorConfig), precheck, ZoneId.systemDefault(),
            ),
            actions = PrecheckActions(
                onConfirmSeatBelts = setupFlow::confirmSeatBelts,
                onComplete = { scope.launch { setupFlow.completePrecheck() } },
            ),
            modifier = modifier,
        )
        AppScreen.LEARNING_START -> LearningStartScreen(
            onBegin = { scope.launch { learningEntry.begin() } },
            onLater = learningEntry::later,
            modifier = modifier,
        )
        AppScreen.LEARNING -> LearningScreen(
            model = LearningScreenModel.of(learningView),
            actions = LearningActions(
                onPreviousCard = { scope.launch { learningFlow.previousCard() } },
                onNextCard = { scope.launch { learningFlow.nextCard() } },
                onStartQuiz = { scope.launch { learningFlow.startQuiz() } },
                onSelect = learningFlow::selectAnswer,
                onCheck = { scope.launch { learningFlow.checkAnswer() } },
                onSkip = { scope.launch { learningFlow.skip() } },
                onNext = { scope.launch { learningFlow.next() } },
                onClose = learningFlow::close,
            ),
            modifier = modifier,
        )
        AppScreen.RESUME_OFFER -> ResumeOfferScreen(
            model = LearningAccessModel.of(vehicle.learningAvailability()),
            actions = ResumeOfferActions(
                onResume = resumeOffer::resume,
                onRestart = { scope.launch { resumeOffer.restart() } },
                onLater = resumeOffer::later,
            ),
            modifier = modifier,
        )
        AppScreen.PARKED_HOME -> ParkedHome(
            model = LearningAccessModel.of(vehicle.learningAvailability()),
            actions = ParkedHomeActions(
                onOpenLearning = { runtime.engine.send(DriveEvent.LearningOpenRequested) },
                onReturn = { runtime.engine.send(DriveEvent.ReturnRequested) },
                onVendorSettings = { runtime.engine.send(DriveEvent.VendorSettingsRequested) },
            ),
            modifier = modifier,
        )
        AppScreen.RETURN -> ReturnScreen(
            model = ReturnScreenModel.of(returnScreen, ZoneId.systemDefault()),
            onSubmit = { code -> scope.launch { runtime.returnFlow.submit(code) } },
            onCancel = runtime.returnFlow::cancel,
            modifier = modifier,
        )
        AppScreen.VENDOR_SETTINGS -> VendorSettingsView(
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
        AppScreen.PREPARING -> HomeMessage(R.string.home_preparing, modifier)
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
    VehicleScreen(modifier) {
        ScreenHeading(stringResource(messageId))
    }
}
