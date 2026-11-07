package com.annyeongdrive.app.zones

import kotlin.math.sin

/**
 * 위치가 들어올 때마다 ZoneData와 비교해 사건(ZoneEvent)을 낸다.
 * 안드로이드에 의존하지 않아 단위 테스트로 검증할 수 있다.
 *
 * 판단 기준 (tools/route.gpx 시연 경로로 검증한 값)
 * - 보호구역: 시설에서 radiusM 안이면 진입, radiusM + 30m 밖이면 이탈. 겹친 구역은 하나로 본다.
 * - 과속 카메라: 250m 안, 거리가 줄어드는 중, 진행 방향에서 60도 안, 진행선에서 옆으로 40m 안.
 *   같은 제한속도의 카메라가 120m 안에서 30초 안에 또 잡히면(길 건너편 카메라) 건너뛴다.
 *   한 번 알린 카메라는 400m 넘게 멀어지면 다시 알릴 수 있다.
 * - 정차: 최근 6개 위치(약 5초)가 3m 안에 머물면 정차. 그때 100m 안의 주정차 카메라를 찾는다.
 *
 * 이 객체의 상태(알린 카메라, 들어간 보호구역 등)는 주행 하나 동안만 유효하다.
 * 새 주행은 DriveSession이 판단기를 새로 만들어 시작한다.
 */
class ZoneDetector(private val data: ZoneData) {

    private val insideZones = mutableSetOf<String>()
    private val announcedCameras = mutableSetOf<String>()
    private val lastCameraDistance = mutableMapOf<String, Double>()
    private val recentCameras = ArrayDeque<RecentCamera>()
    private val history = ArrayDeque<Pair<Double, Double>>()
    private var prev: Pair<Double, Double>? = null
    private var headingDeg: Double? = null
    private var stopped = false

    private data class RecentCamera(val lat: Double, val lon: Double, val limit: Int, val timeMs: Long)

    fun update(lat: Double, lon: Double, timeMs: Long): List<ZoneEvent> {
        val events = mutableListOf<ZoneEvent>()
        updateHeading(lat, lon)
        checkSchoolZones(lat, lon, events)
        checkSpeedCameras(lat, lon, timeMs, events)
        checkStop(lat, lon, events)
        return events
    }

    private fun updateHeading(lat: Double, lon: Double) {
        // 3m 이상 움직였을 때만 방향을 새로 잡는다(정차 중 GPS 흔들림 무시)
        prev?.let { (pl, po) ->
            if (Geo.distanceM(pl, po, lat, lon) < MIN_MOVE_FOR_HEADING_M) return
            headingDeg = Geo.bearingDeg(pl, po, lat, lon)
        }
        prev = lat to lon
    }

    private fun checkSchoolZones(lat: Double, lon: Double, events: MutableList<ZoneEvent>) {
        val wasInside = insideZones.isNotEmpty()
        var entered: Pair<SchoolZone, Double>? = null
        for (zone in data.schoolZones) {
            val d = Geo.distanceM(lat, lon, zone.lat, zone.lon)
            if (zone.id !in insideZones && d <= zone.radiusM) {
                insideZones += zone.id
                if (entered == null) entered = zone to d
            } else if (zone.id in insideZones && d > zone.radiusM + ZONE_EXIT_MARGIN_M) {
                insideZones -= zone.id
            }
        }
        if (!wasInside && entered != null) {
            events += ZoneEvent.SchoolZoneEntered(entered.first, entered.second)
        } else if (wasInside && insideZones.isEmpty()) {
            events += ZoneEvent.SchoolZoneExited
        }
    }

    private fun checkSpeedCameras(lat: Double, lon: Double, timeMs: Long, events: MutableList<ZoneEvent>) {
        val heading = headingDeg
        while (recentCameras.isNotEmpty() && timeMs - recentCameras.first().timeMs > DUPLICATE_WINDOW_MS) {
            recentCameras.removeFirst()
        }
        for (cam in data.speedCameras) {
            val d = Geo.distanceM(lat, lon, cam.lat, cam.lon)
            val prevD = lastCameraDistance.put(cam.id, d)

            if (cam.id in announcedCameras) {
                if (d > CAMERA_RESET_M) announcedCameras -= cam.id
                continue
            }
            if (heading == null || prevD == null || d > CAMERA_AHEAD_M || d >= prevD) continue

            val diff = Geo.angleDiff(Geo.bearingDeg(lat, lon, cam.lat, cam.lon), heading)
            val lateral = d * sin(Math.toRadians(diff))
            if (diff > CAMERA_MAX_ANGLE_DEG || lateral > CAMERA_MAX_LATERAL_M) continue

            announcedCameras += cam.id
            val duplicate = recentCameras.any {
                it.limit == cam.speedLimitKmh &&
                    Geo.distanceM(it.lat, it.lon, cam.lat, cam.lon) < DUPLICATE_DISTANCE_M
            }
            if (duplicate) continue
            recentCameras.addLast(RecentCamera(cam.lat, cam.lon, cam.speedLimitKmh, timeMs))
            events += ZoneEvent.SpeedCameraAhead(cam, d)
        }
    }

    private fun checkStop(lat: Double, lon: Double, events: MutableList<ZoneEvent>) {
        history.addLast(lat to lon)
        while (history.size > STOP_WINDOW) history.removeFirst()
        val (fl, fo) = history.first()
        val isStopped = history.size == STOP_WINDOW && Geo.distanceM(fl, fo, lat, lon) < STOP_MAX_MOVE_M
        if (isStopped && !stopped) {
            data.parkingCameras
                .map { it to Geo.distanceM(lat, lon, it.lat, it.lon) }
                .filter { it.second <= PARKING_NEAR_M }
                .minByOrNull { it.second }
                ?.let { (cam, d) -> events += ZoneEvent.StoppedNearParkingCamera(cam, d) }
        }
        stopped = isStopped
    }

    companion object {
        private const val MIN_MOVE_FOR_HEADING_M = 3.0
        private const val ZONE_EXIT_MARGIN_M = 30.0
        private const val CAMERA_AHEAD_M = 250.0
        private const val CAMERA_RESET_M = 400.0
        private const val CAMERA_MAX_ANGLE_DEG = 60.0
        private const val CAMERA_MAX_LATERAL_M = 40.0
        private const val DUPLICATE_DISTANCE_M = 120.0
        private const val DUPLICATE_WINDOW_MS = 30_000L
        private const val STOP_WINDOW = 6
        private const val STOP_MAX_MOVE_M = 3.0
        private const val PARKING_NEAR_M = 100.0
    }
}
