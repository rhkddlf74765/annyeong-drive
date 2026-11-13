package com.annyeongdrive.app.drive

/**
 * 상태가 전이를 판단할 때 읽는 정보 묶음. 상태 객체는 이것만 보고 다음 상태를 정한다.
 * [vehicle]은 Coordinator가 이벤트마다 갱신하고, 나머지는 주입받아 그대로 쓴다.
 *
 * @property vehicle 최신 차량·위치 신호
 * @property rental 대여 상태 창구
 * @property learning 교육 진행 창구
 * @property stopPlaces 정차 장소 조회 창구
 * @property flags 기능 플래그
 * @property returnGate 반납 처리 진입 확인 창구
 * @property roadRules 주행 중 다시 알려 줄 규칙을 찾는 창구(B11). 구역 데이터가 없으면 아무 규칙도 찾지 않는다
 */
data class DriveContext(
    val vehicle: VehicleSnapshot,
    val rental: RentalStatusReader,
    val learning: LearningStatusReader,
    val stopPlaces: StopPlaceLookup,
    val flags: FeatureFlags,
    val returnGate: ReturnGate,
    val roadRules: RoadRuleWatcher = NoRoadRules(),
)
