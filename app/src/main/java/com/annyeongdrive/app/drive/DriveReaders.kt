package com.annyeongdrive.app.drive

import com.annyeongdrive.app.location.GeoPoint

/**
 * 대여 상태를 읽는 창구. 실제 구현은 2단계에서 RentalSession·VendorConfig·Clock으로 만든다.
 * Coordinator는 대여 데이터를 복제하지 않고 판단할 때마다 이것으로 읽는다.
 */
interface RentalStatusReader {

    /**
     * 대여 설정(언어 선택 + 출발 전 점검)을 마친 대여가 있는지 알려 준다. T2·T3의 가드다.
     *
     * @return 대여 설정을 마쳤으면 true
     */
    fun hasRentalSetup(): Boolean

    /**
     * 반납 예정 시각 + N시간이 지나 예비 초기화를 해야 하는지 알려 준다. T4의 가드다.
     *
     * @return 예비 초기화 시각이 지났으면 true
     */
    fun isResetOverdue(): Boolean
}

/** 이어 보기로 다시 들어갈 교육·퀴즈 위치(B2). */
enum class LearningResumePoint {
    /** 학습 카드. */
    CARD,

    /** 문제. */
    QUIZ,
}

/**
 * 교육 진행을 읽는 창구. 실제 구현은 2·6단계에서 LearningProgress로 만든다.
 */
interface LearningStatusReader {

    /**
     * 이번 대여에서 교육을 시작한 적이 있는지 알려 준다. B1의 가드다.
     *
     * @return 교육 시작 화면에서 "교육 시작"을 누른 적이 있으면 true
     */
    fun hasStarted(): Boolean

    /**
     * 시작했지만 끝내지 않은 교육이 있는지 알려 준다. T15의 가드다.
     *
     * @return 미완료 교육이 있으면 true
     */
    fun hasUnfinished(): Boolean

    /**
     * 지금 문제가 마지막 문항인지 알려 준다. T29·T30·B6의 가드다.
     *
     * @return 마지막 문항이면 true
     */
    fun isLastQuestion(): Boolean

    /**
     * 이어 보기로 들어갈 위치를 알려 준다(B2).
     *
     * @return 저장된 위치
     */
    fun resumePoint(): LearningResumePoint
}

/**
 * 정차한 장소의 성격을 조회하는 창구. 검수 구역은 8단계, 주유소는 P1에서 구현한다.
 */
interface StopPlaceLookup {

    /**
     * 위치가 검수된 주정차 금지 구역 안이면 그 구역 식별자를 돌려준다. T13의 가드다.
     * GPS 반경만으로 금지를 확정하지 않는다. 검수된 구역 데이터가 있을 때만 값을 돌려준다.
     *
     * @param point 정차한 위치
     * @return 검수된 구역 식별자. 없으면 null
     */
    fun verifiedNoParkingZoneNear(point: GeoPoint): String?

    /**
     * 위치가 주유소 반경 안인지 알려 준다. T14의 가드이고, 오주유 기능 플래그가 켜졌을 때만 부른다.
     *
     * @param point 정차한 위치
     * @return 주유소 반경 안이면 true
     */
    fun isNearGasStation(point: GeoPoint): Boolean
}

/**
 * 철회될 수 있는 기능의 켜짐 여부.
 *
 * @property fuelGuardEnabled 오주유 방지(T14)를 켤지. 기본은 꺼짐
 */
data class FeatureFlags(val fuelGuardEnabled: Boolean = false)
