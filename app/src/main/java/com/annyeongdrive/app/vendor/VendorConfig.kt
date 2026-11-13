package com.annyeongdrive.app.vendor

import com.annyeongdrive.app.time.DurationIsoSerializer
import com.annyeongdrive.app.time.InstantIsoSerializer
import com.annyeongdrive.app.vehicle.FuelType
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

/**
 * 하이패스 이용 여부. 업체가 정한 값만 쓴다. 단말기가 있다는 것을 이용 가능으로 해석하지 않는다.
 */
@Serializable
enum class HiPassAvailability {
    /** 업체가 아직 정하지 않았다. */
    UNKNOWN,

    /** 업체가 이용 가능으로 정했다. */
    AVAILABLE,

    /** 업체가 이용 불가로 정했다. */
    NOT_AVAILABLE,
}

/**
 * 차량 정보(업체 설정).
 *
 * @property fuelType 유종. 정하지 않았으면 UNKNOWN
 * @property hiPass 하이패스 이용 여부
 * @property hiPassSettlement 하이패스 통행료 정산 방식 안내. 없으면 null
 */
@Serializable
data class VehicleInfo(
    val fuelType: FuelType = FuelType.UNKNOWN,
    val hiPass: HiPassAvailability = HiPassAvailability.UNKNOWN,
    val hiPassSettlement: String? = null,
)

/**
 * 반납 조건(업체 설정). 화면에 보여 줄 문구와 예비 초기화 기준 시각이다.
 *
 * @property place 반납 장소 안내. 없으면 null
 * @property dueAt 반납 예정 시각. 없으면 예비 초기화를 하지 않는다
 * @property energyRule 연료·배터리 기준 안내. 없으면 null
 */
@Serializable
data class ReturnTerms(
    val place: String? = null,
    @Serializable(with = InstantIsoSerializer::class)
    val dueAt: Instant? = null,
    val energyRule: String? = null,
)

/**
 * 관리자 코드 변경 요청.
 *
 * @property currentCode 지금 코드(확인용)
 * @property newCode 새 코드(영문·숫자 8자리)
 */
data class AdminCodeChangeRequest(val currentCode: String, val newCode: String)

/** 관리자 코드 변경 결과. 어느 경우든 그 뒤의 업체 설정을 함께 돌려주어 실패 횟수·잠금을 저장할 수 있게 한다. */
sealed interface AdminCodeChange {

    /** 바뀐 뒤의 업체 설정. */
    val config: VendorConfig

    /**
     * 코드를 바꿨다.
     *
     * @property config 새 코드가 들어간 업체 설정
     */
    data class Changed(override val config: VendorConfig) : AdminCodeChange

    /**
     * 지금 코드 확인에 실패해 바꾸지 않았다(틀림·잠김·코드 미설정).
     *
     * @property check 지금 코드 확인 결과
     * @property config 실패 횟수·잠금이 반영된 업체 설정
     */
    data class Refused(val check: AdminCodeCheck, override val config: VendorConfig) : AdminCodeChange

    /**
     * 새 코드 형식이 틀려 바꾸지 않았다. 지금 코드는 확인하지 않아 실패 횟수도 늘지 않는다.
     *
     * @property config 그대로인 업체 설정
     */
    data class InvalidNewCode(override val config: VendorConfig) : AdminCodeChange
}

/**
 * 업체 쪽 관리자 코드 확인 결과와 그 뒤의 업체 설정.
 *
 * @property check 확인 결과
 * @property config 실패 횟수·잠금이 반영된 업체 설정
 */
data class VendorVerification(val check: AdminCodeCheck, val config: VendorConfig)

/**
 * 업체 데이터의 루트(Aggregate). 차량 정보, 반납 조건, 업체 연락처, 예비 초기화 시간 N, 잠금 기준, 관리자 자격을 묶는다.
 * 운전자 데이터 초기화 때 지우지 않는다. 불변 객체이며 바꾸는 메서드는 새 객체를 돌려준다.
 * 근거: 결정 문서 5·6장.
 *
 * @property vehicle 차량 정보
 * @property returnTerms 반납 조건
 * @property contact 업체 연락처 안내. 없으면 null
 * @property resetGrace 예비 초기화 시간 N(반납 예정 시각 뒤 얼마가 지나면 지울지)
 * @property lockPolicy 관리자 코드 오류 잠금 기준
 * @property credential 관리자 자격. 처음 설치해 코드를 정하기 전이면 null
 */
