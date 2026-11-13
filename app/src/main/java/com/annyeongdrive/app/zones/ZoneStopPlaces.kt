package com.annyeongdrive.app.zones

import com.annyeongdrive.app.drive.StopPlaceLookup
import com.annyeongdrive.app.location.GeoPoint

/**
 * 시연 지역 구역 데이터(zones.json)로 정차 장소를 조회한다. 검수된 주정차 구역은 구청 주정차 단속 카메라 위치다.
 * GPS 반경만으로 금지를 확정하지 않는다: 이 조회는 "가까이에 검수 구역이 있다"만 알려 주고, 문구는 확인을 요청한다.
 * 상태 머신: T13·T14 가드
 *
 * @param data 구역 데이터
 * @param nearM 검수 구역으로 볼 거리(m). 기능 보드 "주정차 경고" 기준 100m
 */
class ZoneStopPlaces(private val data: ZoneData, private val nearM: Double = DEFAULT_NEAR_M) : StopPlaceLookup {

    /**
     * 정차한 위치에서 [nearM] 안의 가장 가까운 주정차 단속 카메라를 찾는다.
     * 상태 머신: T13 가드
     *
     * @param point 정차한 위치
     * @return 카메라 식별자. 가까이에 없으면 null
     */
    override fun verifiedNoParkingZoneNear(point: GeoPoint): String? = data.parkingCameras
        .map { it to Geo.distanceM(point.latitude, point.longitude, it.lat, it.lon) }
        .filter { (_, distance) -> distance <= nearM }
        .minByOrNull { (_, distance) -> distance }
        ?.first?.id

    /**
     * [구현 예정] 주유소 반경인지 판단한다. implementation-plan.md P1 "오주유 방지 뼈대"에서 시연 지역 주유소 좌표로 구현한다.
     * 오주유 기능 플래그가 꺼져 있어 지금은 Coordinator가 부르지 않는다(T14 가드가 플래그를 먼저 본다).
     *
     * @param point 정차한 위치
     * @return 주유소 반경 안이면 true
     */
    override fun isNearGasStation(point: GeoPoint): Boolean = TODO("구현 예정: P1 시연 지역 주유소 좌표 비교")

    private companion object {
        const val DEFAULT_NEAR_M = 100.0
    }
}
