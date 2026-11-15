package com.annyeongdrive.app.signaltest

import ai.pleos.playground.vehicle.constant.control.VehicleGear
import com.annyeongdrive.app.guide.GuidanceChannel
import com.annyeongdrive.app.guide.RoadRuleReminder
import com.annyeongdrive.app.location.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 통화 중 안내 신호 시나리오: SDK 기어·차속으로 달리다 규칙 구간을 지나거나 정차할 때, 통화 중이면 말하지 않고 알림으로만 띄운다.
 * 근거: 11/14 사용자 결정 (가)(통화 중에는 음성 대신 알림만), 11/15 에뮬레이터 확인(통화가 남은 채 주행하면 안내 6건이 알림으로만 나옴),
 * 기능 실행안 주행 중 규칙 상기(B11), 11/15 사용자 결정(SDK 값을 임의로 넣어 확인하는 신호 테스트).
 */
class CallGuidanceSignalTest {

    private val rig = SignalRig().startRented()

    /** 달리는 중 보호구역에 들어선 것으로 만든다. */
    private fun passSchoolZone() {
        rig.roadRules.next = listOf(RoadRuleReminder.SchoolZone("Z1", 30))
        rig.location.moveTo(GeoPoint(37.64, 127.04))
    }

    /** 통화 중 주행 규칙 안내는 말하지 않고 주행 안내 알림으로 띄우는지 확인한다. */
    @Test
    fun `통화 중 주행 규칙 안내는 말하지 않고 알림으로 띄운다`() {
        // 상황
        rig.drive(40f)
        rig.inCall = true
        // 행동
        passSchoolZone()
        // 결과
        val message = rig.delivered.single()
        assertFalse(message.speak)
        assertEquals(GuidanceChannel.ROAD_RULE, message.channel)
    }

    /** 통화가 끝나면 다음 주행 규칙 안내는 다시 말하는지 확인한다. */
    @Test
    fun `통화가 끝나면 다음 주행 규칙 안내는 다시 말한다`() {
        // 상황
        rig.drive(40f)
        rig.inCall = true
        passSchoolZone()
        rig.inCall = false
        // 행동
        passSchoolZone()
        // 결과
        assertTrue(rig.delivered.last().speak)
    }

    /** 통화 중 주정차 경고는 말하지 않고 경고 알림만 띄우는지 확인한다. */
    @Test
    fun `통화 중 주정차 경고는 말하지 않고 경고 알림만 띄운다`() {
        // 상황
        rig.enterNoParkingZone("PC5")
        rig.drive(40f)
        rig.inCall = true
        rig.speed.sendKmh(0f)
        // 행동
        rig.gear.select(VehicleGear.GEAR_PARK)
        // 결과
        val warning = rig.parkingWarnings.single()
        assertFalse(warning.speak)
    }
}
