package com.annyeongdrive.app.admin

import com.annyeongdrive.app.drive.AlwaysPassReturnGate
import com.annyeongdrive.app.drive.DriveContext
import com.annyeongdrive.app.drive.DriveCoordinator
import com.annyeongdrive.app.drive.FeatureFlags
import com.annyeongdrive.app.drive.VehicleSnapshot
import com.annyeongdrive.app.learning.LearningEntryFlow
import com.annyeongdrive.app.learning.LearningCatalog
import com.annyeongdrive.app.learning.LearningFlow
import com.annyeongdrive.app.learning.LearningPlans
import com.annyeongdrive.app.learning.ResumeOfferFlow
import com.annyeongdrive.app.rental.RentalStartRequest
import com.annyeongdrive.app.service.DriveActionExecutor
import com.annyeongdrive.app.setup.RentalSetupFlow
import com.annyeongdrive.app.setup.RentalStarts
import com.annyeongdrive.app.service.DriveEngine
import com.annyeongdrive.app.service.DriveOutputPorts
import com.annyeongdrive.app.service.DriveStartup
import com.annyeongdrive.app.service.FakeDriveSignals
import com.annyeongdrive.app.service.RecordingPorts
import com.annyeongdrive.app.service.RepositoryDriverDataEraser
import com.annyeongdrive.app.storage.DriverSessionRepository
import com.annyeongdrive.app.storage.StorageFixtures
import com.annyeongdrive.app.storage.StoredLearningStatusReader
import com.annyeongdrive.app.storage.StoredRentalStatusReader
import com.annyeongdrive.app.storage.TempDataStores
import com.annyeongdrive.app.storage.VendorConfigRepository
import com.annyeongdrive.app.time.Clock
import com.annyeongdrive.app.vendor.ReturnTerms
import com.annyeongdrive.app.vendor.SaltSource
import com.annyeongdrive.app.zones.ZoneData
import com.annyeongdrive.app.zones.ZoneRoadRuleWatcher
import com.annyeongdrive.app.zones.ZoneStopPlaces
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.Instant

/** 테스트에서 시각을 직접 옮기는 시계. */
class MutableClock(var now: Instant) : Clock {

    /**
     * 정해 둔 시각을 돌려준다.
     *
     * @return 지금 시각
     */
    override fun now(): Instant = now

    /**
     * 시각을 앞으로 옮긴다.
     *
     * @param duration 옮길 시간
     */
    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
}

/**
 * 사용 흐름 테스트용 앱 한 벌(대여 시작·교육·이어 보기 제안·반납·업체 설정). 실제 DataStore 파일 위에 저장소·Coordinator·엔진·흐름을 조립한다.
 * 같은 [TempDataStores]로 다시 만들면 앱을 껐다 켠 것과 같다(그 전에 [awaitWrites]와 stores.close()를 부른다).
 * 기본 콘텐츠는 비어 있어 교육을 시작한 대여는 정차 시 정차 대기로 간다. 교육·퀴즈 흐름 테스트는 [catalog]를 넘긴다.
 *
 * @param stores 임시 저장 파일
 * @param writeScope 운전자 데이터 지우기처럼 이어서 끝나는 파일 쓰기의 범위
 * @param clock 시계
 * @param catalog 교육 콘텐츠
 * @param zones 구역 데이터(주정차 경고·주행 중 규칙 안내). 기본은 비어 있다
 */
