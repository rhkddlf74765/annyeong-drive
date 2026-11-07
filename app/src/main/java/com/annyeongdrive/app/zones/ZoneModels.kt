package com.annyeongdrive.app.zones

/** 어린이보호구역 한 곳. 시설 위치를 중심으로 radiusM 안을 보호구역으로 본다. */
data class SchoolZone(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val radiusM: Double,
    val speedLimitKmh: Int,
)

/** 과속 단속 카메라. protectedZone은 "school" / "senior" / "none". */
data class SpeedCamera(
    val id: String,
    val lat: Double,
    val lon: Double,
    val speedLimitKmh: Int,
    val protectedZone: String,
    val place: String,
)

/** 구청 주정차 단속 카메라. */
data class ParkingCamera(
    val id: String,
    val lat: Double,
    val lon: Double,
    val place: String,
)

data class ZoneData(
    val schoolZones: List<SchoolZone>,
    val speedCameras: List<SpeedCamera>,
    val parkingCameras: List<ParkingCamera>,
)

/** 판단기가 알려 주는 사건. 안내 문장은 다음 단계(안내 관리자)에서 만든다. */
sealed interface ZoneEvent {
    /** 보호구역 밖에 있다가 하나 이상의 보호구역 안으로 들어왔다. */
    data class SchoolZoneEntered(val zone: SchoolZone, val distanceM: Double) : ZoneEvent

    /** 겹쳐 있던 보호구역을 모두 벗어났다. */
    data object SchoolZoneExited : ZoneEvent

    /** 진행 방향 앞쪽에 과속 카메라가 있다. 카메라마다 한 번만 낸다. */
    data class SpeedCameraAhead(val camera: SpeedCamera, val distanceM: Double) : ZoneEvent

    /** 차가 멈췄고 가까이에 주정차 단속 카메라가 있다. 멈출 때마다 한 번 낸다. */
    data class StoppedNearParkingCamera(val camera: ParkingCamera, val distanceM: Double) : ZoneEvent
}
