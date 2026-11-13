package com.annyeongdrive.app.admin

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * 업체 설정 화면의 반납 예정 시각 입력: "yyyy-MM-dd HH:mm"을 기기 시간대로 읽고 쓴다.
 * 근거: 결정 문서 5장(반납 예정 시각 + N시간 예비 초기화).
 */
class DueAtTextTest {

    private val seoul: ZoneId = ZoneId.of("Asia/Seoul")

    /** 입력한 시각을 기기 시간대 기준으로 읽는지 확인한다. */
    @Test
    fun `입력한 반납 예정 시각을 기기 시간대로 읽는다`() {
        // 상황
        val text = "2026-11-14 18:00"
        // 행동
        val parsed = parseDueAt(text, seoul)
        // 결과
        assertEquals(DueAtInput.Valid(Instant.parse("2026-11-14T09:00:00Z")), parsed)
    }

    /** 비워 두면 반납 예정 시각이 없는 것으로 읽는지 확인한다. */
    @Test
    fun `비워 두면 반납 예정 시각이 없는 것이다`() {
        // 상황
        val text = "   "
        // 행동
        val parsed = parseDueAt(text, seoul)
        // 결과
        assertEquals(DueAtInput.Empty, parsed)
    }

    /** 형식이 틀리면 형식 오류로 읽는지 확인한다. */
    @Test
    fun `형식이 틀리면 형식 오류다`() {
        // 상황
        val text = "11/14 6pm"
        // 행동
        val parsed = parseDueAt(text, seoul)
        // 결과
        assertEquals(DueAtInput.Invalid, parsed)
    }

    /** 저장된 시각을 화면 글자로 바꾸면 다시 같은 시각으로 읽히는지 확인한다. */
    @Test
    fun `저장된 시각을 글자로 바꿨다가 다시 읽으면 같은 시각이다`() {
        // 상황
        val dueAt = Instant.parse("2026-11-14T09:00:00Z")
        // 행동
        val text = formatDueAt(dueAt, seoul)
        // 결과
        assertEquals("2026-11-14 18:00", text)
        assertEquals(DueAtInput.Valid(dueAt), parseDueAt(text, seoul))
    }
}
