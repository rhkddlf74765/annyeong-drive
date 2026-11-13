package com.annyeongdrive.app.drive

import com.annyeongdrive.app.guide.RoadRuleReminder
import com.annyeongdrive.app.location.GeoPoint

/**
 * 주행 중 위치를 보고 다시 알려 줄 규칙(보호구역 진입, 카메라 접근)을 찾는 창구.
 * 같은 지점을 반복해 알리지 않는 판단은 구현이 맡는다. Coordinator는 주행 중 + 기어 D/R일 때만 부른다.
 * 상태 머신: B11
 */
fun interface RoadRuleWatcher {

    /**
     * 새 위치에서 알려 줄 규칙을 찾는다.
     *
     * @param point 지금 위치
     * @return 알려 줄 규칙 목록. 없으면 빈 목록
     */
    fun observe(point: GeoPoint): List<RoadRuleReminder>
}

/** 구역 데이터가 없을 때의 규칙 판단. 아무 규칙도 찾지 않는다. 테스트와 기본값에 쓴다. */
class NoRoadRules : RoadRuleWatcher {

    /**
     * 알려 줄 규칙이 없다고 답한다.
     *
     * @param point 지금 위치
     * @return 항상 빈 목록
     */
    override fun observe(point: GeoPoint): List<RoadRuleReminder> = emptyList()
}
