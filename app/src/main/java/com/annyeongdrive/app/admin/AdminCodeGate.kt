package com.annyeongdrive.app.admin

import com.annyeongdrive.app.storage.VendorConfigRepository
import com.annyeongdrive.app.storage.VendorLoadState
import com.annyeongdrive.app.time.Clock
import com.annyeongdrive.app.vendor.AdminCodeChange
import com.annyeongdrive.app.vendor.AdminCodeChangeRequest
import com.annyeongdrive.app.vendor.AdminCodeCheck
import com.annyeongdrive.app.vendor.SaltSource

/** 관리자 코드 확인 창구의 결과. */
sealed interface AdminGateResult {

    /**
     * 업체 설정 규칙대로 확인했다.
     *
     * @property check 확인 결과(맞음·틀림·잠김·미설정)
     */
    data class Checked(val check: AdminCodeCheck) : AdminGateResult

    /** 업체 설정 파일을 읽지 못해 확인할 수 없다. 업체에 문의하도록 안내한다. */
    data object ConfigUnreadable : AdminGateResult
}

/**
 * 관리자 코드에 관한 저장소 작업을 한곳에서 맡는다: 확인, 처음 코드 정하기, 코드 변경.
 * 반납 처리와 업체 설정 진입이 같은 규칙(실패 횟수·잠금)을 쓰고, 결과는 바로 저장해 앱을 껐다 켜도 남는다.
 * 규칙 자체는 VendorConfig·AdminCredential(Aggregate)에 있고, 여기서는 시각·salt를 넣고 저장만 한다.
 *
 * @param vendorConfigs 업체 데이터 저장소
 * @param clock 지금 시각
 * @param salts 새 코드에 쓸 salt
 */
class AdminCodeGate(
    private val vendorConfigs: VendorConfigRepository,
    private val clock: Clock,
    private val salts: SaltSource,
) {

    /**
     * 업체 설정 파일을 읽을 수 있는지 알려 준다.
     *
     * @return 읽지 못한 상태가 아니면 true
     */
    fun isAvailable(): Boolean = vendorConfigs.loadState.value != VendorLoadState.UNREADABLE

    /**
     * 관리자 코드를 정했는지 알려 준다.
     *
     * @return 정했으면 true
     */
    fun hasAdminCode(): Boolean = vendorConfigs.config.value.hasAdminCode()

    /**
     * 입력한 코드를 확인하고 실패 횟수·잠금을 바로 저장한다.
     * 코드를 아직 정하지 않았으면 저장하지 않고 미설정을 돌려준다.
     *
     * @param input 입력한 코드
     * @return 확인 결과
     */
    suspend fun verify(input: String): AdminGateResult {
        if (!isAvailable()) return AdminGateResult.ConfigUnreadable
        if (!hasAdminCode()) return AdminGateResult.Checked(AdminCodeCheck.NotSet)
        var check: AdminCodeCheck = AdminCodeCheck.NotSet
        vendorConfigs.update { config ->
            val verification = config.verifyAdminCode(input, clock.now())
            check = verification.check
            verification.config
        }
        return AdminGateResult.Checked(check)
    }

    /**
     * 처음 설치 때 관리자 코드를 정하고 저장한다. 형식 검사는 부르는 쪽이 먼저 한다.
     *
     * @param code 영문·숫자 8자리 코드
     * @throws IllegalStateException 파일을 읽지 못했거나 이미 코드를 정했으면
     */
    suspend fun setInitialCode(code: String) {
        check(isAvailable()) { "읽지 못한 업체 설정에는 코드를 정하지 않는다" }
        vendorConfigs.update { it.setInitialAdminCode(code, salts.next()) }
    }

    /**
     * 지금 코드를 확인한 뒤 새 코드로 바꾸고 저장한다. 실패 횟수·잠금도 함께 저장한다.
     *
     * @param request 지금 코드와 새 코드
     * @return 변경 결과. 파일을 읽지 못했으면 null
     */
    suspend fun changeCode(request: AdminCodeChangeRequest): AdminCodeChange? {
        if (!isAvailable()) return null
        var result: AdminCodeChange? = null
        vendorConfigs.update { config ->
            val change = config.changeAdminCode(request, clock.now(), salts.next())
            result = change
            change.config
        }
        return result
    }
}
