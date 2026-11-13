package com.annyeongdrive.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.annyeongdrive.app.R
import com.annyeongdrive.app.admin.CodeNotice
import com.annyeongdrive.app.admin.DueAtInput
import com.annyeongdrive.app.admin.VendorSettingsScreen
import com.annyeongdrive.app.admin.formatDueAt
import com.annyeongdrive.app.admin.parseDueAt
import com.annyeongdrive.app.ui.theme.Annyeong_driveTheme
import com.annyeongdrive.app.vehicle.FuelType
import com.annyeongdrive.app.vendor.AdminCodeChangeRequest
import com.annyeongdrive.app.vendor.HiPassAvailability
import com.annyeongdrive.app.vendor.ReturnTerms
import com.annyeongdrive.app.vendor.VehicleInfo
import com.annyeongdrive.app.vendor.VendorConfig
import java.time.Duration
import java.time.ZoneId

/**
 * 업체 설정 화면에서 저장 버튼을 눌렀을 때 넘기는 편집 값 묶음.
 *
 * @property vehicle 차량 정보
 * @property returnTerms 반납 조건
 * @property contact 업체 연락처
 * @property resetGrace 예비 초기화 시간 N
 */
data class VendorSettingsInput(
    val vehicle: VehicleInfo,
    val returnTerms: ReturnTerms,
    val contact: String?,
    val resetGrace: Duration,
)

/**
 * 업체 설정 화면의 사용자 동작 묶음. 화면은 이 동작을 부르기만 하고 판단은 VendorSettingsFlow가 한다.
 *
 * @property onFirstCode 처음 코드 정하기(새 코드, 확인 입력)
 * @property onEnterCode 코드 입력해 들어가기
 * @property onSave 편집 값을 저장하기
 * @property onChangeCode 코드 바꾸기(요청, 새 코드 확인 입력)
 * @property onClose 닫기
 */
data class VendorSettingsActions(
    val onFirstCode: (String, String) -> Unit,
    val onEnterCode: (String) -> Unit,
    val onSave: (VendorSettingsInput) -> Unit,
    val onChangeCode: (AdminCodeChangeRequest, String) -> Unit,
    val onClose: () -> Unit,
)

/**
 * 업체 설정 화면. 상태(VendorSettingsScreen)를 그리기만 한다.
 *
 * @param state 업체 설정 화면 상태
 * @param actions 사용자 동작
 * @param modifier 바깥에서 주는 배치 설정
 */
@Composable
fun VendorSettingsView(state: VendorSettingsScreen, actions: VendorSettingsActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.vendor_title), style = MaterialTheme.typography.titleLarge)
        when (state) {
            VendorSettingsScreen.Closed -> Unit
            VendorSettingsScreen.ConfigUnreadable -> Text(stringResource(R.string.config_unreadable))
            is VendorSettingsScreen.FirstCodeSetup -> FirstCodeSetup(state.notice, actions.onFirstCode)
            is VendorSettingsScreen.CodeEntry -> CodeEntry(state.notice, actions.onEnterCode)
            is VendorSettingsScreen.Editing -> Editing(state, actions)
        }
        OutlinedButton(onClick = actions.onClose) { Text(stringResource(R.string.action_close)) }
    }
}

/**
 * 처음 코드 정하기 단계.
 *
 * @param notice 직전 입력 안내
 * @param onSubmit 새 코드와 확인 입력을 넘긴다
 */
@Composable
private fun FirstCodeSetup(notice: CodeNotice?, onSubmit: (String, String) -> Unit) {
    var code by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    Text(stringResource(R.string.vendor_first_code))
    NoticeText(notice)
    CodeField(code, R.string.new_code_label) { code = it }
    CodeField(confirm, R.string.confirm_code_label) { confirm = it }
    Button(onClick = { onSubmit(code, confirm) }) { Text(stringResource(R.string.action_confirm)) }
}

/**
 * 코드 입력 단계.
 *
 * @param notice 직전 입력 안내
 * @param onSubmit 입력한 코드를 넘긴다
 */
