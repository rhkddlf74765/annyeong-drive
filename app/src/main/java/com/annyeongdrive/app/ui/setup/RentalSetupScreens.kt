package com.annyeongdrive.app.ui.setup

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.annyeongdrive.app.R
import com.annyeongdrive.app.admin.formatDueAt
import com.annyeongdrive.app.rental.AppLanguage
import com.annyeongdrive.app.setup.FuelGuidance
import com.annyeongdrive.app.setup.HiPassGuidance
import com.annyeongdrive.app.setup.PrecheckProgress
import com.annyeongdrive.app.setup.PrecheckView
import com.annyeongdrive.app.setup.SELECTABLE_LANGUAGES
import com.annyeongdrive.app.ui.admin.fuelLabel
import com.annyeongdrive.app.ui.theme.Annyeong_driveTheme
import com.annyeongdrive.app.vehicle.FuelType
import com.annyeongdrive.app.vehicle.Seat
import com.annyeongdrive.app.vendor.ReturnTerms
import java.time.ZoneId

/**
 * 출발 전 점검 화면의 사용자 동작 묶음.
 *
 * @property onConfirmSeatBelts 안전띠 수동 확인 체크를 바꿨을 때
 * @property onComplete 점검 마침을 눌렀을 때
 */
data class PrecheckActions(
    val onConfirmSeatBelts: (Boolean) -> Unit,
    val onComplete: () -> Unit,
)

/**
 * 언어 선택 화면. 상태 머신: T6에서 들어오고 T7로 나간다.
 *
 * @param onSelect 언어를 골랐을 때
 * @param modifier 바깥에서 주는 배치 설정
 */
@Composable
fun LanguageScreen(onSelect: (AppLanguage) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.setup_language_title), style = MaterialTheme.typography.titleLarge)
        SELECTABLE_LANGUAGES.forEach { language ->
            Button(onClick = { onSelect(language) }) { Text(stringResource(languageLabel(language))) }
        }
    }
}

/**
 * 출발 전 점검 화면. 안전띠만 수동 확인이 필요하고 나머지는 보여 주기만 한다. 상태 머신: T8로 나간다.
 *
 * @param view 점검 화면 내용
 * @param progress 화면 안쪽 상태(안전띠 체크)
 * @param actions 사용자 동작
 * @param modifier 바깥에서 주는 배치 설정
 */
@Composable
fun PrecheckScreen(
    view: PrecheckView,
    progress: PrecheckProgress,
    actions: PrecheckActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.precheck_title), style = MaterialTheme.typography.titleLarge)
        SeatBeltSection(view.warningSeats, progress, actions.onConfirmSeatBelts)
        FuelSection(view.fuel)
        HiPassSection(view.hiPass)
        ReturnTermsSection(view.returnTerms)
        Button(onClick = actions.onComplete) { Text(stringResource(R.string.precheck_done)) }
    }
}

/**
 * 안전띠 항목. 경고가 있는 좌석만 더 짚는다. 경고가 없다고 "모두 착용"을 보여 주지 않는다.
 * 확인 체크는 체크 상자와 문구를 한 줄로 묶어, 문구를 눌러도 바뀐다.
 *
 * @param warningSeats 경고가 있는 좌석
 * @param progress 화면 안쪽 상태
 * @param onConfirm 수동 확인 체크를 바꿨을 때
 */
@Composable
private fun SeatBeltSection(warningSeats: List<Seat>, progress: PrecheckProgress, onConfirm: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.precheck_seatbelt_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.precheck_seatbelt_body))
        if (warningSeats.isNotEmpty()) {
            val seats = warningSeats.map { stringResource(seatLabel(it)) }.joinToString(", ")
            Text(stringResource(R.string.precheck_seatbelt_warning, seats), color = MaterialTheme.colorScheme.error)
        }
        Row(
            modifier = Modifier.toggleable(
                value = progress.seatBeltConfirmed,
                role = Role.Checkbox,
                onValueChange = onConfirm,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = progress.seatBeltConfirmed, onCheckedChange = null)
            Text(stringResource(R.string.precheck_seatbelt_confirm))
        }
        if (progress.needsSeatBeltConfirmation) {
            Text(stringResource(R.string.precheck_seatbelt_needed), color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * 연료 항목. 업체 설정 유종만 쓴다.
 *
 * @param fuel 유종 안내
 */
@Composable
private fun FuelSection(fuel: FuelGuidance) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.precheck_fuel_title), style = MaterialTheme.typography.titleMedium)
        when (fuel) {
            is FuelGuidance.Fuel -> {
                val name = stringResource(fuelLabel(fuel.fuelType))
                Text(stringResource(R.string.precheck_fuel_body, name))
            }
            FuelGuidance.Charge -> Text(stringResource(R.string.precheck_fuel_charge))
            FuelGuidance.AskVendor -> Text(stringResource(R.string.precheck_fuel_ask))
        }
    }
}

