package com.annyeongdrive.app.guide

import androidx.annotation.StringRes
import com.annyeongdrive.app.R

/**
 * 안내 문장 하나: 문구 ID와 넣을 제한속도. 문장은 strings.xml에 있고, 대여 언어의 리소스로 읽는다.
 *
 * @property textId 문구 ID
 * @property limitKmh 문장에 넣을 제한속도. 숫자가 없는 문장이면 null
 */
data class GuidanceSentence(@param:StringRes val textId: Int, val limitKmh: Int? = null)

/**
 * 규칙과 문장 역할로 안내 문장을 고른다. 제한속도가 없으면 숫자 없는 문장을 고른다(30으로 채우지 않는다).
 * 주정차 문장은 금지를 단정하지 않고 표지·시간·예외 확인을 요청한다(기능 보드 "주정차 경고" 3번).
 * 문구는 부록 A-8·E 원문을 받기 전 임시 문구다.
 */
object GuidanceSentences {

    /**
     * 주행 중 규칙의 한 역할 문장을 고른다.
     *
     * @param reminder 알려 줄 규칙
     * @param role 문장 역할
     * @return 안내 문장
     */
    fun of(reminder: RoadRuleReminder, role: SentenceRole): GuidanceSentence = when (reminder) {
        is RoadRuleReminder.SchoolZone -> schoolZone(reminder, role)
        is RoadRuleReminder.SpeedCamera -> speedCamera(reminder, role)
    }

    /**
     * 주정차 경고의 한 역할 문장을 고른다.
     *
     * @param role 문장 역할
     * @return 안내 문장
     */
    fun parkingWarning(role: SentenceRole): GuidanceSentence = when (role) {
        SentenceRole.CORE -> GuidanceSentence(R.string.guide_parking_core)
        SentenceRole.CONSEQUENCE -> GuidanceSentence(R.string.guide_parking_consequence)
    }

    /**
     * 어린이보호구역 문장을 고른다.
     *
     * @param reminder 보호구역 진입
     * @param role 문장 역할
     * @return 안내 문장
     */
    private fun schoolZone(reminder: RoadRuleReminder.SchoolZone, role: SentenceRole): GuidanceSentence = when (role) {
        SentenceRole.CORE -> withLimit(reminder.limitKmh, R.string.guide_school_zone_core_limit, R.string.guide_school_zone_core)
        SentenceRole.CONSEQUENCE -> GuidanceSentence(R.string.guide_school_zone_consequence)
    }

    /**
     * 과속 단속 카메라 문장을 고른다. 어린이보호구역 카메라는 그렇다고 말한다.
     *
     * @param reminder 카메라 접근
     * @param role 문장 역할
     * @return 안내 문장
     */
    private fun speedCamera(reminder: RoadRuleReminder.SpeedCamera, role: SentenceRole): GuidanceSentence {
        if (role == SentenceRole.CONSEQUENCE) return GuidanceSentence(R.string.guide_camera_consequence)
        if (reminder.inSchoolZone) {
            return withLimit(reminder.limitKmh, R.string.guide_school_camera_core_limit, R.string.guide_school_camera_core)
        }
        return withLimit(reminder.limitKmh, R.string.guide_camera_core_limit, R.string.guide_camera_core)
    }

    /**
     * 제한속도가 있으면 숫자 문장을, 없으면 숫자 없는 문장을 고른다.
     *
     * @param limitKmh 유효한 제한속도. 없으면 null
     * @param withLimitId 숫자를 넣는 문구 ID
     * @param withoutLimitId 숫자 없는 문구 ID
     * @return 안내 문장
     */
    private fun withLimit(limitKmh: Int?, @StringRes withLimitId: Int, @StringRes withoutLimitId: Int): GuidanceSentence {
        if (limitKmh == null) return GuidanceSentence(withoutLimitId)
        return GuidanceSentence(withLimitId, limitKmh)
    }
}