@Serializable
@ConsistentCopyVisibility
data class VendorConfig private constructor(
    val vehicle: VehicleInfo,
    val returnTerms: ReturnTerms,
    val contact: String?,
    @Serializable(with = DurationIsoSerializer::class)
    val resetGrace: Duration,
    val lockPolicy: LockPolicy,
    val credential: AdminCredential?,
) {

    /**
     * 관리자 코드를 정했는지 알려 준다. 정하지 않았으면 업체 설정 화면에서 처음 코드를 정한다.
     *
     * @return 코드를 정했으면 true
     */
    fun hasAdminCode(): Boolean = credential != null

    /**
     * 처음 설치 때 관리자 코드를 정한다. 이미 정한 코드를 바꾸는 기능은 4단계 업체 설정 화면에서 따로 추가한다.
     *
     * @param code 영문·숫자 8자리 코드
     * @param salt 기기별 무작위 salt
     * @return 코드가 정해진 업체 설정
     * @throws IllegalStateException 이미 코드를 정했으면
     * @throws IllegalArgumentException 코드 형식이 틀리면
     */
    fun setInitialAdminCode(code: String, salt: ByteArray): VendorConfig {
        check(!hasAdminCode()) { "처음 코드 정하기는 코드가 없을 때만 한다" }
        return copy(credential = AdminCredential.create(code, salt))
    }

    /**
     * 관리자 코드를 확인한다. 반납 처리와 업체 설정 화면 진입에 같은 규칙을 쓴다.
     * 상태 머신: T32 (확인되면 화면이 AdminCodeVerified 이벤트를 낸다)
     *
     * @param input 입력한 코드
     * @param now 지금 시각
     * @return 확인 결과와 바뀐 업체 설정
     */
    fun verifyAdminCode(input: String, now: Instant): VendorVerification {
        val current = credential ?: return VendorVerification(AdminCodeCheck.NotSet, this)
        val verification = current.verify(input, now, lockPolicy)
        return VendorVerification(verification.check, copy(credential = verification.credential))
    }

    /**
     * 지금 코드를 확인한 뒤 새 코드로 바꾼다. 지금 코드 확인은 반납·진입과 같은 실패 횟수·잠금 규칙을 따른다.
     * 새 코드 형식이 틀리면 지금 코드를 확인하지 않고 거절한다.
     *
     * @param request 지금 코드와 새 코드
     * @param now 지금 시각
     * @param salt 새 코드에 쓸 기기별 무작위 salt
     * @return 변경 결과와 바뀐 업체 설정
     */
    fun changeAdminCode(request: AdminCodeChangeRequest, now: Instant, salt: ByteArray): AdminCodeChange {
        if (!AdminCredential.isValidFormat(request.newCode)) return AdminCodeChange.InvalidNewCode(this)
        val verification = verifyAdminCode(request.currentCode, now)
        if (verification.check != AdminCodeCheck.Verified) {
            return AdminCodeChange.Refused(verification.check, verification.config)
        }
        val changed = verification.config.copy(credential = AdminCredential.create(request.newCode, salt))
        return AdminCodeChange.Changed(changed)
    }

    /**
     * 반납 예정 시각 + N시간이 지나 운전자 데이터를 예비 초기화해야 하는지 판단한다. 앱 시작 때 확인한다.
     * 상태 머신: T4 가드 (2-d에서 RentalStatusReader가 읽는다)
     *
     * @param now 지금 시각
     * @return 반납 예정 시각이 있고 그 뒤 N시간이 지났으면 true
     */
    fun isResetOverdue(now: Instant): Boolean {
        val dueAt = returnTerms.dueAt ?: return false
        return !now.isBefore(dueAt.plus(resetGrace))
    }

    /**
     * 차량 정보를 바꾼다.
     *
     * @param newVehicle 새 차량 정보
     * @return 바뀐 업체 설정
     */
    fun withVehicle(newVehicle: VehicleInfo): VendorConfig = copy(vehicle = newVehicle)

    /**
     * 반납 조건을 바꾼다.
     *
     * @param newTerms 새 반납 조건
     * @return 바뀐 업체 설정
     */
    fun withReturnTerms(newTerms: ReturnTerms): VendorConfig = copy(returnTerms = newTerms)

    /**
     * 업체 연락처를 바꾼다.
     *
     * @param newContact 새 연락처. 지우려면 null
     * @return 바뀐 업체 설정
     */
    fun withContact(newContact: String?): VendorConfig = copy(contact = newContact)

    /**
     * 예비 초기화 시간 N을 바꾼다.
     *
     * @param newGrace 새 N
     * @return 바뀐 업체 설정
     */
    fun withResetGrace(newGrace: Duration): VendorConfig = copy(resetGrace = newGrace)

    /**
     * 관리자 코드 오류 잠금 기준을 바꾼다.
     *
     * @param newPolicy 새 잠금 기준
     * @return 바뀐 업체 설정
     */
    fun withLockPolicy(newPolicy: LockPolicy): VendorConfig = copy(lockPolicy = newPolicy)

    companion object {
        /** 예비 초기화 시간 N의 기본값. 업체 설정 화면에서 바꾼다. */
        val DEFAULT_RESET_GRACE: Duration = Duration.ofHours(24)

        /**
         * 처음 설치한 기기의 업체 설정을 만든다. 관리자 코드는 아직 없다.
         *
         * @return 기본값의 업체 설정
         */
        fun initial(): VendorConfig = VendorConfig(
            vehicle = VehicleInfo(),
            returnTerms = ReturnTerms(),
            contact = null,
            resetGrace = DEFAULT_RESET_GRACE,
            lockPolicy = LockPolicy(),
            credential = null,
        )
    }
}
