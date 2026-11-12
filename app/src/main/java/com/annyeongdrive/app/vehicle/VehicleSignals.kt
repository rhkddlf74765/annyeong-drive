package com.annyeongdrive.app.vehicle

import kotlinx.serialization.Serializable

/** 플랫폼이 주행 중 화면 제한(CarUxRestrictions)을 걸었는지. */
enum class UxRestriction {
    /** 제한 없음. 교육 화면을 열 수 있는 조건 중 하나다. */
    NONE,

    /** 제한 있음. 교육 화면과 학습 음성을 닫아야 한다. */
    RESTRICTED,

    /** 신호 미수신. 제한 없음으로 간주하지 않는다. */
    UNKNOWN,
}

/** 운전석 착좌 상태. 착좌는 대기 상태를 떠나는 계기이고, 착좌 해제는 초기화 계기가 아니다. */
enum class SeatOccupancy {
    /** 운전석에 사람이 앉아 있다. */
    OCCUPIED,

    /** 운전석이 비어 있다. */
    VACANT,

    /** 신호 미수신 또는 알 수 없는 값. */
    UNKNOWN,
}

/** 안전띠를 확인하는 좌석. 뒷좌석도 착용 대상이다. */
enum class Seat {
    /** 운전석. */
    DRIVER,

    /** 조수석. */
    FRONT_PASSENGER,

    /** 뒷좌석 왼쪽. */
    REAR_LEFT,

    /** 뒷좌석 가운데. */
    REAR_CENTER,

    /** 뒷좌석 오른쪽. */
    REAR_RIGHT,
}

/**
 * 좌석별 안전띠 미착용 경고 신호.
 * 경고가 없다는 것은 착용 / 빈 좌석 / 센서 없음 / 인식 못 함 중 무엇이든 될 수 있다.
 * 그래서 점검 완료 판단에서 [NO_WARNING]은 [UNKNOWN]과 같게 다룬다(결정 문서 7장).
 */
enum class SeatBeltStatus {
    /** 신호 미수신 또는 알 수 없는 값. */
    UNKNOWN,

    /** 그 좌석에 사람이 앉아 안전띠를 매지 않았다는 경고가 있다. */
    WARNING,

    /** 경고가 없다. 착용했다는 뜻이 아니다. */
    NO_WARNING,
}

/**
 * 차량 유종. 지금은 업체 설정 값에서 온다.
 * 차량 신호(INFO_FUEL_TYPE) 확인 결과에 따라 항목을 다시 맞춘다.
 */
@Serializable
enum class FuelType {
    /** 휘발유. */
    GASOLINE,

    /** 경유. */
    DIESEL,

    /** LPG. */
    LPG,

    /** 전기. 연료 대신 충전 안내로 분기한다. */
    ELECTRIC,

    /** 업체 설정이 없거나 알 수 없는 값. */
    UNKNOWN,
}
