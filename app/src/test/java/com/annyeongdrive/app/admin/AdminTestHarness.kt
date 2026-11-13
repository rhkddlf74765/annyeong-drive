package com.annyeongdrive.app.admin

import com.annyeongdrive.app.drive.AlwaysPassReturnGate
import com.annyeongdrive.app.drive.DriveContext
import com.annyeongdrive.app.drive.DriveCoordinator
import com.annyeongdrive.app.drive.FeatureFlags
import com.annyeongdrive.app.drive.NoStopPlaces
import com.annyeongdrive.app.drive.VehicleSnapshot
import com.annyeongdrive.app.learning.QuizOrder
import com.annyeongdrive.app.service.DriveActionExecutor
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
 * 반납·업체 설정 흐름 테스트용 앱 한 벌. 실제 DataStore 파일 위에 저장소·Coordinator·엔진·흐름을 조립한다.
 * 같은 [TempDataStores]로 다시 만들면 앱을 껐다 켠 것과 같다(그 전에 [awaitWrites]와 stores.close()를 부른다).
 * 문항 순서는 비어 있어(6단계 콘텐츠 전) 교육을 시작한 대여는 정차 시 정차 대기로 간다.
 *
 * @param stores 임시 저장 파일
 * @param writeScope 운전자 데이터 지우기처럼 이어서 끝나는 파일 쓰기의 범위
 * @param clock 시계
 */
class AdminTestHarness(
    stores: TempDataStores,
    private val writeScope: CoroutineScope,
    val clock: MutableClock = MutableClock(StorageFixtures.NOW),
) {

    /** 운전자 데이터 저장소. */
    val driverSessions = DriverSessionRepository(stores.open(DRIVER, replaceCorrupt = true), StorageFixtures.CONTENT_VERSION)

    /** 업체 데이터 저장소. */
    val vendorConfigs = VendorConfigRepository(stores.open(VENDOR))

    /** 가짜 입력 신호(처음 값: P · 0 · 제한 없음). */
    val signals = FakeDriveSignals()

    /** 입력 → Coordinator → 출력 연결. 운전자 데이터 지우기는 실제 저장소로 한다. */
    val engine = DriveEngine(
        coordinator = DriveCoordinator(
            DriveContext(
                vehicle = VehicleSnapshot.UNKNOWN,
                rental = StoredRentalStatusReader(driverSessions.session, vendorConfigs.config, clock),
                learning = StoredLearningStatusReader(driverSessions.session, QuizOrder { emptyList() }),
                stopPlaces = NoStopPlaces(),
                flags = FeatureFlags(),
                returnGate = AlwaysPassReturnGate(),
            ),
        ),
        sources = signals.asSources(),
        executor = DriveActionExecutor(
            DriveOutputPorts(
                learningScreen = {},
                learningSpeech = {},
                driverData = RepositoryDriverDataEraser(driverSessions, writeScope),
                notifier = RecordingPorts().asPorts().notifier,
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
