package com.annyeongdrive.app.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * 위치가 새것인지: 10초 안에 잰 위치만 안내에 쓴다. 오래된 위치로 안내하지 않는다.
 * 근거: 기능 보드 "주행 중 규칙 안내" 5번(오래된 이벤트 폐기), implementation-plan 8단계.
 */
class LocationFreshnessTest {

    private val now = Instant.parse("2026-11-14T09:00:00Z")

    /** 3초 전에 잰 위치는 새것이다. */
    @Test
    fun `3초 전 위치는 쓴다`() {
        // 상황 · 행동 · 결과
        assertTrue(LocationFreshness.isFresh(now.minusSeconds(3), now))
    }

    /** 11초 전에 잰 위치는 버린다. */
    @Test
    fun `11초 전 위치는 버린다`() {
        // 상황 · 행동 · 결과
        assertFalse(LocationFreshness.isFresh(now.minusSeconds(11), now))
    }

    /** 기기 시계 차이로 미래 시각이 와도 새것으로 본다. */
    @Test
    fun `미래 시각의 위치는 새것으로 본다`() {
        // 상황 · 행동 · 결과
        assertTrue(LocationFreshness.isFresh(now.plusSeconds(2), now))
    }
}
