package com.annyeongdrive.app.admin

import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.drive.state.ParkedVendorSettings
import com.annyeongdrive.app.service.DriveEngine
import com.annyeongdrive.app.storage.VendorConfigRepository
import com.annyeongdrive.app.vendor.AdminCodeChange
import com.annyeongdrive.app.vendor.AdminCodeChangeRequest
import com.annyeongdrive.app.vendor.AdminCodeCheck
import com.annyeongdrive.app.vendor.AdminCredential
import com.annyeongdrive.app.vendor.ReturnTerms
import com.annyeongdrive.app.vendor.VehicleInfo
import com.annyeongdrive.app.vendor.VendorConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import java.time.Instant

/** 업체 설정 화면에서 코드를 다룰 때 보여 줄 안내. */
sealed interface CodeNotice {

    /** 코드 형식이 틀렸다(영문·숫자 8자리가 아님). */
    data object InvalidFormat : CodeNotice

    /** 새 코드와 확인 입력이 다르다. */
    data object ConfirmMismatch : CodeNotice

    /**
     * 코드가 틀렸다.
     *
     * @property remainingAttempts 잠기기 전까지 남은 횟수
     */
    data class Wrong(val remainingAttempts: Int) : CodeNotice

    /**
     * 잠겨 있다.
     *
     * @property until 잠금이 풀리는 시각
     */
    data class Locked(val until: Instant) : CodeNotice

    /** 코드를 바꿨다. */
    data object Changed : CodeNotice
}

/** 업체 설정 화면이 보여 줄 것. 화면 안쪽 단계이며 운전 상태(상태 머신)와는 따로 관리한다. */
sealed interface VendorSettingsScreen {

    /** 업체 설정이 닫혀 있다. */
    data object Closed : VendorSettingsScreen

    /** 업체 설정 파일을 읽지 못해 진입을 막았다. 업체에 문의하라고 안내한다. */
    data object ConfigUnreadable : VendorSettingsScreen

    /**
     * 처음 설치라 관리자 코드를 새로 정한다.
     *
     * @property notice 직전 입력에 대한 안내. 없으면 null
     */
    data class FirstCodeSetup(val notice: CodeNotice?) : VendorSettingsScreen

    /**
     * 관리자 코드를 입력받는다.
     *
     * @property notice 직전 입력에 대한 안내. 없으면 null
     */
    data class CodeEntry(val notice: CodeNotice?) : VendorSettingsScreen

    /**
     * 항목을 편집한다. 저장 버튼을 눌러야 저장되고, 닫거나 출발하면 저장하지 않은 변경은 버린다.
     *
     * @property draft 편집 중인 업체 설정(저장 전)
     * @property unsaved 저장하지 않은 변경이 있는지
     * @property codeNotice 코드 변경에 대한 안내. 없으면 null
     */
    data class Editing(
        val draft: VendorConfig,
        val unsaved: Boolean,
        val codeNotice: CodeNotice?,
    ) : VendorSettingsScreen
}

/**
 * 업체 설정 화면의 흐름: 처음 코드 정하기 → 코드 입력 → 편집·저장 → 코드 변경 → 닫기.
 * 화면 안쪽 단계는 여기서 관리하고, 운전 상태가 업체 설정(ParkedVendorSettings)이 아니면 언제든 닫힌 것으로 본다.
 * 업체 데이터는 VendorConfig(Aggregate)의 메서드로만 바꾸고, 저장은 저장 버튼을 눌렀을 때만 한다.
 * 상태 머신: B10 (들어옴 B9, 출발하면 T9로 닫힘)
 *
 * @param gate 관리자 코드 확인·설정·변경 창구
 * @param vendorConfigs 업체 데이터 저장소
 * @param engine 입력 → Coordinator → 출력 연결
 */
