package com.annyeongdrive.app.service

import android.content.Context
import com.annyeongdrive.app.admin.AdminCodeGate
import com.annyeongdrive.app.admin.ReturnCodeFlow
import com.annyeongdrive.app.admin.VendorSettingsFlow
import com.annyeongdrive.app.drive.AlwaysPassReturnGate
import com.annyeongdrive.app.drive.DriveContext
import com.annyeongdrive.app.drive.DriveCoordinator
import com.annyeongdrive.app.drive.FeatureFlags
import com.annyeongdrive.app.drive.NoStopPlaces
import com.annyeongdrive.app.drive.VehicleSnapshot
import com.annyeongdrive.app.learning.CURRENT_CONTENT_VERSION
import com.annyeongdrive.app.learning.NO_QUIZ_YET
import com.annyeongdrive.app.storage.AppDataStores
import com.annyeongdrive.app.storage.DriverSessionRepository
import com.annyeongdrive.app.storage.StoredLearningStatusReader
import com.annyeongdrive.app.storage.StoredRentalStatusReader
import com.annyeongdrive.app.storage.VendorConfigRepository
import com.annyeongdrive.app.time.DeviceClock
import com.annyeongdrive.app.vendor.SecureRandomSaltSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 앱 전체에서 하나만 있어야 하는 객체를 한 번에 만들어 묶는다(조립 지점). [AnnyeongApplication]이 하나 갖는다.
 * 서비스와 화면은 여기서 같은 저장소·엔진을 받는다.
 *
 * @param context 앱 Context
 */
class DriveRuntime(context: Context) {

    private val appContext = context.applicationContext
    private val clock = DeviceClock()

    /** 앱이 살아 있는 동안의 작업 범위. 저장 파일 쓰기 등을 이어 간다. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 운전자 데이터 저장소. */
    val driverSessions = DriverSessionRepository(AppDataStores.driverSession(appContext), CURRENT_CONTENT_VERSION)

    /** 업체 데이터 저장소. */
    val vendorConfigs = VendorConfigRepository(AppDataStores.vendorConfig(appContext))

    /** 가짜 입력 어댑터 한 벌. 실제 차량 신호가 연결되면 이 자리를 바꾼다. */
    val fakeSignals = FakeDriveSignals()

    /** 판단 핵심. */
    val coordinator = DriveCoordinator(
        DriveContext(
            vehicle = VehicleSnapshot.UNKNOWN,
            rental = StoredRentalStatusReader(driverSessions.session, vendorConfigs.config, clock),
            learning = StoredLearningStatusReader(driverSessions.session, NO_QUIZ_YET),
            stopPlaces = NoStopPlaces(),
            flags = FeatureFlags(),
            returnGate = AlwaysPassReturnGate(),
        ),
    )

    /** 입력 → Coordinator → 출력 연결. 서비스가 시작·멈춤을 맡고, 화면은 상태를 읽는다. */
    val engine = DriveEngine(
        coordinator = coordinator,
        sources = fakeSignals.asSources(),
        executor = DriveActionExecutor(
            DriveOutputPorts(
                learningScreen = NoLearningScreen(),
                learningSpeech = SpeechHubLearningSpeech(appContext),
                driverData = RepositoryDriverDataEraser(driverSessions, scope),
                notifier = PendingDriveNotifier(),
            ),
        ),
    )

    /** 앱 시작 순서(저장소 읽기 → AppStarted → 신호 구독). 서비스가 시작·멈춤을 맡는다. */
    val startup = DriveStartup(driverSessions, vendorConfigs, engine)

    /** 관리자 코드 확인·설정·변경 창구. 반납과 업체 설정이 함께 쓴다. */
    val adminGate = AdminCodeGate(vendorConfigs, clock, SecureRandomSaltSource())

    /** 반납 처리 화면의 흐름. */
    val returnFlow = ReturnCodeFlow(adminGate, engine)

    /** 업체 설정 화면의 흐름. */
    val vendorSettingsFlow = VendorSettingsFlow(adminGate, vendorConfigs, engine)
}
