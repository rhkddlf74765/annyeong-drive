package com.annyeongdrive.app.guide

import java.util.Locale

/** 언어별 안내 문장집. 언어를 추가하려면 이 인터페이스를 구현한다. */
interface Phrasebook {
    val locale: Locale

    /** 이 역할의 문장이 없으면 null을 돌려준다(그 역할은 건너뛴다). */
    fun sentence(rule: Rule, role: SentenceRole, params: Map<String, String>): String?
}

/**
 * 영어 문장집. 과태료 금액은 출처 확인 전이라 넣지 않는다(11/8 결정).
 * 금액을 넣게 되면 SentenceRole에 역할을 추가하거나 CONSEQUENCE 문장만 바꾼다.
 */
object EnglishPhrasebook : Phrasebook {
    override val locale: Locale = Locale.US

    override fun sentence(rule: Rule, role: SentenceRole, params: Map<String, String>): String? {
        val limit = params[Params.LIMIT]
        return when (rule) {
            Rule.SCHOOL_ZONE -> when (role) {
                SentenceRole.CORE -> schoolZoneCore(limit)
                SentenceRole.CONSEQUENCE -> "Children cross here, and fines are doubled."
            }
            Rule.SPEED_CAMERA -> when (role) {
                SentenceRole.CORE ->
                    if (params[Params.SCHOOL] == "true") "School zone camera ahead. Limit $limit."
                    else "Speed camera ahead. Limit $limit."
                SentenceRole.CONSEQUENCE ->
                    "Tickets go to the rental company and are charged to you later."
            }
            Rule.PARKING_CAMERA -> when (role) {
                SentenceRole.CORE -> "Parking enforcement area nearby. Check the signs for allowed times and exceptions."
                SentenceRole.CONSEQUENCE ->
                    "Parking tickets go to the rental company and are charged to you later."
            }
        }
    }

    /**
     * 보호구역 핵심 문장. 유효한 제한속도가 있을 때만 숫자를 말한다(30으로 채우지 않는다).
     *
     * @param limit 제한속도. 없으면 null
     * @return 핵심 문장
     */
    private fun schoolZoneCore(limit: String?): String {
        if (limit == null) return "School zone. Follow the posted speed limit."
        return "School zone. Speed limit $limit."
    }
}