/**
 * 하이패스 항목. 업체가 정하지 않았으면 업체에 물어보라고만 안내한다.
 *
 * @param hiPass 하이패스 안내
 */
@Composable
private fun HiPassSection(hiPass: HiPassGuidance) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.precheck_hipass_title), style = MaterialTheme.typography.titleMedium)
        when (hiPass) {
            is HiPassGuidance.Available -> {
                Text(stringResource(R.string.precheck_hipass_available))
                hiPass.settlement?.let { Text(stringResource(R.string.precheck_hipass_settlement, it)) }
            }
            HiPassGuidance.NotAvailable -> Text(stringResource(R.string.precheck_hipass_not_available))
            HiPassGuidance.AskVendor -> Text(stringResource(R.string.precheck_hipass_ask))
        }
    }
}

/**
 * 반납 조건 항목. 업체가 정하지 않은 항목은 업체에 확인하라고 보여 준다.
 *
 * @param terms 반납 조건
 */
@Composable
private fun ReturnTermsSection(terms: ReturnTerms) {
    val zone = remember { ZoneId.systemDefault() }
    val ask = stringResource(R.string.ask_vendor_short)
    val place = terms.place ?: ask
    val energy = terms.energyRule ?: ask
    val dueText = formatDueAt(terms.dueAt, zone)
    val due = dueText.ifEmpty { ask }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.precheck_return_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.precheck_return_place, place))
        Text(stringResource(R.string.precheck_return_due, due))
        Text(stringResource(R.string.precheck_return_energy, energy))
    }
}

/**
 * 언어 이름 문구 ID를 고른다.
 *
 * @param language 언어
 * @return 문구 ID
 */
@StringRes
private fun languageLabel(language: AppLanguage): Int = when (language) {
    AppLanguage.KOREAN -> R.string.language_korean
    AppLanguage.ENGLISH -> R.string.language_english
    AppLanguage.JAPANESE -> R.string.language_japanese
    AppLanguage.SPANISH -> R.string.language_spanish
}

/**
 * 좌석 이름 문구 ID를 고른다.
 *
 * @param seat 좌석
 * @return 문구 ID
 */
@StringRes
private fun seatLabel(seat: Seat): Int = when (seat) {
    Seat.DRIVER -> R.string.seat_driver
    Seat.FRONT_PASSENGER -> R.string.seat_front_passenger
    Seat.REAR_LEFT -> R.string.seat_rear_left
    Seat.REAR_CENTER -> R.string.seat_rear_center
    Seat.REAR_RIGHT -> R.string.seat_rear_right
}

/** 언어 선택 화면 미리보기. */
@Preview(showBackground = true)
@Composable
private fun LanguageScreenPreview() {
    Annyeong_driveTheme {
        LanguageScreen(onSelect = {})
    }
}

/** 출발 전 점검 화면 미리보기. 조수석 경고가 있고 업체가 하이패스를 정하지 않은 경우. */
@Preview(showBackground = true, heightDp = 900)
@Composable
private fun PrecheckScreenPreview() {
    Annyeong_driveTheme {
        PrecheckScreen(
            view = PrecheckView(
                warningSeats = listOf(Seat.FRONT_PASSENGER),
                fuel = FuelGuidance.Fuel(FuelType.DIESEL),
                hiPass = HiPassGuidance.AskVendor,
                returnTerms = ReturnTerms(place = "제주공항 렌터카 하우스"),
            ),
            progress = PrecheckProgress(needsSeatBeltConfirmation = true),
            actions = PrecheckActions(onConfirmSeatBelts = {}, onComplete = {}),
        )
    }
}
