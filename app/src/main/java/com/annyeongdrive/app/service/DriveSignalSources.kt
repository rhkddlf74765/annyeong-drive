package com.annyeongdrive.app.service

import com.annyeongdrive.app.gear.FakeGearSource
import com.annyeongdrive.app.gear.Gear
import com.annyeongdrive.app.gear.GearSource
import com.annyeongdrive.app.location.FakeLocationSource
import com.annyeongdrive.app.location.LocationSource
import com.annyeongdrive.app.vehicle.DriverSeatSource
import com.annyeongdrive.app.vehicle.SpeedSource
import com.annyeongdrive.app.vehicle.UxRestriction
import com.annyeongdrive.app.vehicle.UxRestrictionSource
import com.annyeongdrive.app.vehicle.fake.FakeDriverSeatSource
import com.annyeongdrive.app.vehicle.fake.FakeSpeedSource
import com.annyeongdrive.app.vehicle.fake.FakeUxRestrictionSource

/**
 * DriveEngine이 구독하는 입력 어댑터 묶음. 다섯 신호가 엔진·조립 지점·테스트를 함께 지나가서 묶었다(코딩 규칙 5).
 *
 * @property gear 기어 신호
 * @property speed 차속 신호
 * @property ux 화면 제한 신호
 * @property driverSeat 운전석 착좌 신호
 * @property location 위치 신호
 */
data class DriveSignalSources(
    val gear: GearSource,
    val speed: SpeedSource,
    val ux: UxRestrictionSource,
    val driverSeat: DriverSeatSource,
    val location: LocationSource,
)

/**
 * 가짜 입력 어댑터 한 벌. 실제 차량 신호가 연결되기 전까지 앱과 테스트가 쓴다.
 * 처음 값은 시동을 막 건 차와 같다: 기어 P, 차속 0, 화면 제한 없음, 착좌는 아직 모름.
 * debug 확인용 화면의 조작 버튼(3-c 다음 작업)이 이 객체로 신호를 바꾼다.
 */
class FakeDriveSignals {

    /** 가짜 기어. */
    val gear = FakeGearSource(Gear.P)

    /** 가짜 차속. */
    val speed = FakeSpeedSource(0f)

    /** 가짜 화면 제한. */
    val ux = FakeUxRestrictionSource(UxRestriction.NONE)

    /** 가짜 운전석 착좌. */
    val driverSeat = FakeDriverSeatSource()

    /** 가짜 위치. 위치를 정하기 전에는 알리지 않는다. */
    val location = FakeLocationSource()

    /**
     * 엔진이 구독할 묶음으로 내놓는다.
     *
     * @return 가짜 신호 묶음
     */
    fun asSources(): DriveSignalSources = DriveSignalSources(gear, speed, ux, driverSeat, location)
}
