package com.annyeongdrive.app.admin

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** 업체 설정 화면에서 반납 예정 시각을 쓰고 읽는 형식(예: 2026-11-14 18:00). */
private val DUE_AT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** 반납 예정 시각 입력을 읽은 결과. */
sealed interface DueAtInput {

    /** 비워 두었다. 반납 예정 시각이 없으면 예비 초기화를 하지 않는다. */
    data object Empty : DueAtInput

    /**
     * 올바른 시각이다.
     *
     * @property instant 읽은 시각
     */
    data class Valid(val instant: Instant) : DueAtInput

    /** 형식이 틀렸다. 저장하지 않는다. */
    data object Invalid : DueAtInput
}

/**
 * 반납 예정 시각을 화면에 보여 줄 글자로 바꾼다.
 *
 * @param instant 반납 예정 시각. 없으면 null
 * @param zone 기기 시간대
 * @return "yyyy-MM-dd HH:mm" 글자. 시각이 없으면 빈 글자
 */
fun formatDueAt(instant: Instant?, zone: ZoneId): String {
    val value = instant ?: return ""
    return DUE_AT_FORMAT.format(value.atZone(zone))
}

/**
 * 화면에 입력한 반납 예정 시각을 읽는다. 앞뒤 공백은 무시한다.
 *
 * @param text 입력한 글자
 * @param zone 기기 시간대
 * @return 빈 값·올바른 시각·형식 오류 중 하나
 */
fun parseDueAt(text: String, zone: ZoneId): DueAtInput {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return DueAtInput.Empty
    return try {
        DueAtInput.Valid(LocalDateTime.parse(trimmed, DUE_AT_FORMAT).atZone(zone).toInstant())
    } catch (e: DateTimeParseException) {
        DueAtInput.Invalid
    }
}
