package com.annyeongdrive.app.signaltest

import ai.pleos.playground.vehicle.constant.control.VehicleGear
import com.annyeongdrive.app.drive.AlwaysPassReturnGate
import com.annyeongdrive.app.drive.DriveContext
import com.annyeongdrive.app.drive.DriveCoordinator
import com.annyeongdrive.app.drive.FakeLearningStatus
import com.annyeongdrive.app.drive.FakeRentalStatus
import com.annyeongdrive.app.drive.FakeRoadRules
import com.annyeongdrive.app.drive.FakeStopPlaces
import com.annyeongdrive.app.drive.FeatureFlags
import com.annyeongdrive.app.drive.VehicleSnapshot
import com.annyeongdrive.app.drive.state.DriveState
import com.annyeongdrive.app.gear.CarGearSource
import com.annyeongdrive.app.guide.CallAwareGuidanceOutput
import com.annyeongdrive.app.guide.EncounterBasedPolicy
import com.annyeongdrive.app.guide.GuidanceAnnouncer
import com.annyeongdrive.app.guide.GuidanceChannel
import com.annyeongdrive.app.guide.GuidanceMessage
import com.annyeongdrive.app.location.FakeLocationSource
import com.annyeongdrive.app.location.GeoPoint
import com.annyeongdrive.app.rental.AppLanguage
import com.annyeongdrive.app.service.DriveActionExecutor
import com.annyeongdrive.app.service.DriveEngine
import com.annyeongdrive.app.service.DriveOutputPorts
import com.annyeongdrive.app.service.DriveSignalSources
import com.annyeongdrive.app.signaltest.FakePleosSeat.Companion.SDK_OCCUPIED
import com.annyeongdrive.app.vehicle.Seat
import com.annyeongdrive.app.vehicle.UxRestriction
import com.annyeongdrive.app.vehicle.car.CarSpeedSource
import com.annyeongdrive.app.vehicle.fake.FakeUxRestrictionSource
import com.annyeongdrive.app.vehicle.pleos.PleosDriverSeatSource
import com.annyeongdrive.app.vehicle.pleos.PleosSeatBeltSource

/**
 * 신호 시나리오 테스트 장치. release와 같은 실제 어댑터(기어·착좌·안전띠·차속)에 가짜 SDK를 붙이고,
 * 실제 엔진·Coordinator·안내(통화 중 알림만 포함)까지 이어 붙인다. 테스트는 SDK 값만 바꾸고 상태와 출력을 확인한다.
 * 대여·교육 상태, 주정차 구역, 규칙 안내 위치는 Coordinator 테스트와 같은 가짜를 쓴다.
 */
class SignalRig {

    /** 가짜 SDK 기어. */
    val gear = FakePleosGear()

    /** 가짜 SDK 좌석(착좌). */
    val seat = FakePleosSeat()

    /** 가짜 SDK 안전(안전띠 경고). */
    val safety = FakePleosSafety()

    /** 가짜 차속 연결(m/s). */
    val speed = FakeSpeedFeed()

    /** 가짜 화면 제한. 처음에는 제한 없음. */
    val ux = FakeUxRestrictionSource(UxRestriction.NONE)

    /** 가짜 위치. */
    val location = FakeLocationSource()

    /** 가짜 대여 상태. */
    val rental = FakeRentalStatus()

    /** 가짜 교육 진행. */
    val learning = FakeLearningStatus()

    /** 가짜 주정차 구역 조회. */
    val stopPlaces = FakeStopPlaces()

    /** 가짜 규칙 안내 위치. */
    val roadRules = FakeRoadRules()

    /** 통화 중인지. 안내 출력이 이 값을 읽는다. */
    var inCall = false

    /** 대여 언어. 영어는 말하고 한국어는 알림으로 띄운다. */
    var language: AppLanguage? = AppLanguage.ENGLISH

    /** 안내 출력이 실제로 내보낸 안내(통화 중 처리를 거친 뒤). */
    val delivered = mutableListOf<GuidanceMessage>()

    /** 교육 화면 닫기 지시 횟수. */
    var screenClosed = 0
        private set

    /** 학습 음성 중단 지시 횟수. */
    var speechStopped = 0
        private set

    /** 운전자 데이터 지우기 횟수. */
    var driverDataErased = 0
        private set

    private val log = RecordingSignalLog()

    private val guidance = GuidanceAnnouncer(
        policy = EncounterBasedPolicy(),
        output = CallAwareGuidanceOutput({ delivered += it }, { inCall }),
        language = { language },
    )

    /** release와 같은 어댑터와 엔진. */
    val engine = DriveEngine(
        coordinator = DriveCoordinator(
            DriveContext(
                vehicle = VehicleSnapshot.UNKNOWN,
                rental = rental,
                learning = learning,
                stopPlaces = stopPlaces,
                flags = FeatureFlags(),
                returnGate = AlwaysPassReturnGate(),
                roadRules = roadRules,
            ),
        ),
        sources = DriveSignalSources(
            gear = CarGearSource({ gear }, IMMEDIATE, log),
            speed = CarSpeedSource(speed, log),
            ux = ux,
            driverSeat = PleosDriverSeatSource({ seat }, IMMEDIATE, log),
            location = location,
            seatBelts = PleosSeatBeltSource({ safety }, IMMEDIATE, log),
        ),
        executor = DriveActionExecutor(
            DriveOutputPorts(
                learningScreen = { screenClosed += 1 },
                learningSpeech = { speechStopped += 1 },
                driverData = { eraseDriverData() },
                notifier = guidance,
            ),
        ),
    )

    /** 지금 운전 상태. */
    val state: DriveState
        get() = engine.state.value

    /** 주정차 경고로 내보낸 안내. */
    val parkingWarnings: List<GuidanceMessage>
        get() = delivered.filter { it.channel == GuidanceChannel.PARKING_WARNING }

    /**
     * 대여 설정과 교육 시작을 마친 운전자가 앉아 있는 정차 대기를 만든다(SDK: P, 0km/h, 운전석 앉음).
     *
     * @return 이 장치
     */
    fun startRented(): SignalRig {
        rental.hasRentalSetup = true
        learning.started = true
        seat.setCurrent(Seat.DRIVER, SDK_OCCUPIED)
        engine.start()
        return this
    }

    /**
     * SDK가 D와 차속을 보내 주행을 시작한다.
     *
     * @param kmh 차속(km/h)
     */
    fun drive(kmh: Float) {
        gear.select(VehicleGear.GEAR_DRIVE)
        speed.sendKmh(kmh)
    }

    /**
     * 검수된 주정차 경고 구역 안의 한 지점으로 위치를 옮긴다.
     *
     * @param zoneId 구역 ID
     */
    fun enterNoParkingZone(zoneId: String) {
        stopPlaces.noParkingZoneId = zoneId
        location.moveTo(ZONE_POINT)
    }

    /** 반납 처리로 운전자 데이터를 지운다. 실제 앱처럼 대여·교육 상태가 사라진다. */
    private fun eraseDriverData() {
        driverDataErased += 1
        rental.hasRentalSetup = false
        learning.started = false
    }

    companion object {
        /** 시나리오에서 쓰는 주정차 경고 구역 안 지점. */
        val ZONE_POINT = GeoPoint(37.6251787, 127.0330457)
    }
}
