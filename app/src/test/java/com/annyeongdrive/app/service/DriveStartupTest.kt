package com.annyeongdrive.app.service

import com.annyeongdrive.app.drive.AlwaysPassReturnGate
import com.annyeongdrive.app.drive.DriveContext
import com.annyeongdrive.app.drive.DriveCoordinator
import com.annyeongdrive.app.drive.FeatureFlags
import com.annyeongdrive.app.drive.NoStopPlaces
import com.annyeongdrive.app.drive.VehicleSnapshot
import com.annyeongdrive.app.drive.state.Idle
import com.annyeongdrive.app.drive.state.ParkedResumeOffer
import com.annyeongdrive.app.drive.state.SetupLanguage
import com.annyeongdrive.app.learning.QuizOrder
import com.annyeongdrive.app.storage.DriverSessionRepository
import com.annyeongdrive.app.storage.StorageFixtures
import com.annyeongdrive.app.storage.StoredLearningStatusReader
import com.annyeongdrive.app.storage.StoredRentalStatusReader
import com.annyeongdrive.app.storage.TempDataStores
import com.annyeongdrive.app.storage.VendorConfigRepository
import com.annyeongdrive.app.time.Clock
import com.annyeongdrive.app.vendor.ReturnTerms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Duration

/**
 * 앱 시작 순서: 저장소를 읽은 뒤 앱 시작(예비 초기화)을 처리하고, 그다음에 입력 신호를 받는다.
 * 근거: 결정 문서 3장(앱 시작 시 반납 예정 + N시간 경과면 초기화)·4장(타이머가 아니라 앱 시작 시 확인),
 * implementation-plan 3단계 "앱 시작 시 예비 초기화 확인", 상태 머신 T2·T3·T4·T5.
 */
class DriveStartupTest {

    private val stores = TempDataStores()
    private val writeScope = CoroutineScope(SupervisorJob())

    /** 파일 쓰기 범위를 닫고 임시 파일을 지운다. */
    @After
    fun tearDown() {
        writeScope.cancel()
        stores.delete()
    }

    /**
     * 앱을 껐다 켠 것처럼, 지난 실행에서 저장해 둔 대여·업체 설정을 파일에 남긴다.
     *
     * @param dueHoursAgo 반납 예정 시각이 지금보다 몇 시간 전인지
     */
    private fun savePreviousRun(dueHoursAgo: Long) = runBlocking {
        DriverSessionRepository(stores.open(DRIVER), StorageFixtures.CONTENT_VERSION)
            .update { StorageFixtures.startedSession().completePrecheck().beginLearning() }
        VendorConfigRepository(stores.open(VENDOR))
            .update { it.withReturnTerms(ReturnTerms(dueAt = StorageFixtures.NOW.minus(Duration.ofHours(dueHoursAgo)))) }
        stores.close()
    }

    /** 새로 켠 앱의 저장소·Coordinator·엔진·시작 순서 한 벌. */
    private inner class FreshApp {
        val driverSessions = DriverSessionRepository(stores.open(DRIVER), StorageFixtures.CONTENT_VERSION)
        val vendorConfigs = VendorConfigRepository(stores.open(VENDOR))
        val signals = FakeDriveSignals()
        val recorded = RecordingPorts()
        private val realEraser = RepositoryDriverDataEraser(driverSessions, writeScope)

        /** 운전자 데이터를 실제로 지운 횟수. */
        var erasedCount = 0
            private set
        val engine = DriveEngine(
            coordinator = DriveCoordinator(
                DriveContext(
                    vehicle = VehicleSnapshot.UNKNOWN,
                    rental = StoredRentalStatusReader(driverSessions.session, vendorConfigs.config, Clock { StorageFixtures.NOW }),
                    learning = StoredLearningStatusReader(driverSessions.session, QuizOrder { StorageFixtures.QUIZ_IDS }),
                    stopPlaces = NoStopPlaces(),
                    flags = FeatureFlags(),
                    returnGate = AlwaysPassReturnGate(),
                ),
            ),
            sources = signals.asSources(),
            executor = DriveActionExecutor(
                recorded.asPorts().copy(
                    driverData = {
                        erasedCount += 1
                        realEraser.erase()
                    },
                ),
            ),
        )
        val startup = DriveStartup(driverSessions, vendorConfigs, engine)
    }

    /** 반납 예정 + N시간(기본 24시간)이 지난 채 앱을 켜면 운전자 데이터를 지우고 대기로 가는지 확인한다(T4, T5). */
    @Test
    fun `예비 초기화 시각이 지난 채 앱을 켜면 운전자 데이터를 지우고 대기로 간다`() = runBlocking {
        // 상황
        savePreviousRun(dueHoursAgo = 25)
        val app = FreshApp()
        // 행동
        app.startup.start()
        // 결과
        assertNull(app.driverSessions.session.value)
        assertSame(Idle, app.engine.state.value)
    }

    /** 예비 초기화 시각 전이면 지난 대여를 그대로 두는지 확인한다. */
    @Test
    fun `예비 초기화 시각 전이면 지난 대여를 지우지 않는다`() = runBlocking {
        // 상황
        savePreviousRun(dueHoursAgo = 1)
        val app = FreshApp()
        // 행동
        app.startup.start()
        // 결과
        assertEquals("rental-0001", app.driverSessions.session.value?.rentalId)
    }

    /**
     * 운전석에 앉은 채로 앱을 켜도 저장된 대여·교육 진행을 읽은 뒤에 판단하는지 확인한다(T3 → T12 → T15, 순서 보장).
     * 지난 실행에서 교육을 시작만 하고 끝내지 않았으므로 이어 보기 제안이 나와야 한다. 읽기 전에 판단했다면 언어 선택(T2)으로 갔을 것이다.
     */
    @Test
    fun `운전석에 앉은 채로 앱을 켜도 저장된 교육 진행을 읽은 뒤에 이어 보기를 제안한다`() = runBlocking {
        // 상황
        savePreviousRun(dueHoursAgo = 1)
        val app = FreshApp()
        app.signals.driverSeat.sit()
        // 행동
        app.startup.start()
        // 결과
        assertSame(ParkedResumeOffer, app.engine.state.value)
    }

    /** 저장된 대여가 없으면 착좌했을 때 대여 설정(언어 선택)부터 시작하는지 확인한다(T2). */
    @Test
    fun `저장된 대여가 없으면 착좌했을 때 언어 선택부터 시작한다`() = runBlocking {
        // 상황
        val app = FreshApp()
        app.startup.start()
        // 행동
        app.signals.driverSeat.sit()
        // 결과
        assertSame(SetupLanguage, app.engine.state.value)
    }

    /** 서비스가 다시 만들어져 시작 순서를 또 불러도 앱 시작은 한 번만 처리하는지 확인한다. */
    @Test
    fun `시작을 두 번 불러도 앱 시작은 한 번만 처리한다`() = runBlocking {
        // 상황
        savePreviousRun(dueHoursAgo = 25)
        val app = FreshApp()
        app.startup.start()
        app.startup.stop()
        // 행동
        app.startup.start()
        // 결과
        assertSame(Idle, app.engine.state.value)
        assertEquals(1, app.erasedCount)
    }

    private companion object {
        const val DRIVER = "driver_session"
        const val VENDOR = "vendor_config"
    }
}
