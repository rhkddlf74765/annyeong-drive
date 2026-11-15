package com.annyeongdrive.app.signaltest

import ai.pleos.playground.vehicle.constant.control.VehicleGear
import com.annyeongdrive.app.drive.state.DrivingCruising
import com.annyeongdrive.app.drive.state.ParkedNoParkingWarning
import com.annyeongdrive.app.drive.state.ParkedRest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 정차 순간 신호 시나리오: 주정차 경고 구역에서 SDK가 보낸 기어·차속 순서에 따라 경고가 몇 번, 언제 나오는지 확인한다.
 * 실제 에뮬레이터에서 본 신호열(콘솔이 0을 한 번 보낸 뒤 감속을 이어 감)도 그대로 넣는다.
 * 근거: 상태 머신 T13과 가드 해석 보완(2026-11-14, P로 바꾼 순간에만), 기능 보드 "주정차 경고",
 * 11/15 에뮬레이터 확인(차속 31 → 0 → P → 30 → 0), 11/15 사용자 결정(SDK 값을 임의로 넣어 확인하는 신호 테스트).
 */
class ParkingStopSignalTest {

    private val rig = SignalRig().startRented()

    /** 구역 안에서 40km/h로 달리는 중으로 만든다. */
    private fun drivingInZone() {
        rig.enterNoParkingZone("PC5")
        rig.drive(40f)
    }

    /** 차속이 0이 된 뒤 P를 넣으면 주정차 경고를 한 번 내고 경고 화면 상태로 가는지 확인한다. */
    @Test
    fun `차속이 0이 된 뒤 P를 넣으면 경고를 한 번 낸다`() {
        // 상황
        drivingInZone()
        rig.speed.sendKmh(0f)
        // 행동
        rig.gear.select(VehicleGear.GEAR_PARK)
        // 결과
        assertEquals(1, rig.parkingWarnings.size)
        assertSame(ParkedNoParkingWarning, rig.state)
    }

    /** 감속 중에 P를 넣으면 차속이 0이 되는 순간 한 번 경고하는지 확인한다. */
    @Test
    fun `감속 중에 P를 넣으면 차속이 0이 되는 순간 한 번 경고한다`() {
        // 상황
        drivingInZone()
        rig.speed.sendKmh(15f)
        rig.gear.select(VehicleGear.GEAR_PARK)
        val beforeStop = rig.parkingWarnings.size
        // 행동
        listOf(10f, 5f, 0f).forEach(rig.speed::sendKmh)
        // 결과
        assertEquals(0, beforeStop)
        assertEquals(1, rig.parkingWarnings.size)
        assertSame(ParkedNoParkingWarning, rig.state)
    }

    /** 콘솔처럼 차속이 0으로 한 번 튄 뒤 감속을 이어 가면, 경고는 한 번이고 다시 움직일 때 경고 화면이 닫히며 마지막 정차는 일반 정차인지 확인한다. */
    @Test
    fun `차속이 0으로 튄 뒤 감속을 이어 가면 경고는 한 번이고 마지막 정차는 일반 정차다`() {
        // 상황
        drivingInZone()
        rig.speed.sendKmh(31f)
        // 행동
        rig.speed.sendKmh(0f)
        rig.gear.select(VehicleGear.GEAR_PARK)
        val afterPark = rig.state
        rig.speed.sendKmh(30f)
        val afterBounce = rig.state
        listOf(20f, 10f, 0f).forEach(rig.speed::sendKmh)
        // 결과
        assertSame(ParkedNoParkingWarning, afterPark)
        assertSame(DrivingCruising, afterBounce)
        assertEquals(1, rig.parkingWarnings.size)
        assertSame(ParkedRest, rig.state)
    }

    /** 같은 자리에서 P → D → P로 다시 넣으면 다시 경고하고, 두 번째는 한 문장인지 확인한다. */
    @Test
    fun `같은 자리에서 P D P로 다시 넣으면 다시 경고하고 두 번째는 한 문장이다`() {
        // 상황
        drivingInZone()
        rig.speed.sendKmh(0f)
        rig.gear.select(VehicleGear.GEAR_PARK)
        // 행동
        rig.gear.select(VehicleGear.GEAR_DRIVE)
        rig.gear.select(VehicleGear.GEAR_PARK)
        // 결과
        assertEquals(listOf(2, 1), rig.parkingWarnings.map { it.sentences.size })
    }

    /** 구역 밖에서 정차하면 경고하지 않는지 확인한다. */
    @Test
    fun `구역 밖에서 정차하면 경고하지 않는다`() {
        // 상황
        rig.drive(40f)
        rig.speed.sendKmh(0f)
        // 행동
        rig.gear.select(VehicleGear.GEAR_PARK)
        // 결과
        assertTrue(rig.parkingWarnings.isEmpty())
        assertSame(ParkedRest, rig.state)
    }
}
