package com.annyeongdrive.app.guide

import com.annyeongdrive.app.rental.AppLanguage
import java.util.Locale

/**
 * 대여 언어로 안내 문장을 읽고 말할 Locale을 고른다. 언어만이 아니라 나라까지 붙인다.
 * Pleos TTS는 "en"처럼 나라가 없는 값은 읽지 못하고(ON_DEVICE_ENGINE_ERROR) "en_US"는 읽는다(11/14 에뮬레이터 확인).
 *
 * @param language 대여 언어
 * @return 문장 리소스와 음성에 쓸 Locale
 */
fun voiceLocaleOf(language: AppLanguage): Locale = when (language) {
    AppLanguage.KOREAN -> Locale.KOREA
    AppLanguage.ENGLISH -> Locale.US
    AppLanguage.JAPANESE -> Locale.JAPAN
    AppLanguage.SPANISH -> Locale.forLanguageTag("es-ES")
}
