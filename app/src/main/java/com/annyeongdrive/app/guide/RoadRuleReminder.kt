package com.annyeongdrive.app.guide

/**
 * 주행 중 다시 알려 줄 규칙 하나. 위치 판단(RoadRuleWatcher)이 만들고, Coordinator가 할 일로 넘기고, 안내 관리자가 문장으로 만든다.
 * 제한속도는 유효한 값이 있을 때만 담는다. 값이 없으면 숫자 없는 문장으로 안내한다(CLAUDE.md 도메인 원칙).
 */
sealed interface RoadRuleReminder {

    /** "처음 만났는지"를 세는 규칙 종류. */
    val rule: Rule

    /**
     * 어린이보호구역에 들어왔다.
     *
     * @property zoneId 보호구역 식별자
     * @property limitKmh 유효한 제한속도(km/h). 모르면 null
     */
    data class SchoolZone(val zoneId: String, val limitKmh: Int?) : RoadRuleReminder {
        override val rule: Rule = Rule.SCHOOL_ZONE
    }

    /**
     * 진행 방향 앞에 과속 단속 카메라가 있다.
     *
     * @property cameraId 카메라 식별자
     * @property limitKmh 유효한 제한속도(km/h). 모르면 null
     * @property inSchoolZone 어린이보호구역 단속 카메라인지
     */
    data class SpeedCamera(val cameraId: String, val limitKmh: Int?, val inSchoolZone: Boolean) : RoadRuleReminder {
        override val rule: Rule = Rule.SPEED_CAMERA
    }
}

/**
 * 제한속도 값이 말해도 되는 값인지 보고, 아니면 null로 바꾼다. 없는 숫자는 추정하지 않는다.
 *
 * @param kmh 데이터의 제한속도
 * @return 0보다 크면 그 값, 아니면 null
 */
fun validSpeedLimit(kmh: Int?): Int? {
    val value = kmh ?: return null
    if (value <= 0) return null
    return value
}
