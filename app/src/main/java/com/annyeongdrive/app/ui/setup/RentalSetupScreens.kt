package com.annyeongdrive.app.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.annyeongdrive.app.R
import com.annyeongdrive.app.rental.AppLanguage
import com.annyeongdrive.app.setup.FuelGuidance
import com.annyeongdrive.app.setup.HiPassGuidance
import com.annyeongdrive.app.setup.PrecheckProgress
import com.annyeongdrive.app.ui.HeadingBeforeLanguageChoice
import com.annyeongdrive.app.setup.PrecheckView
import com.annyeongdrive.app.ui.theme.Annyeong_driveTheme
import com.annyeongdrive.app.ui.theme.PrimaryAction
import com.annyeongdrive.app.ui.theme.ScreenHeading
import com.annyeongdrive.app.ui.theme.ScreenSection
import com.annyeongdrive.app.ui.theme.VehicleScreen
import com.annyeongdrive.app.ui.theme.VehicleTargetHeight
import com.annyeongdrive.app.vehicle.FuelType
import com.annyeongdrive.app.vehicle.Seat
import com.annyeongdrive.app.vendor.ReturnTerms
import java.time.ZoneId

/** 출발 전 점검에서 수동 확인과 점검 마침을 기존 흐름에 전달한다. */
data class PrecheckActions(
    val onConfirmSeatBelts: (Boolean) -> Unit,
    val onComplete: () -> Unit,
)

/** 대여 언어 선택 화면. 상태 머신 T6·T7. */
@Composable
internal fun LanguageScreen(onSelect: (AppLanguage) -> Unit, modifier: Modifier = Modifier) {
    VehicleScreen(modifier) {
        HeadingBeforeLanguageChoice(R.string.setup_language_title)
        LANGUAGE_OPTIONS.forEach { option ->
            PrimaryAction(option.label, onClick = { onSelect(option.language) })
        }
    }
}

/** 출발 전 점검 화면. 수동 안전띠 확인과 점검 마침을 흐름에 넘긴다(T8). */
@Composable
internal fun PrecheckScreen(model: PrecheckScreenModel, actions: PrecheckActions, modifier: Modifier = Modifier) {
    VehicleScreen(modifier) {
        ScreenHeading(stringResource(R.string.precheck_title))
        SeatBeltSection(model, actions.onConfirmSeatBelts)
        ScreenSection {
            Text(stringResource(R.string.precheck_fuel_title), style = MaterialTheme.typography.titleMedium)
            val fuelLabel = model.fuelLabel
            val message = if (fuelLabel == null) stringResource(model.fuelMessage)
                else stringResource(model.fuelMessage, stringResource(fuelLabel))
            Text(message)
        }
        ScreenSection {
            Text(stringResource(R.string.precheck_hipass_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(model.hiPassMessage))
            model.hiPassSettlement?.let { Text(stringResource(R.string.precheck_hipass_settlement, it)) }
        }
        ScreenSection {
            Text(stringResource(R.string.precheck_return_title), style = MaterialTheme.typography.titleMedium)
            val ask = stringResource(R.string.ask_vendor_short)
            Text(stringResource(R.string.precheck_return_place, model.returnPlace ?: ask))
            Text(stringResource(R.string.precheck_return_due, model.returnDue ?: ask))
            Text(stringResource(R.string.precheck_return_energy, model.returnEnergy ?: ask))
        }
        PrimaryAction(R.string.precheck_done, actions.onComplete)
    }
}

/** 경고 좌석과 별도로 운전자의 수동 확인 체크를 보여 준다. */
@Composable
private fun SeatBeltSection(model: PrecheckScreenModel, onConfirm: (Boolean) -> Unit) {
    ScreenSection {
        Text(stringResource(R.string.precheck_seatbelt_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.precheck_seatbelt_body))
        if (model.warningSeatLabels.isNotEmpty()) {
            val seats = model.warningSeatLabels.map { stringResource(it) }.joinToString(", ")
            Text(stringResource(R.string.precheck_seatbelt_warning, seats), color = MaterialTheme.colorScheme.error)
        }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = VehicleTargetHeight).toggleable(
                value = model.seatBeltsConfirmed,
                role = Role.Checkbox,
                onValueChange = onConfirm,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = model.seatBeltsConfirmed, onCheckedChange = null)
            Text(stringResource(R.string.precheck_seatbelt_confirm))
        }
        if (model.showConfirmationPrompt) {
            Text(stringResource(R.string.precheck_seatbelt_needed), color = MaterialTheme.colorScheme.error)
        }
    }
}

/** 언어 선택 화면 미리보기. */
@Preview(showBackground = true)
@Composable
private fun LanguageScreenPreview() {
    Annyeong_driveTheme { LanguageScreen(onSelect = {}) }
}

/** 출발 전 점검 화면 미리보기. 조수석 경고와 수동 확인 안내가 있는 경우. */
@Preview(showBackground = true, heightDp = 900)
@Composable
private fun PrecheckScreenPreview() {
    val view = PrecheckView(
        warningSeats = listOf(Seat.FRONT_PASSENGER),
        fuel = FuelGuidance.Fuel(FuelType.DIESEL),
        hiPass = HiPassGuidance.AskVendor,
        returnTerms = ReturnTerms(place = "제주공항 렌터카 하우스"),
    )
    Annyeong_driveTheme {
        PrecheckScreen(
            model = PrecheckScreenModel.of(
                view, PrecheckProgress(needsSeatBeltConfirmation = true), ZoneId.systemDefault(),
            ),
            actions = PrecheckActions(onConfirmSeatBelts = {}, onComplete = {}),
        )
    }
}
