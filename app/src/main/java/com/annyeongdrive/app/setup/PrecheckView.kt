package com.annyeongdrive.app.setup

import com.annyeongdrive.app.vehicle.FuelType
import com.annyeongdrive.app.vehicle.Seat
import com.annyeongdrive.app.vehicle.SeatBeltReadings
import com.annyeongdrive.app.vendor.HiPassAvailability
import com.annyeongdrive.app.vendor.ReturnTerms
import com.annyeongdrive.app.vendor.VendorConfig

/** 유종 항목에서 보여 줄 안내. */
sealed interface FuelGuidance {

    /**
     * 업체가 정한 연료를 알려 준다.
     *
     * @property fuelType 업체가 정한 연료(전기 제외)
     */
    data class Fuel(val fuelType: FuelType) : FuelGuidance

    /** 전기차라 충전 방식과 충전구 위치를 확인하라고 안내한다. */
    data object Charge : FuelGuidance

    /** 업체가 정하지 않아 주입구 표시를 보고 업체에 확인하라고 안내한다. */
    data object AskVendor : FuelGuidance
}

/** 하이패스 항목에서 보여 줄 안내. 단말기가 있다는 것을 이용 가능으로 보지 않는다. */
sealed interface HiPassGuidance {

    /**
     * 업체가 이용 가능으로 정했다.
     *
     * @property settlement 정산 방식 안내. 없으면 null
     */
    data class Available(val settlement: String?) : HiPassGuidance

    /** 업체가 이용 불가로 정했다. */
    data object NotAvailable : HiPassGuidance

    /** 업체가 정하지 않아 이용 가능 여부와 정산 방식을 업체에 물어보라고 안내한다. */
    data object AskVendor : HiPassGuidance
}

/**
 * 출발 전 점검 화면에 보여 줄 내용. 안전띠만 수동 확인이 필요하고 나머지는 보여 주기만 한다(11/13 결정).
 * 안전띠는 경고가 있는 좌석만 더 짚고, "모두 착용" 같은 표시는 만들 수 없게 구성했다.
 *
 * @property warningSeats 미착용 경고가 있는 좌석
 * @property fuel 유종 안내
 * @property hiPass 하이패스 안내
 * @property returnTerms 반납 조건(빈 항목은 업체에 확인하라고 안내)
 */
data class PrecheckView(
    val warningSeats: List<Seat>,
    val fuel: FuelGuidance,
    val hiPass: HiPassGuidance,
    val returnTerms: ReturnTerms,
) {

    companion object {
        /**
         * 안전띠 신호와 업체 설정으로 점검 화면 내용을 만든다.
         *
         * @param seatBelts 좌석별 안전띠 신호
         * @param vendor 업체 설정
         * @return 점검 화면 내용
         */
        fun of(seatBelts: SeatBeltReadings, vendor: VendorConfig): PrecheckView = PrecheckView(
            warningSeats = seatBelts.warningSeats(),
            fuel = fuelGuidance(vendor.vehicle.fuelType),
            hiPass = hiPassGuidance(vendor.vehicle.hiPass, vendor.vehicle.hiPassSettlement),
            returnTerms = vendor.returnTerms,
        )

        /**
         * 유종을 안내로 바꾼다.
         *
         * @param fuelType 업체 설정 유종
         * @return 안내
         */
        private fun fuelGuidance(fuelType: FuelType): FuelGuidance = when (fuelType) {
            FuelType.ELECTRIC -> FuelGuidance.Charge
            FuelType.UNKNOWN -> FuelGuidance.AskVendor
            FuelType.GASOLINE, FuelType.DIESEL, FuelType.LPG -> FuelGuidance.Fuel(fuelType)
        }

        /**
         * 하이패스 이용 여부를 안내로 바꾼다.
         *
         * @param availability 업체가 정한 이용 여부
         * @param settlement 정산 방식 안내
         * @return 안내
         */
        private fun hiPassGuidance(availability: HiPassAvailability, settlement: String?): HiPassGuidance =
            when (availability) {
                HiPassAvailability.AVAILABLE -> HiPassGuidance.Available(settlement)
                HiPassAvailability.NOT_AVAILABLE -> HiPassGuidance.NotAvailable
                HiPassAvailability.UNKNOWN -> HiPassGuidance.AskVendor
            }
    }
}
