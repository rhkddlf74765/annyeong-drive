package com.annyeongdrive.app.gear

import com.annyeongdrive.app.vehicle.SignalSource
import com.annyeongdrive.app.vehicle.fake.FakeSignal

/**
 * 개발·시연용 가짜 기어 신호. 개발용 버튼이나 테스트에서 [shift]로 기어를 바꾼다.
 * 실제 차량 신호(CarGearSource)가 준비되면 이 구현을 교체한다.
 */
class FakeGearSource private constructor(
    private val signal: FakeSignal<Gear>,
) : GearSource, SignalSource<Gear> by signal {

    /**
     * 처음 알릴 기어로 가짜 신호를 만든다.
     *
     * @param initial 구독을 시작할 때 처음 알릴 기어
     */
    constructor(initial: Gear = Gear.P) : this(FakeSignal(initial))

    /**
     * 기어를 바꾸고 구독자에게 알린다.
     *
     * @param gear 바꿀 기어
     */
    fun shift(gear: Gear) = signal.emit(gear)
}