class AdminTestHarness(
    stores: TempDataStores,
    private val writeScope: CoroutineScope,
    val clock: MutableClock = MutableClock(StorageFixtures.NOW),
    catalog: LearningCatalog = EMPTY_CATALOG,
    zones: ZoneData = NO_ZONES,
) {

    /** 운전자 데이터 저장소. */
    val driverSessions = DriverSessionRepository(stores.open(DRIVER, replaceCorrupt = true), StorageFixtures.CONTENT_VERSION)

    /** 업체 데이터 저장소. */
    val vendorConfigs = VendorConfigRepository(stores.open(VENDOR))

    /** 이 대여의 교육 계획. */
    val plans = LearningPlans(catalog, driverSessions.session, vendorConfigs.config)

    /** 가짜 입력 신호(처음 값: P · 0 · 제한 없음). */
    val signals = FakeDriveSignals()

    /** 교육 화면 닫기·학습 음성 중단이 몇 번 실행됐는지 기록한다. */
    val ports = RecordingPorts()

    /** 입력 → Coordinator → 출력 연결. 운전자 데이터 지우기는 실제 저장소로 한다. */
    val engine = DriveEngine(
        coordinator = DriveCoordinator(
            DriveContext(
                vehicle = VehicleSnapshot.UNKNOWN,
                rental = StoredRentalStatusReader(driverSessions.session, vendorConfigs.config, clock),
                learning = StoredLearningStatusReader(driverSessions.session, plans),
                stopPlaces = ZoneStopPlaces(zones),
                flags = FeatureFlags(),
                returnGate = AlwaysPassReturnGate(),
                roadRules = ZoneRoadRuleWatcher(zones, clock),
            ),
        ),
        sources = signals.asSources(),
        executor = DriveActionExecutor(
            DriveOutputPorts(
                learningScreen = ports.asPorts().learningScreen,
                learningSpeech = ports.asPorts().learningSpeech,
                driverData = RepositoryDriverDataEraser(driverSessions, writeScope),
                notifier = ports.asPorts().notifier,
            ),
        ),
    )

    /** 앱 시작 순서. */
    val startup = DriveStartup(driverSessions, vendorConfigs, engine)

    /** 관리자 코드 창구. salt는 고정 값이다. */
    val gate = AdminCodeGate(vendorConfigs, clock, SaltSource { StorageFixtures.SALT })

    /** 반납 처리 흐름. */
    val returnFlow = ReturnCodeFlow(gate, engine)

    /** 업체 설정 흐름. */
    val vendorFlow = VendorSettingsFlow(gate, vendorConfigs, engine)

    /** 대여 설정 흐름. 새 대여 식별자는 [NEW_RENTAL_ID]로 고정한다. */
    val setupFlow = RentalSetupFlow(
        driverSessions,
        engine,
        RentalStarts { language -> RentalStartRequest(NEW_RENTAL_ID, clock.now(), language, StorageFixtures.CONTENT_VERSION) },
    )

    /** 교육 시작 흐름. */
    val learningEntry = LearningEntryFlow(driverSessions, engine)

    /** 교육·퀴즈 흐름. */
    val learningFlow = LearningFlow(driverSessions, engine, plans)

    /** 이어서 확인할까요 흐름. */
    val resumeOffer = ResumeOfferFlow(driverSessions, engine)

    /** 앱을 시작하고(저장소 읽기 → AppStarted → 신호 구독) 운전석에 앉는다. 대여가 있으면 정차 대기로 간다. */
    suspend fun startAndSit() {
        startup.start()
        signals.driverSeat.sit()
    }

    /** 이어서 끝나는 파일 쓰기(운전자 데이터 지우기)가 모두 끝날 때까지 기다린다. */
    suspend fun awaitWrites() {
        writeScope.coroutineContext[Job]?.children?.forEach { it.join() }
    }

    companion object {
        /** 운전자 데이터 파일 이름. */
        const val DRIVER = "driver_session"

        /** 업체 데이터 파일 이름. */
        const val VENDOR = "vendor_config"

        /** 언어를 골라 새로 시작한 대여의 식별자. */
        const val NEW_RENTAL_ID = "rental-new"

        /** 구역이 하나도 없는 데이터. */
        val NO_ZONES = ZoneData(emptyList(), emptyList(), emptyList())

        /** 카드·문항이 없는 콘텐츠. */
        val EMPTY_CATALOG = LearningCatalog(StorageFixtures.CONTENT_VERSION, emptyList(), emptyList())

        /**
         * 지난 실행에서 저장해 둔 파일을 만든다(대여·업체 설정). 끝나면 파일을 닫는다.
         *
         * @param stores 임시 저장 파일
         * @param adminCode 정해 둔 관리자 코드. 정하지 않았으면 null
         * @param terms 반납 조건. 없으면 null
         * @param grace 예비 초기화 시간 N. 기본값을 쓰려면 null
         */
        fun seedPreviousRun(
            stores: TempDataStores,
            adminCode: String? = StorageFixtures.ADMIN_CODE,
            terms: ReturnTerms? = null,
            grace: Duration? = null,
        ) = runBlocking {
            DriverSessionRepository(stores.open(DRIVER, replaceCorrupt = true), StorageFixtures.CONTENT_VERSION)
                .update { StorageFixtures.startedSession().completePrecheck().beginLearning() }
            VendorConfigRepository(stores.open(VENDOR)).update { initial ->
                var config = initial
                if (adminCode != null) config = config.setInitialAdminCode(adminCode, StorageFixtures.SALT)
                if (terms != null) config = config.withReturnTerms(terms)
                if (grace != null) config = config.withResetGrace(grace)
                config
            }
            stores.close()
        }
    }
}
