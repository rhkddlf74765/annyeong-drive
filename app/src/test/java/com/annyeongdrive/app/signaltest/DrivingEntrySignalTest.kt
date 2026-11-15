package com.annyeongdrive.app.signaltest

import ai.pleos.playground.vehicle.constant.control.VehicleGear
import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.drive.LearningAvailability
import com.annyeongdrive.app.drive.state.DrivingCruising
import com.annyeongdrive.app.drive.state.ParkedRest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 주행 진입 신호 시나리오: 가짜 SDK가 보낸 기어·차속이 실제 어댑터와 엔진을 거쳐 교육 화면 닫기·학습 음성 중단으로 이어진다.
 * 근거: CLAUDE.md 아키텍처(주행 진입이 생기면 교육 화면 닫기와 학습 음성 중단을 한 번에), 도메인 원칙(교육은 P + 차속 0 + 제한 없음일 때만),
 * 상태 머신 T9, 11/15 사용자 결정(SDK 값을 임의로 넣어 확인하는 신호 테스트).
 */
class DrivingEntrySignalTest {

    private val rig = SignalRig().startRented()

    /** 퀴즈 중 SDK가 D를 보내면 교육 화면 닫기와 학습 음성 중단을 함께 지시하는지 확인한다. */
    @Test
    fun `퀴즈 중 SDK가 D를 보내면 교육 화면을 닫고 학습 음성을 멈춘다`() {
        // 상황
        rig.engine.send(DriveEvent.LearningOpenRequested)
        rig.engine.send(DriveEvent.LearningBegin)
        rig.engine.send(DriveEvent.QuizStartRequested)
        // 행동
        rig.gear.select(VehicleGear.GEAR_DRIVE)
        // 결과
        assertSame(DrivingCruising, rig.state)
        assertEquals(1, rig.screenClosed)
        assertEquals(1, rig.speechStopped)
    }

    /** SDK 기어가 P인 채로 차속만 생겨도 주행으로 보는지 확인한다. */
    @Test
    fun `SDK 기어가 P여도 차속이 생기면 주행으로 본다`() {
        // 상황
        val before = rig.state
        // 행동
        rig.speed.sendKmh(5f)
        // 결과
        assertSame(ParkedRest, before)
        assertSame(DrivingCruising, rig.state)
    }

    /** SDK가 기어를 모른다고 보내면 P로 보지 않아 교육을 열 수 없는지 확인한다. */
    @Test
    fun `SDK가 기어를 모르면 P로 보지 않아 교육을 열 수 없다`() {
        // 상황 · 행동
        rig.gear.select(VehicleGear.GEAR_UNKNOWN)
        // 결과
        assertEquals(LearningAvailability.CHECKING_VEHICLE, rig.engine.vehicle.value.learningAvailability())
    }

    /** 차속 권한이 없어 차속을 받지 못하면 P여도 교육을 열 수 없는지 확인한다(권한 거부 위험). */
    @Test
    fun `차속 권한이 없으면 P여도 교육을 열 수 없다`() {
        // 상황
        val denied = SignalRig()
        denied.speed.readable = false
        // 행동
        denied.startRented()
        // 결과
        assertEquals(LearningAvailability.CHECKING_VEHICLE, denied.engine.vehicle.value.learningAvailability())
    }
}
