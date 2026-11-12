package com.annyeongdrive.app.gear

/**
 * 변속 기어 단.
 * 신호를 아직 받지 못했거나 알 수 없는 값이면 [UNKNOWN]이다. 모르는 값을 P로 간주하지 않는다.
 */
enum class Gear {
    /** 주차. */
    P,

    /** 후진. */
    R,

    /** 중립. 정차 진입·주행 진입 어느 쪽의 계기도 아니다. */
    N,

    /** 전진. */
    D,

    /** 신호 미수신 또는 알 수 없는 값. */
    UNKNOWN,
}
