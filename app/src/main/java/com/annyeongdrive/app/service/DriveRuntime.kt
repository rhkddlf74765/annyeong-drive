package com.annyeongdrive.app.service

import android.content.Context
import com.annyeongdrive.app.admin.AdminCodeGate
import com.annyeongdrive.app.admin.ReturnCodeFlow
import com.annyeongdrive.app.admin.VendorSettingsFlow
import com.annyeongdrive.app.drive.AlwaysPassReturnGate
import com.annyeongdrive.app.drive.DriveContext
import com.annyeongdrive.app.drive.DriveCoordinator
import com.annyeongdrive.app.drive.FeatureFlags
import com.annyeongdrive.app.drive.VehicleSnapshot
import com.annyeongdrive.app.guide.CallAwareGuidanceOutput
import com.annyeongdrive.app.guide.EncounterBasedPolicy
import com.annyeongdrive.app.guide.GuidanceAnnouncer
import com.annyeongdrive.app.learning.CORE_CATALOG
import com.annyeongdrive.app.learning.CURRENT_CONTENT_VERSION
import com.annyeongdrive.app.learning.LearningEntryFlow
import com.annyeongdrive.app.learning.LearningFlow
import com.annyeongdrive.app.learning.LearningPlans
import com.annyeongdrive.app.learning.ResumeOfferFlow
import com.annyeongdrive.app.location.FusedLocationSource
import com.annyeongdrive.app.setup.DefaultRentalStarts
import com.annyeongdrive.app.setup.RentalSetupFlow
import com.annyeongdrive.app.setup.UuidRentalIdSource
import com.annyeongdrive.app.storage.AppDataStores
import com.annyeongdrive.app.storage.DriverSessionRepository
import com.annyeongdrive.app.storage.StoredLearningStatusReader
import com.annyeongdrive.app.storage.StoredRentalStatusReader
import com.annyeongdrive.app.storage.VendorConfigRepository
import com.annyeongdrive.app.time.DeviceClock
import com.annyeongdrive.app.vendor.SecureRandomSaltSource
import com.annyeongdrive.app.zones.ParkingWarningFlow
import com.annyeongdrive.app.zones.ZoneLoader
import com.annyeongdrive.app.zones.ZoneRoadRuleWatcher
import com.annyeongdrive.app.zones.ZoneStopPlaces
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

    /**
     * 가짜 입력 어댑터 한 벌. 실제 차량 신호가 연결되면 이 자리를 바꾼다.
     * release 빌드에서는 기어·차속·화면 제한·착좌·안전띠를 실제 신호로 바꾸므로 여기의 그 가짜들은 엔진에 연결되지 않는다.
     */
    val fakeSignals = FakeDriveSignals()

    /** 시연 지역 구역 데이터(보호구역·과속 카메라·주정차 카메라). 앱에 들어 있는 zones.json이다. */
    private val zones = ZoneLoader.load(appContext)

    /** 주행 중 규칙 안내·주정차 경고를 문장으로 만들어 말하고 띄운다. 대여 언어로 안내하고, 통화 중에는 알림으로만 띄운다. */
    val guidance = GuidanceAnnouncer(
        policy = EncounterBasedPolicy(),
        output = CallAwareGuidanceOutput(AndroidGuidanceOutput(appContext), CallState(appContext)::isInCall),
        language = { driverSessions.session.value?.language },
    )

    /** 이 대여의 교육 계획(콘텐츠 × 대여·업체 설정). Coordinator에는 문항 순서로 쓰인다. */
    val learningPlans = LearningPlans(CORE_CATALOG, driverSessions.session, vendorConfigs.config)

    /** 판단 핵심. */
    val coordinator = DriveCoordinator(
        DriveContext(
            vehicle = VehicleSnapshot.UNKNOWN,
            rental = StoredRentalStatusReader(driverSessions.session, vendorConfigs.config, clock),
            learning = StoredLearningStatusReader(driverSessions.session, learningPlans),
            stopPlaces = ZoneStopPlaces(zones),
            flags = FeatureFlags(),
            returnGate = AlwaysPassReturnGate(),
            roadRules = ZoneRoadRuleWatcher(zones, clock),
        ),
    )

    /** 입력 → Coordinator → 출력 연결. 서비스가 시작·멈춤을 맡고, 화면은 상태를 읽는다. */
    val engine = DriveEngine(
        coordinator = coordinator,
        sources = driveSignalSources(appContext, fakeSignals).copy(location = FusedLocationSource(appContext, clock)),
        executor = DriveActionExecutor(
            DriveOutputPorts(
                learningScreen = NoLearningScreen(),
                learningSpeech = SpeechHubLearningSpeech(appContext),
                driverData = RepositoryDriverDataEraser(driverSessions, scope),
                notifier = guidance,
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

    /** 대여 설정(언어 선택 → 출발 전 점검) 화면의 흐름. */
    val rentalSetupFlow = RentalSetupFlow(
        driverSessions,
        engine,
        DefaultRentalStarts(clock, UuidRentalIdSource(), CURRENT_CONTENT_VERSION),
    )

    /** 교육 시작 화면의 흐름. */
    val learningEntryFlow = LearningEntryFlow(driverSessions, engine)

    /** 교육·퀴즈 화면의 흐름. */
    val learningFlow = LearningFlow(driverSessions, engine, learningPlans)

    /** 이어서 확인할까요 화면의 흐름. */
    val resumeOfferFlow = ResumeOfferFlow(driverSessions, engine)

    /** 주정차 경고 화면의 흐름. */
    val parkingWarningFlow = ParkingWarningFlow(engine)
}
