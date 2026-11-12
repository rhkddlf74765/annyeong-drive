package com.annyeongdrive.app.drive

import com.annyeongdrive.app.drive.state.DriveState
import com.annyeongdrive.app.gear.Gear
import com.annyeongdrive.app.location.GeoPoint
import com.annyeongdrive.app.vehicle.SeatOccupancy
import com.annyeongdrive.app.vehicle.UxRestriction

/**
 * 시나리오 테스트 도우미. 이벤트를 차례로 넣고 할 일과 상태를 확인한다.
 * 시작 상태는 실제 이벤트를 넣어 만든다. 상태를 직접 꽂지 않는다.
 *
 * @param flags 기능 플래그. 기본은 오주유 꺼짐
 */
class DriveScenario(flags: FeatureFlags = FeatureFlags()) {

    /** 대여 상태 가짜. 테스트에서 값을 바꾼다. */
    val rental = FakeRentalStatus()

    /** 교육 진행 가짜. 테스트에서 값을 바꾼다. */
    val learning = FakeLearningStatus()

    /** 정차 장소 가짜. 테스트에서 값을 바꾼다. */
    val stopPlaces = FakeStopPlaces()

    private val coordinator = DriveCoordinator(
        DriveContext(
            vehicle = VehicleSnapshot.UNKNOWN,
            rental = rental,
            learning = learning,
            stopPlaces = stopPlaces,
            flags = flags,
            returnGate = AlwaysPassReturnGate(),
        ),
    )

    /** 현재 상태. */
    val state: DriveState
        get() = coordinator.state

    /**
     * 이벤트를 차례로 넣는다.
     *
     * @param events 넣을 이벤트
     * @return 모든 이벤트에서 나온 할 일을 순서대로 모은 목록
     */
    fun feed(vararg events: DriveEvent): List<DriveAction> = events.flatMap(coordinator::handle)

    /**
     * 기어 P, 차속 0, UX 제한 없음 신호를 넣는다. 교육 진입 조건을 만족하는 정차 신호다.
     *
     * @return 그때 나온 할 일
     */
    fun parkSignals(): List<DriveAction> = feed(*PARKED_SIGNALS)

    /**
     * 대여 설정을 마치고 교육도 시작한 적이 있는 운전자가 정차 대기에 있는 상태를 만든다.
     *
     * @return 이 시나리오(이어서 쓰기 위해)
     */
    fun atParkedRest(): DriveScenario {
        rental.hasRentalSetup = true
        learning.started = true
        parkSignals()
        feed(DriveEvent.DriverSeatChanged(SeatOccupancy.OCCUPIED))
        return this
    }

    /**
     * 정차 대기에서 교육을 열어 문제 화면에 있는 상태를 만든다.
     *
     * @return 이 시나리오
     */
    fun atLearningQuiz(): DriveScenario {
        atParkedRest()
        feed(DriveEvent.LearningOpenRequested, DriveEvent.LearningBegin, DriveEvent.QuizStartRequested)
        return this
    }

    /**
     * 정차 대기에서 D로 바꿔 일반 주행에 있는 상태를 만든다.
     *
     * @return 이 시나리오
     */
    fun atDriving(): DriveScenario {
        atParkedRest()
        feed(DriveEvent.GearChanged(Gear.D), DriveEvent.SpeedChanged(30f))
        return this
    }

    /**
     * 일반 주행에서 위치를 정하고 정차한다(차속 0 → 기어 P).
     *
     * @param point 정차할 위치
     * @return 정차 순간에 나온 할 일
     */
    fun stopAt(point: GeoPoint = SOMEWHERE): List<DriveAction> =
        feed(DriveEvent.LocationChanged(point), DriveEvent.SpeedChanged(0f), DriveEvent.GearChanged(Gear.P))

    companion object {
        /** 테스트용 임의 위치. */
        val SOMEWHERE = GeoPoint(37.6300, 127.0300)

        /** 교육 진입 조건을 만족하는 정차 신호. */
        private val PARKED_SIGNALS = arrayOf(
            DriveEvent.GearChanged(Gear.P),
            DriveEvent.SpeedChanged(0f),
            DriveEvent.UxRestrictionChanged(UxRestriction.NONE),
        )
    }
}

/** 값을 직접 정하는 대여 상태 가짜. */
class FakeRentalStatus : RentalStatusReader {

    /** 대여 설정을 마쳤는지. */
    var hasRentalSetup = false

    /** 예비 초기화 시각이 지났는지. */
    var resetOverdue = false

    /**
     * 정해 둔 대여 설정 여부를 돌려준다.
     *
     * @return [hasRentalSetup]
     */
    override fun hasRentalSetup(): Boolean = hasRentalSetup

    /**
     * 정해 둔 예비 초기화 여부를 돌려준다.
     *
     * @return [resetOverdue]
     */
    override fun isResetOverdue(): Boolean = resetOverdue
}

/** 값을 직접 정하는 교육 진행 가짜. */
class FakeLearningStatus : LearningStatusReader {

    /** 교육을 시작한 적이 있는지. */
    var started = false

    /** 미완료 교육이 있는지. */
    var unfinished = false

    /** 지금 문제가 마지막 문항인지. */
    var lastQuestion = false

    /** 이어 보기 위치. */
    var resumeAt = LearningResumePoint.CARD

    /**
     * 정해 둔 시작 여부를 돌려준다.
     *
     * @return [started]
     */
    override fun hasStarted(): Boolean = started

    /**
     * 정해 둔 미완료 여부를 돌려준다.
     *
     * @return [unfinished]
     */
    override fun hasUnfinished(): Boolean = unfinished

    /**
     * 정해 둔 마지막 문항 여부를 돌려준다.
     *
     * @return [lastQuestion]
     */
    override fun isLastQuestion(): Boolean = lastQuestion

    /**
     * 정해 둔 이어 보기 위치를 돌려준다.
     *
     * @return [resumeAt]
     */
    override fun resumePoint(): LearningResumePoint = resumeAt
}

/** 값을 직접 정하고 주유소 조회 횟수를 세는 정차 장소 가짜. */
class FakeStopPlaces : StopPlaceLookup {

    /** 검수된 주정차 금지 구역 식별자. null이면 구역 밖. */
    var noParkingZoneId: String? = null

    /** 주유소 반경 안인지. */
    var nearGasStation = false

    /** [isNearGasStation]이 불린 횟수. */
    var gasStationQueries = 0
        private set

    /**
     * 정해 둔 구역 식별자를 돌려준다.
     *
     * @param point 쓰지 않는다
     * @return [noParkingZoneId]
     */
    override fun verifiedNoParkingZoneNear(point: GeoPoint): String? = noParkingZoneId

    /**
     * 조회 횟수를 세고 정해 둔 주유소 여부를 돌려준다.
     *
     * @param point 쓰지 않는다
     * @return [nearGasStation]
     */
    override fun isNearGasStation(point: GeoPoint): Boolean {
        gasStationQueries += 1
        return nearGasStation
    }
}
