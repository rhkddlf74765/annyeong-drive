package com.annyeongdrive.app.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/**
 * 안내 알림 표시 시간: 주행 안내는 8초, 주정차 경고는 10초 뒤 알림을 지운다. 화면 위 알림이 남아 버튼을 가리지 않게 한다.
 * 근거: 11/15 스모크 확인(한국어 대여에서 주정차 경고 알림이 남아 "반납 완료" 버튼을 가림), 11/15 사용자 결정(알림 자동 삭제, 8초·10초).
 */
class NotificationLifetimeTest {

    /** 주행 중 규칙 안내 알림은 8초 뒤 지우는지 확인한다. */
    @Test
    fun `주행 안내 알림은 8초 뒤 지운다`() {
        // 상황 · 행동
        val lifetime = NotificationLifetime.of(GuidanceChannel.ROAD_RULE)

        // 결과
        assertEquals(Duration.ofSeconds(8), lifetime)
    }

    /** 주정차 경고 알림은 두 문장을 읽을 시간을 더 주어 10초 뒤 지우는지 확인한다. */
    @Test
    fun `주정차 경고 알림은 10초 뒤 지운다`() {
        // 상황 · 행동
        val lifetime = NotificationLifetime.of(GuidanceChannel.PARKING_WARNING)

        // 결과
        assertEquals(Duration.ofSeconds(10), lifetime)
    }

    /** 모든 알림 종류가 시간을 가지고, 그 시간이 0보다 큰지 확인한다(영원히 남는 알림이 없다). */
    @Test
    fun `모든 알림은 정해진 시간 뒤 사라진다`() {
        // 상황 · 행동
        val lifetimes = GuidanceChannel.entries.map(NotificationLifetime::of)

        // 결과
        assertTrue(lifetimes.all { !it.isZero && !it.isNegative })
    }
}
