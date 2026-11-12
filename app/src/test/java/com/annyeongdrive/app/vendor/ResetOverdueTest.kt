package com.annyeongdrive.app.vendor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * 예비 초기화 판단: 반납 예정 시각 + N시간 경과.
 * 근거: 결정 문서 3장(초기화 조건)·5장(예비 초기화), 상태 머신 T4 가드.
 */
class ResetOverdueTest {

    private val configWithDue = VendorConfig.initial()
        .withReturnTerms(ReturnTerms(dueAt = DUE_AT))
        .withResetGrace(Duration.ofHours(2))

    /** 반납 예정 시각을 정하지 않았으면 예비 초기화하지 않는지 확인한다. */
    @Test
    fun `반납 예정 시각이 없으면 예비 초기화하지 않는다`() {
        // 상황
        val config = VendorConfig.initial()
        // 행동
        val overdue = config.isResetOverdue(DUE_AT.plus(Duration.ofDays(30)))
        // 결과
        assertFalse(overdue)
    }

    /** 반납 예정 시각은 지났지만 N시간이 지나기 전이면 예비 초기화하지 않는지 확인한다. */
    @Test
    fun `반납 예정 시각 뒤 N시간이 지나기 전이면 예비 초기화하지 않는다`() {
        // 상황
        val now = DUE_AT.plus(Duration.ofHours(2)).minusSeconds(1)
        // 행동
        val overdue = configWithDue.isResetOverdue(now)
        // 결과
        assertFalse(overdue)
    }

    /** 반납 예정 시각 + N시간이 되면 예비 초기화하는지 확인한다(경계 포함). */
    @Test
    fun `반납 예정 시각 뒤 N시간이 지나면 예비 초기화한다`() {
        // 상황
        val now = DUE_AT.plus(Duration.ofHours(2))
        // 행동
        val overdue = configWithDue.isResetOverdue(now)
        // 결과
        assertTrue(overdue)
    }

    private companion object {
        val DUE_AT: Instant = Instant.parse("2026-11-14T09:00:00Z")
    }
}
