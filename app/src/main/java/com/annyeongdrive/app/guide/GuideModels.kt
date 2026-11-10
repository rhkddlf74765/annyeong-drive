package com.annyeongdrive.app.guide

import java.util.Locale

/** 안내하는 규칙의 종류. "처음 만났는지"는 이 단위로 센다. */
enum class Rule {
    SCHOOL_ZONE,
    SPEED_CAMERA,
    PARKING_CAMERA,
}

/**
 * 안내 문장 하나가 맡는 역할.
 * 정책(VerbosityPolicy)이 어떤 역할을 넣을지 고르고, 문장집(Phrasebook)이 역할별 문장을 준다.
 * 새 역할(예: 금액 안내 FINE_AMOUNT)이 필요하면 여기 추가하고 문장집에 문장만 넣으면 된다.
 */
enum class SentenceRole {
    /** 무엇을 지켜야 하는가. 예: "School zone. Speed limit 30." */
    CORE,
    /** 왜 지켜야 하고 어기면 어떻게 되는가. 예: "Fines are doubled here." */
    CONSEQUENCE,
}

/** 판단기 사건에서 뽑은 안내 재료. params에는 제한속도 같은 값이 들어간다. */
data class Announcement(
    val rule: Rule,
    val params: Map<String, String> = emptyMap(),
)

/** 실제로 읽을 문장. */
data class Utterance(
    val rule: Rule,
    val encounter: Int,
    val roles: List<SentenceRole>,
    val text: String,
    val locale: Locale,
)

object Params {
    const val LIMIT = "limit"
    const val SCHOOL = "school"   // 과속 카메라가 어린이보호구역 카메라인지("true"/"false")
}
