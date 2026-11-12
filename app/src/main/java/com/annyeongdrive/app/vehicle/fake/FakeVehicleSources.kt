package com.annyeongdrive.app.vehicle.fake

import com.annyeongdrive.app.vehicle.DriverSeatSource
import com.annyeongdrive.app.vehicle.FuelType
import com.annyeongdrive.app.vehicle.FuelTypeSource
import com.annyeongdrive.app.vehicle.Seat
import com.annyeongdrive.app.vehicle.SeatBeltSource
import com.annyeongdrive.app.vehicle.SeatBeltStatus
import com.annyeongdrive.app.vehicle.SeatOccupancy
import com.annyeongdrive.app.vehicle.SignalSource
import com.annyeongdrive.app.vehicle.SpeedSource
import com.annyeongdrive.app.vehicle.UxRestriction
import com.annyeongdrive.app.vehicle.UxRestrictionSource

/** 개발·시연용 가짜 차속 신호. [changeSpeed]로 차속을 바꾼다. */
class FakeSpeedSource private constructor(
    private val signal: FakeSignal<Float>,
) : SpeedSource, SignalSource<Float> by signal {

    /**
     * 처음 알릴 차속으로 가짜 신호를 만든다.
     *
     * @param initialKmh 구독을 시작할 때 처음 알릴 차속(km/h)
     */
    constructor(initialKmh: Float = 0f) : this(FakeSignal(initialKmh))

    /**
     * 차속을 바꾸고 구독자에게 알린다.
     *
     * @param kmh 새 차속(km/h)
     */
    fun changeSpeed(kmh: Float) = signal.emit(kmh)
}

/** 개발·시연용 가짜 UX 제한 신호. [change]로 제한 상태를 바꾼다. */
class FakeUxRestrictionSource private constructor(
    private val signal: FakeSignal<UxRestriction>,
) : UxRestrictionSource, SignalSource<UxRestriction> by signal {

    /**
     * 처음 알릴 제한 상태로 가짜 신호를 만든다.
     *
     * @param initial 구독을 시작할 때 처음 알릴 제한 상태
     */
    constructor(initial: UxRestriction = UxRestriction.NONE) : this(FakeSignal(initial))

    /**
     * 제한 상태를 바꾸고 구독자에게 알린다.
     *
     * @param restriction 새 제한 상태
     */
    fun change(restriction: UxRestriction) = signal.emit(restriction)
}

/** 개발·시연용 가짜 운전석 착좌 신호. [sit]과 [leave]로 착좌를 바꾼다. */
class FakeDriverSeatSource private constructor(
    private val signal: FakeSignal<SeatOccupancy>,
) : DriverSeatSource, SignalSource<SeatOccupancy> by signal {

    /**
     * 처음 알릴 착좌 상태로 가짜 신호를 만든다.
     *
     * @param initial 구독을 시작할 때 처음 알릴 착좌 상태
     */
    constructor(initial: SeatOccupancy = SeatOccupancy.UNKNOWN) : this(FakeSignal(initial))

    /** 운전석에 앉았다고 알린다. */
    fun sit() = signal.emit(SeatOccupancy.OCCUPIED)

    /** 운전석에서 일어났다고 알린다. */
    fun leave() = signal.emit(SeatOccupancy.VACANT)
}

/**
 * 가짜 안전띠 신호. 차량 신호 확인 전이라 모든 좌석을 항상 [SeatBeltStatus.UNKNOWN]으로 알린다(결정 문서 7장).
 */
class FakeSeatBeltSource : SeatBeltSource,
    SignalSource<Map<Seat, SeatBeltStatus>> by FakeSignal(Seat.entries.associateWith { SeatBeltStatus.UNKNOWN })

/**
 * 가짜 유종 신호. 업체 설정 값을 그대로 돌려준다.
 *
 * @param fuelType 돌려줄 유종
 */
class FakeFuelTypeSource(private val fuelType: FuelType = FuelType.UNKNOWN) : FuelTypeSource {

    /**
     * 만들 때 받은 유종을 돌려준다.
     *
     * @return 유종
     */
    override fun fuelType(): FuelType = fuelType
}
