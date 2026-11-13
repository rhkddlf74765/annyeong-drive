package com.annyeongdrive.app.drive

import com.annyeongdrive.app.location.GeoPoint

/**
 * 구역 데이터가 없을 때의 정차 장소 조회. 어디에서 멈춰도 검수 구역·주유소가 아니라고 답한다.
 * GPS 반경만으로 주정차 금지를 정하지 않는다는 원칙에 따라, 데이터가 없으면 경고하지 않는다.
 * 앱은 zones.json으로 ZoneStopPlaces를 쓰고(8단계), 이 구현은 테스트가 쓴다.
 */
class NoStopPlaces : StopPlaceLookup {

    /**
     * 검수된 주정차 금지 구역이 없다고 답한다.
     * 상태 머신: T13 가드
     *
     * @param point 정차한 위치
     * @return 항상 null
     */
    override fun verifiedNoParkingZoneNear(point: GeoPoint): String? = null

    /**
     * 주유소 반경이 아니라고 답한다.
     * 상태 머신: T14 가드
     *
     * @param point 정차한 위치
     * @return 항상 false
     */
    override fun isNearGasStation(point: GeoPoint): Boolean = false
}
