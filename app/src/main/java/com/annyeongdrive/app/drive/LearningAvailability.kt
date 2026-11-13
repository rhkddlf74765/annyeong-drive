package com.annyeongdrive.app.drive

/**
 * 지금 교육·퀴즈를 열 수 있는지와, 열 수 없으면 그 이유. 화면은 이 값을 그대로 보여 주기만 한다.
 * 교육 진입 조건(기어 P + 차속 0 + UX 제한 없음)의 판단은 [VehicleSnapshot.learningAvailability]가 한다.
 */
enum class LearningAvailability {
    /** 열 수 있다. */
    AVAILABLE,

    /** 기어·차속·화면 제한 중 아직 받지 못한 신호가 있다. "차량 상태를 확인하고 있어요"로 안내한다. */
    CHECKING_VEHICLE,

    /** 신호는 모두 받았지만 진입 조건이 아니다(P가 아님, 움직이는 중, 화면 제한). 차를 세우고 P로 바꾸라고 안내한다. */
    RESTRICTED,
}