@Composable
private fun CodeEntry(notice: CodeNotice?, onSubmit: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    NoticeText(notice)
    CodeField(code, R.string.admin_code_label) { code = it }
    Button(onClick = { onSubmit(code) }) { Text(stringResource(R.string.action_confirm)) }
}

/**
 * 편집 단계. 글자 입력은 저장 버튼을 누를 때 읽어 넘기고, 형식이 틀리면 저장하지 않는다.
 *
 * @param state 편집 상태
 * @param actions 사용자 동작
 */
@Composable
private fun Editing(state: VendorSettingsScreen.Editing, actions: VendorSettingsActions) {
    val zone = ZoneId.systemDefault()
    val draft = state.draft
    var fuel by remember { mutableStateOf(draft.vehicle.fuelType) }
    var hiPass by remember { mutableStateOf(draft.vehicle.hiPass) }
    var settlement by remember { mutableStateOf(draft.vehicle.hiPassSettlement.orEmpty()) }
    var place by remember { mutableStateOf(draft.returnTerms.place.orEmpty()) }
    var dueAt by remember { mutableStateOf(formatDueAt(draft.returnTerms.dueAt, zone)) }
    var energy by remember { mutableStateOf(draft.returnTerms.energyRule.orEmpty()) }
    var contact by remember { mutableStateOf(draft.contact.orEmpty()) }
    var graceHours by remember { mutableStateOf(draft.resetGrace.toHours().toString()) }
    var inputError by remember { mutableStateOf<Int?>(null) }

    ChoiceRow(R.string.vendor_fuel, FuelType.entries, fuel, ::fuelLabel) { fuel = it }
    ChoiceRow(R.string.vendor_hipass, HiPassAvailability.entries, hiPass, ::hiPassLabel) { hiPass = it }
    TextInput(settlement, R.string.vendor_hipass_settlement) { settlement = it }
    TextInput(place, R.string.vendor_return_place) { place = it }
    TextInput(dueAt, R.string.vendor_due_at) { dueAt = it }
    TextInput(energy, R.string.vendor_energy_rule) { energy = it }
    TextInput(contact, R.string.vendor_contact) { contact = it }
    TextInput(graceHours, R.string.vendor_reset_grace) { graceHours = it }
    inputError?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
    if (state.unsaved) Text(stringResource(R.string.vendor_unsaved))
    Button(onClick = {
        val due = parseDueAt(dueAt, zone)
        val hours = graceHours.trim().toLongOrNull()
        inputError = editingInputError(due, hours)
        if (inputError == null) {
            val dueInstant = (due as? DueAtInput.Valid)?.instant
            actions.onSave(
                VendorSettingsInput(
                    vehicle = VehicleInfo(fuel, hiPass, settlement.ifBlank { null }),
                    returnTerms = ReturnTerms(place.ifBlank { null }, dueInstant, energy.ifBlank { null }),
                    contact = contact.ifBlank { null },
                    resetGrace = Duration.ofHours(hours ?: 0),
                ),
            )
        }
    }) { Text(stringResource(R.string.action_save)) }

    HorizontalDivider()
    CodeChange(state.codeNotice, actions.onChangeCode)
}

/**
 * 편집 입력의 형식 오류를 찾는다.
 *
 * @param due 반납 예정 시각 입력
 * @param hours N 입력(시간)
 * @return 오류 문구 ID. 없으면 null
 */
private fun editingInputError(due: DueAtInput, hours: Long?): Int? = when {
    due == DueAtInput.Invalid -> R.string.due_at_invalid
    hours == null || hours < 1 -> R.string.grace_invalid
    else -> null
}

/**
 * 코드 바꾸기 영역.
 *
 * @param notice 코드 변경 안내
 * @param onChange 요청과 새 코드 확인 입력을 넘긴다
 */
