package com.annyeongdrive.app.service

import com.annyeongdrive.app.drive.AlwaysPassReturnGate
import com.annyeongdrive.app.drive.DriveContext
import com.annyeongdrive.app.drive.DriveCoordinator
import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.drive.FakeLearningStatus
import com.annyeongdrive.app.drive.FakeRentalStatus
import com.annyeongdrive.app.drive.FeatureFlags
import com.annyeongdrive.app.drive.NoStopPlaces
import com.annyeongdrive.app.drive.VehicleSnapshot
import com.annyeongdrive.app.drive.state.DrivingCruising
import com.annyeongdrive.app.drive.state.Idle
import com.annyeongdrive.app.drive.state.ParkedRest
import com.annyeongdrive.app.drive.state.Returning
import com.annyeongdrive.app.gear.Gear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 엔진: 가짜 입력 신호와 화면 동작이 Coordinator를 거쳐 화면 상태와 출력 창구로 나간다.
 * 근거: CLAUDE.md 아키텍처(입력 → DriveCoordinator → 출력 실행기), implementation-plan 3단계, 상태 머신 T3·T9·T31.
 */
class DriveEngineTest {

    private val rental = FakeRentalStatus().apply { hasRentalSetup = true }
    private val learning = FakeLearningStatus().apply { started = true }
    private val signals = FakeDriveSignals()
    private val recorded = RecordingPorts()
    private val engine = DriveEngine(
        coordinator = DriveCoordinator(
            DriveContext(VehicleSnapshot.UNKNOWN, rental, learning, NoStopPlaces(), FeatureFlags(), AlwaysPassReturnGate()),
        ),
        sources = signals.asSources(),
        executor = DriveActionExecutor(recorded.asPorts()),
    )

    /** 엔진을 시작하고 운전석에 앉아 정차 대기에 있는 상태를 만든다(가짜 신호 처음 값: P · 0 · 제한 없음). */
    private fun startSeated() {
        engine.start()
        signals.driverSeat.sit()
    }

    /** 가짜 착좌·기어 신호가 바뀌면 화면 상태가 Coordinator 상태를 따라가는지 확인한다(T3, T9). */
    @Test
    fun `가짜 신호가 바뀌면 화면 상태가 Coordinator 상태를 따라간다`() {
        // 상황
        startSeated()
        val parked = engine.state.value
        // 행동
        signals.gear.shift(Gear.D)
        // 결과
        assertSame(ParkedRest, parked)
        assertSame(DrivingCruising, engine.state.value)
    }

    /** 퀴즈 중 D로 바꾸면 교육 화면 닫기와 학습 음성 중단 창구가 함께 불리는지 확인한다(T9). */
    @Test
    fun `퀴즈 중 D로 바꾸면 교육 화면 닫기와 학습 음성 중단 창구가 불린다`() {
        // 상황
        startSeated()
        engine.send(DriveEvent.LearningOpenRequested)
        engine.send(DriveEvent.LearningBegin)
        engine.send(DriveEvent.QuizStartRequested)
        // 행동
        signals.gear.shift(Gear.D)
        // 결과
        assertEquals(1, recorded.screenClosed)
        assertEquals(1, recorded.speechStopped)
    }

    /** 화면에서 보낸 사용자 동작이 신호와 같은 흐름으로 처리되는지 확인한다(T31). */
    @Test
    fun `화면에서 보낸 사용자 동작도 같은 흐름으로 처리된다`() {
        // 상황
        startSeated()
        // 행동
        engine.send(DriveEvent.ReturnRequested)
        // 결과
        assertSame(Returning, engine.state.value)
    }

    /** 반납이 확정되면 운전자 데이터 지우기 창구가 불리고 대기로 가는지 확인한다(T32, T5). */
    @Test
    fun `반납이 확정되면 운전자 데이터 지우기 창구가 불린다`() {
        // 상황
        startSeated()
        engine.send(DriveEvent.ReturnRequested)
        // 행동
        engine.send(DriveEvent.AdminCodeVerified)
        // 결과
        assertEquals(1, recorded.driverDataErased)
        assertSame(Idle, engine.state.value)
    }

    /** 엔진을 멈추면 그 뒤의 신호가 처리되지 않는지 확인한다. */
    @Test
    fun `엔진을 멈추면 신호가 더 들어오지 않는다`() {
        // 상황
        startSeated()
        engine.stop()
        // 행동
        signals.gear.shift(Gear.D)
        // 결과
        assertSame(ParkedRest, engine.state.value)
    }
}
