package com.annyeongdrive.app.rental

import kotlinx.serialization.Serializable

/**
 * 운전자가 고르는 앱 언어. 기본 지원은 네 언어이고 1차 제출에는 한국어·영어만 연다(결정 문서 1장).
 *
 * @property tag BCP 47 언어 태그
 */
@Serializable
enum class AppLanguage(val tag: String) {
    /** 한국어. */
    KOREAN("ko"),

    /** 영어. */
    ENGLISH("en"),

    /** 일본어. 1차 제출 범위 밖. */
    JAPANESE("ja"),

    /** 스페인어. 1차 제출 범위 밖. */
    SPANISH("es"),
}
