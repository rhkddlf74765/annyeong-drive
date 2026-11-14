package com.annyeongdrive.app.vehicle

import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import com.annyeongdrive.app.gear.Gear
import com.annyeongdrive.app.vehicle.pleos.PleosSignalValues
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 실제 차량 신호 값 옮기기: Pleos Vehicle SDK의 기어·착좌·안전띠 값과 차속(m/s)을 앱 신호 값으로 판단 없이 옮긴다.
 * 모르는 값은 알 수 없음으로 둔다(P·착용·정차로 보지 않는다).
 * 근거: CLAUDE.md 아키텍처(어댑터는 값만 옮긴다, 모르는 기어를 P로 보지 않는다), 도메인 원칙(경고 없음은 착용이 아니다),
 * 11/14 사용자 결정(release 실제 기어·착좌·안전띠·차속 연결, SDK 착좌 값 뒤집기), 11/14 에뮬레이터 확인(앉음 원값 2), 결정 문서 7장.
 */
class PleosSignalMappingTest {

    /** P·R·N·D는 같은 기어로 옮긴다. */
    @Test
    fun `P R N D는 같은 기어로 옮긴다`() {
        // 상황 · 행동
        val gears = listOf(VehicleGear.GEAR_PARK, VehicleGear.GEAR_REVERSE, VehicleGear.GEAR_NEUTRAL, VehicleGear.GEAR_DRIVE)
            .map(PleosSignalValues::gearOf)

        // 결과
        assertEquals(listOf(Gear.P, Gear.R, Gear.N, Gear.D), gears)
    }

    /** 수동 변속 전진 단은 전진(D)으로 본다. */
    @Test
    fun `수동 변속 전진 단은 D로 본다`() {
        // 상황 · 행동
        val gear = PleosSignalValues.gearOf(VehicleGear.GEAR_3)

        // 결과
        assertEquals(Gear.D, gear)
    }

    /** 모르는 기어와 받지 못한 기어는 P가 아니라 알 수 없음이다. */
    @Test
    fun `모르는 기어는 알 수 없음이다`() {
        // 상황 · 행동
        val unknown = PleosSignalValues.gearOf(VehicleGear.GEAR_UNKNOWN)
        val missing = PleosSignalValues.gearOf(null)

        // 결과
        assertEquals(Gear.UNKNOWN, unknown)
        assertEquals(Gear.UNKNOWN, missing)
    }

    /** SDK 착좌 값은 표준 원값과 반대라 뒤집어 읽는다: true는 비어 있음, false는 앉음, 못 받으면 모름. */
    @Test
    fun `SDK 착좌 값은 뒤집어 읽는다`() {
        // 상황 · 행동
        val values = listOf(true, false, null).map(PleosSignalValues::occupancyOf)

        // 결과
        assertEquals(listOf(SeatOccupancy.VACANT, SeatOccupancy.OCCUPIED, SeatOccupancy.UNKNOWN), values)
    }

    /** 안전띠 경고가 없으면 착용이 아니라 경고 없음이다. */
    @Test
    fun `안전띠 경고 없음은 착용이 아니라 경고 없음이다`() {
        // 상황 · 행동
        val values = listOf(true, false, null).map(PleosSignalValues::seatBeltOf)

        // 결과
        assertEquals(listOf(SeatBeltStatus.WARNING, SeatBeltStatus.NO_WARNING, SeatBeltStatus.UNKNOWN), values)
    }

    /** 운전석은 왼쪽 앞자리, 다섯 좌석이 모두 Pleos 좌석 위치를 가진다. */
    @Test
    fun `운전석은 왼쪽 앞자리이고 다섯 좌석 모두 위치가 있다`() {
        // 상황 · 행동
        val areas = PleosSignalValues.SEAT_AREAS

        // 결과
        assertEquals(SeatValue.ROW_1_LEFT, areas[Seat.DRIVER])
        assertEquals(Seat.entries.toSet(), areas.keys)
    }

    /** 차속은 m/s를 km/h로 바꾸고, 후진의 음수 값은 크기만 쓴다. */
    @Test
    fun `차속은 km_h로 바꾸고 후진 음수는 크기만 쓴다`() {
        // 상황 · 행동
        val forward = SpeedUnits.kmhOf(10f)
        val reverse = SpeedUnits.kmhOf(-2.5f)
        val stopped = SpeedUnits.kmhOf(0f)

        // 결과
        assertEquals(36f, forward, 0.001f)
        assertEquals(9f, reverse, 0.001f)
        assertEquals(0f, stopped, 0f)
    }
}
