package com.annyeongdrive.app.drive

/**
 * 정차 중 "반납 완료"를 눌렀을 때 반납 처리로 들어가도 되는지 확인하는 창구.
 * 지금은 상태 머신대로 항상 통과한다([AlwaysPassReturnGate]).
 * 반납 장소 확인처럼 조건이 정해지면 이 인터페이스의 구현을 바꾸고, [ReturnCheck]에 결과를 추가한다.
 * 상태 머신: B8 (반납 진입 확인 분기)
 */
fun interface ReturnGate {

    /**
     * 지금 반납 처리로 들어갈 수 있는지 확인한다.
     * 상태 머신: B8
     *
     * @param vehicle 최신 차량·위치 신호. 반납 장소 확인 같은 확장에서 위치를 쓴다
     * @return 확인 결과
     */
    fun check(vehicle: VehicleSnapshot): ReturnCheck
}

/**
 * 반납 처리 진입 확인 결과.
 * 결과를 추가하면(예: 반납 장소 밖) 정차 상태의 반납 분기가 컴파일 오류로 드러나 처리할 곳을 빠뜨리지 않는다.
 */
sealed interface ReturnCheck {

    /**
     * 반납 처리로 들어가도 된다.
     * 상태 머신: B8 통과 → 반납 처리
     */
    data object Passed : ReturnCheck
}

/** 조건 없이 항상 통과하는 [ReturnGate]. 반납 장소 확인이 정해지기 전까지 쓴다. */
class AlwaysPassReturnGate : ReturnGate {

    /**
     * 항상 통과를 돌려준다.
     *
     * @param vehicle 쓰지 않는다
     * @return [ReturnCheck.Passed]
     */
    override fun check(vehicle: VehicleSnapshot): ReturnCheck = ReturnCheck.Passed
}