@Composable
private fun CodeChange(notice: CodeNotice?, onChange: (AdminCodeChangeRequest, String) -> Unit) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    Text(stringResource(R.string.vendor_change_code), style = MaterialTheme.typography.titleMedium)
    NoticeText(notice)
    CodeField(current, R.string.current_code_label) { current = it }
    CodeField(next, R.string.new_code_label) { next = it }
    CodeField(confirm, R.string.confirm_code_label) { confirm = it }
    Button(onClick = { onChange(AdminCodeChangeRequest(current, next), confirm) }) {
        Text(stringResource(R.string.vendor_change_code))
    }
}

/**
 * 코드 안내 문구. 안내가 없으면 그리지 않는다.
 *
 * @param notice 코드 안내
 */
@Composable
private fun NoticeText(notice: CodeNotice?) {
    val text = when (notice) {
        null -> return
        CodeNotice.InvalidFormat -> stringResource(R.string.code_invalid)
        CodeNotice.ConfirmMismatch -> stringResource(R.string.code_mismatch)
        is CodeNotice.Wrong -> stringResource(R.string.code_wrong, notice.remainingAttempts)
        is CodeNotice.Locked -> stringResource(R.string.code_locked, lockTimeText(notice.until))
        CodeNotice.Changed -> stringResource(R.string.code_changed)
    }
    Text(text)
}

/**
 * 코드 입력칸. 입력한 글자는 가린다.
 *
 * @param value 입력값
 * @param labelId 이름표 문구 ID
 * @param onChange 입력이 바뀌면
 */
@Composable
private fun CodeField(value: String, labelId: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(labelId)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
    )
}

/**
 * 글자 입력칸.
 *
 * @param value 입력값
 * @param labelId 이름표 문구 ID
 * @param onChange 입력이 바뀌면
 */
@Composable
private fun TextInput(value: String, labelId: Int, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(stringResource(labelId)) })
}

/**
 * 여러 값 중 하나를 고르는 줄.
 *
 * @param labelId 줄 이름 문구 ID
 * @param options 고를 수 있는 값
 * @param selected 고른 값
 * @param label 값의 이름 문구 ID
 * @param onSelect 값을 고르면
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(labelId: Int, options: List<T>, selected: T, label: (T) -> Int, onSelect: (T) -> Unit) {
    Text(stringResource(labelId), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(stringResource(label(option))) })
        }
    }
}

/**
 * 유종 이름 문구 ID를 고른다.
 *
 * @param fuel 유종
 * @return 문구 ID
 */
private fun fuelLabel(fuel: FuelType): Int = when (fuel) {
    FuelType.GASOLINE -> R.string.fuel_gasoline
    FuelType.DIESEL -> R.string.fuel_diesel
    FuelType.LPG -> R.string.fuel_lpg
    FuelType.ELECTRIC -> R.string.fuel_electric
    FuelType.UNKNOWN -> R.string.not_decided
}

/**
 * 하이패스 이용 여부 이름 문구 ID를 고른다.
 *
 * @param hiPass 이용 여부
 * @return 문구 ID
 */
private fun hiPassLabel(hiPass: HiPassAvailability): Int = when (hiPass) {
    HiPassAvailability.UNKNOWN -> R.string.not_decided
    HiPassAvailability.AVAILABLE -> R.string.hipass_available
    HiPassAvailability.NOT_AVAILABLE -> R.string.hipass_not_available
}

/** 업체 설정 편집 화면 미리보기. */
@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun VendorSettingsEditingPreview() {
    Annyeong_driveTheme {
        VendorSettingsView(
            VendorSettingsScreen.Editing(VendorConfig.initial(), unsaved = true, codeNotice = null),
            VendorSettingsActions({ _, _ -> }, {}, {}, { _, _ -> }, {}),
        )
    }
}

/** 업체 설정 코드 입력 화면 미리보기(틀림). */
@Preview(showBackground = true)
@Composable
private fun VendorSettingsCodeEntryPreview() {
    Annyeong_driveTheme {
        VendorSettingsView(VendorSettingsScreen.CodeEntry(CodeNotice.Wrong(2)), VendorSettingsActions({ _, _ -> }, {}, {}, { _, _ -> }, {}))
    }
}
