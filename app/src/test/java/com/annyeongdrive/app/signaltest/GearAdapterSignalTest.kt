package com.annyeongdrive.app.signaltest

import ai.pleos.playground.vehicle.constant.control.VehicleGear
import com.annyeongdrive.app.gear.CarGearSource
import com.annyeongdrive.app.gear.Gear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 기어 어댑터: 가짜 Pleos SDK가 보낸 기어 값이 앱 기어로 바뀌어 엔진 쪽으로 나간다.
 * 시작 시 알 수 없음 먼저, 모르는 값은 P가 아니라 알 수 없음, 멈춘 뒤에는 알리지 않는다.
 * 근거: CLAUDE.md 아키텍처(어댑터는 값만 옮긴다, 모르는 기어를 P로 보지 않는다), vehicle/SignalSource 계약,
 * 11/15 사용자 결정(SDK 값을 임의로 넣어 확인하는 신호 테스트).
 */
class GearAdapterSignalTest {

    private val sdk = FakePleosGear()
    private val log = RecordingSignalLog()
    private val received = mutableListOf<Gear>()

    /**
     * 바로 실행하는 스레드 창구로 어댑터를 만들고 구독을 시작한다.
     *
     * @return 구독 중인 어댑터
     */
    private fun started(): CarGearSource {
        val source = CarGearSource({ sdk }, IMMEDIATE, log)
        source.start { received += it }
        return source
    }

    /** 시작하면 알 수 없음을 먼저 알리고 SDK 현재 기어를 알리는지 확인한다. */
    @Test
    fun `시작하면 알 수 없음을 먼저 알리고 SDK 현재 기어를 알린다`() {
        // 상황
        sdk.current = VehicleGear.GEAR_DRIVE
        // 행동
        started()
        // 결과
        assertEquals(listOf(Gear.UNKNOWN, Gear.D), received)
    }

    /** SDK 기어 콜백 값을 차례로 같은 기어로 옮기고, 수동 변속 전진 단은 D로 보는지 확인한다. */
    @Test
    fun `SDK 기어 콜백 값을 차례로 옮기고 수동 전진 단은 D로 본다`() {
        // 상황
        started()
        received.clear()
        // 행동
        listOf(VehicleGear.GEAR_REVERSE, VehicleGear.GEAR_NEUTRAL, VehicleGear.GEAR_DRIVE, VehicleGear.GEAR_3, VehicleGear.GEAR_PARK)
            .forEach(sdk::select)
        // 결과
        assertEquals(listOf(Gear.R, Gear.N, Gear.D, Gear.D, Gear.P), received)
    }

    /** SDK가 모르는 기어나 빈 값을 주면 P가 아니라 알 수 없음으로 옮기는지 확인한다. */
    @Test
    fun `SDK가 모르는 기어나 빈 값을 주면 P가 아니라 알 수 없음이다`() {
        // 상황
        started()
        received.clear()
        // 행동
        sdk.select(VehicleGear.GEAR_UNKNOWN)
        sdk.select(null)
        // 결과
        assertEquals(listOf(Gear.UNKNOWN, Gear.UNKNOWN), received)
    }

    /** SDK 구독 오류가 나면 알 수 없음을 알리고 오류를 남기는지 확인한다. */
    @Test
    fun `SDK 구독 오류가 나면 알 수 없음을 알리고 오류를 남긴다`() {
        // 상황
        sdk.current = VehicleGear.GEAR_DRIVE
        started()
        received.clear()
        // 행동
        sdk.fail(IllegalStateException("vehicle service died"))
        // 결과
        assertEquals(listOf(Gear.UNKNOWN), received)
        assertEquals(1, log.messages.size)
    }

    /** SDK 현재 기어 읽기에 실패해도 구독은 이어져 다음 기어를 받는지 확인한다. */
    @Test
    fun `SDK 현재 기어 읽기에 실패해도 구독은 이어진다`() {
        // 상황
        sdk.readFailure = IllegalStateException("not ready")
        started()
        // 행동
        sdk.select(VehicleGear.GEAR_DRIVE)
        // 결과
        assertEquals(listOf(Gear.UNKNOWN, Gear.D), received)
        assertEquals(1, log.messages.size)
    }

    /** 멈추면 SDK 구독을 놓고, 그 뒤 SDK가 값을 보내도 알리지 않는지 확인한다. */
    @Test
    fun `멈추면 SDK 구독을 놓고 이후 값은 알리지 않는다`() {
        // 상황
        val source = started()
        received.clear()
        // 행동
        source.stop()
        sdk.select(VehicleGear.GEAR_DRIVE)
        // 결과
        assertEquals(0, sdk.subscribers)
        assertTrue(received.isEmpty())
    }

    /** SDK 콜백이 메인 스레드로 옮겨지는 사이 구독을 멈추면 늦게 도착한 기어는 알리지 않는지 확인한다. */
    @Test
    fun `메인 스레드로 옮기는 사이 멈추면 늦게 도착한 기어는 알리지 않는다`() {
        // 상황
        val main = QueuedMainThread()
        val source = CarGearSource({ sdk }, main, log)
        source.start { received += it }
        main.runAll()
        received.clear()
        sdk.select(VehicleGear.GEAR_DRIVE)
        // 행동
        source.stop()
        main.runAll()
        // 결과
        assertTrue(received.isEmpty())
    }
}
