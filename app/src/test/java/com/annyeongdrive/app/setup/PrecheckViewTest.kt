package com.annyeongdrive.app.setup

import com.annyeongdrive.app.vehicle.FuelType
import com.annyeongdrive.app.vehicle.Seat
import com.annyeongdrive.app.vehicle.SeatBeltReadings
import com.annyeongdrive.app.vehicle.SeatBeltStatus
import com.annyeongdrive.app.vendor.HiPassAvailability
import com.annyeongdrive.app.vendor.VehicleInfo
import com.annyeongdrive.app.vendor.VendorConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 출발 전 점검 화면 내용: 안전띠 경고 좌석, 유종, 하이패스, 반납 조건. 판단 없이 업체 설정과 차량 신호를 그대로 안내로 바꾼다.
 * 근거: 기능 실행안 2번, 결정 문서 7장(안전띠 경고 없음 = 알 수 없음, 하이패스 단말기 ≠ 이용 가능), implementation-plan 5단계.
 */
class PrecheckViewTest {

    /**
     * 좌석 신호 묶음을 만든다. 적지 않은 좌석은 알 수 없음이다.
     *
     * @param pairs 좌석별 신호
     * @return 신호 묶음
     */
    private fun readings(vararg pairs: Pair<Seat, SeatBeltStatus>): SeatBeltReadings =
        SeatBeltReadings(SeatBeltReadings.UNKNOWN.statuses + pairs)

    /**
     * 차량 정보만 정한 업체 설정을 만든다.
     *
     * @param vehicle 차량 정보
     * @return 업체 설정
     */
    private fun vendor(vehicle: VehicleInfo): VendorConfig = VendorConfig.initial().withVehicle(vehicle)

    /** 모든 좌석이 "경고 없음"이어도 경고 좌석이 비어 있을 뿐 착용으로 바뀌는 값은 없다. */
    @Test
    fun `모든 좌석이 경고 없음이어도 모두 착용으로 보지 않고 경고 좌석만 비어 있다`() {
        // 상황
        val allNoWarning = SeatBeltReadings(Seat.entries.associateWith { SeatBeltStatus.NO_WARNING })

        // 행동
        val view = PrecheckView.of(allNoWarning, VendorConfig.initial())

        // 결과
        assertTrue(view.warningSeats.isEmpty())
    }

    /** 경고가 있는 좌석만 좌석 순서대로 더 짚는다. */
    @Test
    fun `경고가 있는 좌석만 좌석 순서대로 안내한다`() {
        // 상황
        val seats = readings(
            Seat.REAR_RIGHT to SeatBeltStatus.WARNING,
            Seat.DRIVER to SeatBeltStatus.NO_WARNING,
            Seat.FRONT_PASSENGER to SeatBeltStatus.WARNING,
        )

        // 행동
        val view = PrecheckView.of(seats, VendorConfig.initial())

        // 결과
        assertEquals(listOf(Seat.FRONT_PASSENGER, Seat.REAR_RIGHT), view.warningSeats)
    }

    /** 업체가 하이패스를 정하지 않았으면 이용 가능이라고 하지 않고 업체에 물어보라고 안내한다. */
    @Test
    fun `업체가 하이패스를 정하지 않았으면 업체에 물어보라고 안내한다`() {
        // 상황
        val config = vendor(VehicleInfo(hiPass = HiPassAvailability.UNKNOWN))

        // 행동
        val view = PrecheckView.of(SeatBeltReadings.UNKNOWN, config)

        // 결과
        assertSame(HiPassGuidance.AskVendor, view.hiPass)
    }

    /** 업체가 이용 가능으로 정했을 때만 정산 방식과 함께 이용 가능으로 안내한다. */
    @Test
    fun `업체가 하이패스 이용 가능으로 정했으면 정산 방식과 함께 안내한다`() {
        // 상황
        val config = vendor(VehicleInfo(hiPass = HiPassAvailability.AVAILABLE, hiPassSettlement = "반납 때 정산"))

        // 행동
        val view = PrecheckView.of(SeatBeltReadings.UNKNOWN, config)

        // 결과
        assertEquals(HiPassGuidance.Available("반납 때 정산"), view.hiPass)
    }

    /** 전기차면 주유 대신 충전 확인을 안내한다. */
    @Test
    fun `전기차면 충전 방식 확인을 안내한다`() {
        // 상황
        val config = vendor(VehicleInfo(fuelType = FuelType.ELECTRIC))

        // 행동
        val view = PrecheckView.of(SeatBeltReadings.UNKNOWN, config)

        // 결과
        assertSame(FuelGuidance.Charge, view.fuel)
    }

    /** 업체가 유종을 정하지 않았으면 추측하지 않고 업체에 확인하라고 안내한다. */
    @Test
    fun `업체가 유종을 정하지 않았으면 업체에 확인하라고 안내한다`() {
        // 상황
        val config = VendorConfig.initial()

        // 행동
        val view = PrecheckView.of(SeatBeltReadings.UNKNOWN, config)

        // 결과
        assertSame(FuelGuidance.AskVendor, view.fuel)
    }

    /** 업체가 정한 연료는 그대로 안내한다. */
    @Test
    fun `업체가 정한 연료를 그대로 안내한다`() {
        // 상황
        val config = vendor(VehicleInfo(fuelType = FuelType.DIESEL))

        // 행동
        val view = PrecheckView.of(SeatBeltReadings.UNKNOWN, config)

        // 결과
        assertEquals(FuelGuidance.Fuel(FuelType.DIESEL), view.fuel)
    }
}
