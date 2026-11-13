package com.annyeongdrive.app.drive

import com.annyeongdrive.app.gear.Gear
import com.annyeongdrive.app.vehicle.UxRestriction
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 교육을 열 수 있는지와 열 수 없는 이유: 진입 조건(P + 차속 0 + UX 제한 없음)이면 열 수 있고,
 * 아직 받지 못한 신호가 있으면 확인 중, 신호를 모두 받았는데 조건이 아니면 제한이다.
 * 근거: CLAUDE.md 도메인 원칙(교육 화면 진입 조건), 기능 실행안 부록 E(안내 문구), docs/state-machine.md T19·T21 가드, implementation-plan 7단계.
 */
class LearningAvailabilityTest {

    private val parked = VehicleSnapshot(gear = Gear.P, speedKmh = 0f, ux = UxRestriction.NONE, location = null)

    /** P + 차속 0 + 제한 없음이면 열 수 있다. */
    @Test
    fun `P와 차속 0이고 제한이 없으면 열 수 있다`() {
        // 상황 · 행동
        val availability = parked.learningAvailability()

        // 결과
        assertSame(LearningAvailability.AVAILABLE, availability)
    }

    /** 앱 시작 직후처럼 아무 신호도 받지 못했으면 확인 중이다. */
    @Test
    fun `아무 신호도 받지 못했으면 차량 상태 확인 중이다`() {
        // 상황 · 행동
        val availability = VehicleSnapshot.UNKNOWN.learningAvailability()

        // 결과
        assertSame(LearningAvailability.CHECKING_VEHICLE, availability)
    }

    /** P와 차속 0이어도 화면 제한 신호를 모르면 열지 않고 확인 중으로 본다(알 수 없음은 제한 없음이 아니다). */
    @Test
    fun `화면 제한 신호만 몰라도 차량 상태 확인 중이다`() {
        // 상황 · 행동
        val availability = parked.copy(ux = UxRestriction.UNKNOWN).learningAvailability()

        // 결과
        assertSame(LearningAvailability.CHECKING_VEHICLE, availability)
    }

    /** 화면 제한이 걸려 있으면 제한이다. */
    @Test
    fun `화면 제한이 걸려 있으면 제한이다`() {
        // 상황 · 행동
        val availability = parked.copy(ux = UxRestriction.RESTRICTED).learningAvailability()

        // 결과
        assertSame(LearningAvailability.RESTRICTED, availability)
    }

    /** 기어가 N처럼 P가 아니면 신호를 모두 받았어도 제한이다. */
    @Test
    fun `기어가 P가 아니면 제한이다`() {
        // 상황 · 행동
        val availability = parked.copy(gear = Gear.N).learningAvailability()

        // 결과
        assertSame(LearningAvailability.RESTRICTED, availability)
    }
}
