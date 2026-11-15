package com.annyeongdrive.app.ui.setup

import com.annyeongdrive.app.R
import com.annyeongdrive.app.admin.formatDueAt
import com.annyeongdrive.app.rental.AppLanguage
import com.annyeongdrive.app.setup.FuelGuidance
import com.annyeongdrive.app.setup.HiPassGuidance
import com.annyeongdrive.app.setup.PrecheckProgress
import com.annyeongdrive.app.setup.PrecheckView
import com.annyeongdrive.app.setup.SELECTABLE_LANGUAGES
import com.annyeongdrive.app.ui.admin.fuelLabel
import com.annyeongdrive.app.vehicle.Seat
import java.time.ZoneId

/** 언어 버튼에 표시할 문구 ID와 선택 시 넘길 언어. */
internal data class LanguageOption(val language: AppLanguage, val label: Int)

/** 대여 설정에서 제공하는 언어와 화면 문구를 같은 순서로 묶는다. */
internal val LANGUAGE_OPTIONS = SELECTABLE_LANGUAGES.map { LanguageOption(it, languageLabel(it)) }

/** 언어 이름의 문구 ID를 반환한다. */
private fun languageLabel(language: AppLanguage): Int = when (language) {
    AppLanguage.KOREAN -> R.string.language_korean
    AppLanguage.ENGLISH -> R.string.language_english
    AppLanguage.JAPANESE -> R.string.language_japanese
    AppLanguage.SPANISH -> R.string.language_spanish
}

/** 좌석 이름의 문구 ID를 반환한다. */
private fun seatLabel(seat: Seat): Int = when (seat) {
    Seat.DRIVER -> R.string.seat_driver
    Seat.FRONT_PASSENGER -> R.string.seat_front_passenger
    Seat.REAR_LEFT -> R.string.seat_rear_left
    Seat.REAR_CENTER -> R.string.seat_rear_center
    Seat.REAR_RIGHT -> R.string.seat_rear_right
}

/** 출발 전 점검에서 표시할 안전띠 경고, 수동 확인과 업체 설정. */
internal data class PrecheckScreenModel(
    val warningSeatLabels: List<Int>,
    val seatBeltsConfirmed: Boolean,
    val showConfirmationPrompt: Boolean,
    val fuelMessage: Int,
    val fuelLabel: Int?,
    val hiPassMessage: Int,
    val hiPassSettlement: String?,
    val returnPlace: String?,
    val returnDue: String?,
    val returnEnergy: String?,
) {
    companion object {
        /** 점검 흐름이 제공한 안내와 확인 상태를 화면 문구 ID 및 표시 값으로 바꾼다. */
        fun of(view: PrecheckView, progress: PrecheckProgress, zone: ZoneId): PrecheckScreenModel {
            val fuel = view.fuel
            val hiPass = view.hiPass
            val fuelMessage = when (fuel) {
                is FuelGuidance.Fuel -> R.string.precheck_fuel_body
                FuelGuidance.Charge -> R.string.precheck_fuel_charge
                FuelGuidance.AskVendor -> R.string.precheck_fuel_ask
            }
            val hiPassMessage = when (hiPass) {
                is HiPassGuidance.Available -> R.string.precheck_hipass_available
                HiPassGuidance.NotAvailable -> R.string.precheck_hipass_not_available
                HiPassGuidance.AskVendor -> R.string.precheck_hipass_ask
            }
            return PrecheckScreenModel(
                warningSeatLabels = view.warningSeats.map(::seatLabel),
                seatBeltsConfirmed = progress.seatBeltConfirmed,
                showConfirmationPrompt = progress.needsSeatBeltConfirmation,
                fuelMessage = fuelMessage,
                fuelLabel = (fuel as? FuelGuidance.Fuel)?.let { fuelLabel(it.fuelType) },
                hiPassMessage = hiPassMessage,
                hiPassSettlement = (hiPass as? HiPassGuidance.Available)?.settlement,
                returnPlace = view.returnTerms.place,
                returnDue = formatDueAt(view.returnTerms.dueAt, zone).ifEmpty { null },
                returnEnergy = view.returnTerms.energyRule,
            )
        }
    }
}
