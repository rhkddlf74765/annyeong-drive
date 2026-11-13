package com.annyeongdrive.app.zones

import com.annyeongdrive.app.drive.RoadRuleWatcher
import com.annyeongdrive.app.guide.RoadRuleReminder
import com.annyeongdrive.app.guide.validSpeedLimit
import com.annyeongdrive.app.location.GeoPoint
import com.annyeongdrive.app.time.Clock

/**
 * 시연 지역 구역 데이터(zones.json)로 주행 중 규칙을 찾는다. 판단은 검증된 ZoneDetector를 그대로 쓰고,
 * 사건을 규칙 안내 재료로 옮기기만 한다. 정차 중 주정차 카메라 사건은 쓰지 않는다(정차 순간 판단은 T13이 맡는다).
 * 상태 머신: B11
 *
 * @param data 구역 데이터
 * @param clock 지금 시각(같은 카메라 중복 판단의 시간 창에 쓴다)
 */
class ZoneRoadRuleWatcher(data: ZoneData, private val clock: Clock) : RoadRuleWatcher {

    private val detector = ZoneDetector(data)

    /**
     * 새 위치를 판단기에 넘기고 나온 사건 중 알려 줄 규칙만 돌려준다.
     *
     * @param point 지금 위치
     * @return 알려 줄 규칙 목록
     */
    override fun observe(point: GeoPoint): List<RoadRuleReminder> =
        detector.update(point.latitude, point.longitude, clock.now().toEpochMilli()).mapNotNull(::reminderOf)

    /**
     * 판단기 사건을 규칙 안내 재료로 옮긴다. 제한속도는 유효한 값만 담는다.
     *
     * @param event 판단기 사건
     * @return 알려 줄 규칙. 알리지 않는 사건이면 null
     */
    private fun reminderOf(event: ZoneEvent): RoadRuleReminder? = when (event) {
        is ZoneEvent.SchoolZoneEntered ->
            RoadRuleReminder.SchoolZone(event.zone.id, validSpeedLimit(event.zone.speedLimitKmh))
        is ZoneEvent.SpeedCameraAhead -> RoadRuleReminder.SpeedCamera(
            cameraId = event.camera.id,
            limitKmh = validSpeedLimit(event.camera.speedLimitKmh),
            inSchoolZone = event.camera.protectedZone == SCHOOL,
        )
        ZoneEvent.SchoolZoneExited -> null
        is ZoneEvent.StoppedNearParkingCamera -> null
    }

    private companion object {
        /** 어린이보호구역 카메라를 뜻하는 protectedZone 값. */
        const val SCHOOL = "school"
    }
}