class VendorSettingsFlow(
    private val gate: AdminCodeGate,
    private val vendorConfigs: VendorConfigRepository,
    private val engine: DriveEngine,
) {

    private val current = MutableStateFlow<VendorSettingsScreen>(VendorSettingsScreen.Closed)

    /** 업체 설정 화면이 그릴 상태. */
    val screen: StateFlow<VendorSettingsScreen> = current.asStateFlow()

    /** 업체 설정 화면이 열릴 때(B9 뒤) 부른다. 파일 상태와 코드 유무에 따라 첫 단계를 정한다. */
    fun open() {
        if (!inVendorSettings()) return
        current.value = when {
            !gate.isAvailable() -> VendorSettingsScreen.ConfigUnreadable
            !gate.hasAdminCode() -> VendorSettingsScreen.FirstCodeSetup(notice = null)
            else -> VendorSettingsScreen.CodeEntry(notice = null)
        }
    }

    /**
     * 처음 설치 때 관리자 코드를 정한다. 두 번 입력이 같고 형식이 맞아야 하며, 정하면 바로 편집으로 들어간다.
     *
     * @param code 새 코드
     * @param confirm 확인 입력
     */
    suspend fun setFirstCode(code: String, confirm: String) {
        if (!stillOpen<VendorSettingsScreen.FirstCodeSetup>()) return
        val notice = newCodeNotice(code, confirm)
        if (notice != null) {
            current.value = VendorSettingsScreen.FirstCodeSetup(notice)
            return
        }
        gate.setInitialCode(code)
        startEditing(codeNotice = null)
    }

    /**
     * 관리자 코드를 입력해 편집으로 들어간다. 반납 처리와 같은 실패 횟수·잠금 규칙을 따른다.
     *
     * @param code 입력한 코드
     */
    suspend fun enterCode(code: String) {
        if (!stillOpen<VendorSettingsScreen.CodeEntry>()) return
        when (val result = gate.verify(code)) {
            AdminGateResult.ConfigUnreadable -> current.value = VendorSettingsScreen.ConfigUnreadable
            is AdminGateResult.Checked -> afterEntry(result.check)
        }
    }

    /**
     * 차량 정보(유종·하이패스)를 편집한다. 저장 버튼을 누르기 전까지 저장하지 않는다.
     *
     * @param vehicle 새 차량 정보
     */
    fun editVehicle(vehicle: VehicleInfo) = editDraft { it.withVehicle(vehicle) }

    /**
     * 반납 조건(장소·예정 시각·연료·배터리 기준)을 편집한다.
     *
     * @param terms 새 반납 조건
     */
    fun editReturnTerms(terms: ReturnTerms) = editDraft { it.withReturnTerms(terms) }

    /**
     * 업체 연락처를 편집한다.
     *
     * @param contact 새 연락처. 지우려면 null
     */
    fun editContact(contact: String?) = editDraft { it.withContact(contact) }

    /**
     * 예비 초기화 시간 N을 편집한다.
     *
     * @param grace 새 N
     */
    fun editResetGrace(grace: Duration) = editDraft { it.withResetGrace(grace) }

    /**
     * 편집한 항목을 저장한다. 관리자 자격(코드·실패 횟수·잠금)은 저장소의 최신 값을 그대로 두고 편집 항목만 덮는다.
     * 업체 설정 상태가 아니면(이미 출발했으면) 저장하지 않는다.
     */
    suspend fun save() {
        val editing = editingOrNull() ?: return
        val draft = editing.draft
        val saved = vendorConfigs.update { latest ->
            latest.withVehicle(draft.vehicle)
                .withReturnTerms(draft.returnTerms)
                .withContact(draft.contact)
                .withResetGrace(draft.resetGrace)
        }
        current.value = VendorSettingsScreen.Editing(saved, unsaved = false, codeNotice = editing.codeNotice)
    }

    /**
     * 관리자 코드를 바꾼다. 지금 코드를 확인하고, 새 코드는 두 번 입력이 같고 형식이 맞아야 한다.
     * 편집 중인 다른 항목은 그대로 두고 저장하지 않는다.
     *
     * @param request 지금 코드와 새 코드
     * @param confirm 새 코드 확인 입력
     */
    suspend fun changeCode(request: AdminCodeChangeRequest, confirm: String) {
        val editing = editingOrNull() ?: return
        val notice = newCodeNotice(request.newCode, confirm) ?: changeNotice(gate.changeCode(request))
        current.value = editing.copy(codeNotice = notice)
    }

    /** 업체 설정을 닫는다. 저장하지 않은 변경은 버린다. 상태 머신: B10 */
    fun close() {
        current.value = VendorSettingsScreen.Closed
        engine.send(DriveEvent.VendorSettingsClosed) // B10
    }

    /** 운전 상태가 바뀌면(출발 등) 부른다. 업체 설정 상태가 아니면 화면을 닫고 저장하지 않은 변경을 버린다. */
    fun syncWithDrive() {
        if (inVendorSettings()) return
        current.value = VendorSettingsScreen.Closed
    }

    /**
     * 코드 입력 결과에 따라 편집으로 들어가거나 안내를 보여 준다.
     *
     * @param check 코드 확인 결과
     */
    private fun afterEntry(check: AdminCodeCheck) {
        when (check) {
            AdminCodeCheck.Verified -> startEditing(codeNotice = null)
            is AdminCodeCheck.Wrong -> current.value = VendorSettingsScreen.CodeEntry(CodeNotice.Wrong(check.remainingAttempts))
            is AdminCodeCheck.Locked -> current.value = VendorSettingsScreen.CodeEntry(CodeNotice.Locked(check.until))
            AdminCodeCheck.NotSet -> current.value = VendorSettingsScreen.FirstCodeSetup(notice = null)
        }
    }

    /**
     * 저장소의 최신 업체 설정으로 편집을 시작한다.
     *
     * @param codeNotice 함께 보여 줄 코드 안내
     */
    private fun startEditing(codeNotice: CodeNotice?) {
        current.value = VendorSettingsScreen.Editing(vendorConfigs.config.value, unsaved = false, codeNotice = codeNotice)
    }

    /**
     * 편집 중인 업체 설정을 바꾼다. 편집 단계가 아니면 아무것도 하지 않는다.
     *
     * @param change 편집 내용(VendorConfig의 메서드)
     */
    private fun editDraft(change: (VendorConfig) -> VendorConfig) {
        val editing = editingOrNull() ?: return
        current.value = editing.copy(draft = change(editing.draft), unsaved = true)
    }

    /**
     * 편집 단계면 그 상태를 돌려준다. 이미 출발해 업체 설정 상태가 아니면 화면을 닫고 null을 돌려준다.
     *
     * @return 편집 상태. 아니면 null
     */
    private fun editingOrNull(): VendorSettingsScreen.Editing? {
        if (!inVendorSettings()) {
            current.value = VendorSettingsScreen.Closed
            return null
        }
        return current.value as? VendorSettingsScreen.Editing
    }

    /**
     * 업체 설정 상태이고 지금 화면 단계가 [T]인지 판단한다. 출발해 상태가 바뀌었으면 화면을 닫는다.
     *
     * @return 그 단계에 있으면 true
     */
    private inline fun <reified T : VendorSettingsScreen> stillOpen(): Boolean {
        if (!inVendorSettings()) {
            current.value = VendorSettingsScreen.Closed
            return false
        }
        return current.value is T
    }

    /**
     * 운전 상태가 업체 설정인지 판단한다. 화면은 기어를 직접 보지 않고 Coordinator 상태만 따른다.
     *
     * @return 업체 설정 상태면 true
     */
    private fun inVendorSettings(): Boolean = engine.state.value == ParkedVendorSettings

    /**
     * 새 코드 입력을 검사한다.
     *
     * @param code 새 코드
     * @param confirm 확인 입력
     * @return 문제가 있으면 안내, 없으면 null
     */
    private fun newCodeNotice(code: String, confirm: String): CodeNotice? = when {
        !AdminCredential.isValidFormat(code) -> CodeNotice.InvalidFormat
        !code.equals(confirm, ignoreCase = true) -> CodeNotice.ConfirmMismatch
        else -> null
    }

    /**
     * 코드 변경 결과를 안내로 바꾼다.
     *
     * @param change 변경 결과. 파일을 읽지 못했으면 null
     * @return 보여 줄 안내
     */
    private fun changeNotice(change: AdminCodeChange?): CodeNotice = when (change) {
        null -> CodeNotice.InvalidFormat
        is AdminCodeChange.Changed -> CodeNotice.Changed
        is AdminCodeChange.InvalidNewCode -> CodeNotice.InvalidFormat
        is AdminCodeChange.Refused -> refusedNotice(change.check)
    }

    /**
     * 지금 코드 확인 실패를 안내로 바꾼다.
     *
     * @param check 지금 코드 확인 결과
     * @return 보여 줄 안내
     */
    private fun refusedNotice(check: AdminCodeCheck): CodeNotice = when (check) {
        is AdminCodeCheck.Wrong -> CodeNotice.Wrong(check.remainingAttempts)
        is AdminCodeCheck.Locked -> CodeNotice.Locked(check.until)
        AdminCodeCheck.Verified, AdminCodeCheck.NotSet -> CodeNotice.InvalidFormat
    }
}
