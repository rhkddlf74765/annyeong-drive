package com.annyeongdrive.app.vehicle

import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 실제 화면 제한 신호 변환: 플랫폼의 "주행 중 화면 최적화 필요" 값을 앱의 제한 상태로 옮긴다.
 * android.car 객체 자체는 에뮬레이터에서 확인한다(9단계).
 * 근거: CLAUDE.md 아키텍처(어댑터는 신호를 옮기기만 한다), docs/decisions-2026-11-11.md 3장, implementation-plan 7단계.
 */
class UxRestrictionMappingTest {

    /** 화면 최적화가 필요하면 제한이다. */
    @Test
    fun `화면 최적화가 필요하면 제한이다`() {
        // 상황 · 행동
        val restriction = UxRestriction.of(requiresDistractionOptimization = true)

        // 결과
        assertSame(UxRestriction.RESTRICTED, restriction)
    }

    /** 화면 최적화가 필요 없으면 제한 없음이다. */
    @Test
    fun `화면 최적화가 필요 없으면 제한 없음이다`() {
        // 상황 · 행동
        val restriction = UxRestriction.of(requiresDistractionOptimization = false)

        // 결과
        assertSame(UxRestriction.NONE, restriction)
    }

    /** 차량 서비스에 연결되지 않아 값을 모르면 알 수 없음이다(제한 없음으로 보지 않는다). */
    @Test
    fun `값을 모르면 알 수 없음이다`() {
        // 상황 · 행동
        val restriction = UxRestriction.of(requiresDistractionOptimization = null)

        // 결과
        assertSame(UxRestriction.UNKNOWN, restriction)
    }
}
