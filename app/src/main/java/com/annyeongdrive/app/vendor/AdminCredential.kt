package com.annyeongdrive.app.vendor

import com.annyeongdrive.app.time.DurationIsoSerializer
import com.annyeongdrive.app.time.InstantIsoSerializer
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Locale

/**
 * 관리자 코드 확인 결과.
 * 상태 머신: T32 (확인되면 화면이 AdminCodeVerified 이벤트를 낸다)
 */
sealed interface AdminCodeCheck {

    /** 코드가 맞다. */
    data object Verified : AdminCodeCheck

    /**
     * 코드가 틀렸다.
     *
     * @property remainingAttempts 잠기기 전까지 남은 시도 횟수
     */
    data class Wrong(val remainingAttempts: Int) : AdminCodeCheck

    /**
     * 잠겨 있어 확인하지 않았다.
     *
     * @property until 잠금이 풀리는 시각
     */
    data class Locked(val until: Instant) : AdminCodeCheck

    /** 아직 관리자 코드를 정하지 않았다(처음 설치). */
    data object NotSet : AdminCodeCheck
}

/**
 * 관리자 코드 오류 잠금 기준. 업체 설정 값이다(결정 문서 5장).
 *
 * @property maxFailures 잠그기 전까지 허용하는 연속 실패 횟수
 * @property lockDuration 잠금 시간
 */
@Serializable
data class LockPolicy(
    val maxFailures: Int = 5,
    @Serializable(with = DurationIsoSerializer::class)
    val lockDuration: Duration = Duration.ofSeconds(30),
)

/** 기기별 무작위 salt를 만드는 창구. 테스트에서는 고정 값을 넣는다. */
fun interface SaltSource {

    /**
     * 새 salt를 만든다.
     *
     * @return salt 바이트
     */
    fun next(): ByteArray
}

/** [SecureRandom]으로 16바이트 salt를 만드는 [SaltSource]. */
class SecureRandomSaltSource : SaltSource {

    private val random = SecureRandom()

    /**
     * 16바이트 무작위 salt를 만든다.
     *
     * @return salt 바이트
     */
    override fun next(): ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)

    private companion object {
        const val SALT_BYTES = 16
    }
}

/**
 * 관리자 코드 확인 한 번의 결과와 그 뒤의 자격 상태.
 *
 * @property check 확인 결과
 * @property credential 실패 횟수·잠금이 반영된 자격
 */
data class CredentialVerification(val check: AdminCodeCheck, val credential: AdminCredential)

/**
 * 관리자 자격. 코드 원문은 저장하지 않고 salt와 SHA-256 해시만 둔다. 실패 횟수와 잠금도 여기서 관리한다.
 * 코드는 영문 대소문자와 숫자로 된 정확히 8자리다. 대소문자는 구분하지 않는다(대문자로 맞춘 뒤 해시한다).
 * [VendorConfig] 안에 있고, 바깥에서는 VendorConfig의 메서드로만 바꾼다. 불변 객체다.
 * 코드 변경은 4단계 업체 설정 화면에서 추가한다.
 * 근거: 결정 문서 5장.
 *
 * @property saltBase64 기기별 무작위 salt(Base64)
 * @property hashBase64 SHA-256(salt + 코드)(Base64)
 * @property failedAttempts 지금까지 연속으로 틀린 횟수
 * @property lockedUntil 잠금이 풀리는 시각. 잠기지 않았으면 null
 */
@Serializable
@ConsistentCopyVisibility
data class AdminCredential private constructor(
    val saltBase64: String,
    val hashBase64: String,
    val failedAttempts: Int,
    @Serializable(with = InstantIsoSerializer::class)
    val lockedUntil: Instant?,
) {

    /**
     * 입력한 코드를 확인한다. 잠겨 있으면 확인하지 않는다.
     * 잠금이 끝났으면 다시 [LockPolicy.maxFailures]번 시도할 수 있고, 맞히면 실패 횟수를 0으로 되돌린다.
     *
     * @param input 입력한 코드
     * @param now 지금 시각
     * @param policy 잠금 기준
     * @return 확인 결과와 바뀐 자격
     */
    fun verify(input: String, now: Instant, policy: LockPolicy): CredentialVerification {
        val until = lockedUntil
        if (until != null && now.isBefore(until)) return CredentialVerification(AdminCodeCheck.Locked(until), this)
        val unlocked = clearExpiredLock()
        if (unlocked.matches(input)) return CredentialVerification(AdminCodeCheck.Verified, unlocked.resetFailures())
        return unlocked.recordFailure(now, policy)
    }

    /**
     * 입력한 코드의 해시가 저장된 해시와 같은지 일정한 시간으로 비교한다.
     *
     * @param input 입력한 코드
     * @return 같으면 true
     */
    private fun matches(input: String): Boolean {
        val salt = Base64.getDecoder().decode(saltBase64)
        val expected = Base64.getDecoder().decode(hashBase64)
        return MessageDigest.isEqual(hash(salt, normalize(input)), expected)
    }

    /**
     * 지난 잠금을 지운다.
     *
     * @return 잠금이 없는 자격
     */
    private fun clearExpiredLock(): AdminCredential = copy(lockedUntil = null)

    /**
     * 실패 횟수를 0으로 되돌린다.
     *
     * @return 실패 기록이 없는 자격
     */
    private fun resetFailures(): AdminCredential = copy(failedAttempts = 0)

    /**
     * 실패를 기록한다. 허용 횟수에 이르면 잠그고 실패 횟수를 0으로 되돌린다.
     *
     * @param now 지금 시각
     * @param policy 잠금 기준
     * @return 틀림 또는 잠김 결과
     */
    private fun recordFailure(now: Instant, policy: LockPolicy): CredentialVerification {
        val failures = failedAttempts + 1
        if (failures < policy.maxFailures) {
            return CredentialVerification(AdminCodeCheck.Wrong(policy.maxFailures - failures), copy(failedAttempts = failures))
        }
        val until = now.plus(policy.lockDuration)
        return CredentialVerification(AdminCodeCheck.Locked(until), copy(failedAttempts = 0, lockedUntil = until))
    }

    companion object {
        private const val CODE_LENGTH = 8

        /**
         * 관리자 코드 형식이 맞는지 판단한다. 영문 대소문자와 숫자로 된 정확히 8자리다.
         *
         * @param code 검사할 코드
         * @return 영문·숫자 8자리면 true
         */
        fun isValidFormat(code: String): Boolean = code.length == CODE_LENGTH && code.all(::isAllowedChar)

        /**
         * 처음 정하는 코드로 자격을 만든다. 코드 원문은 남기지 않는다.
         *
         * @param code 영문·숫자 8자리 코드
         * @param salt 기기별 무작위 salt
         * @return 새 자격
         * @throws IllegalArgumentException 코드 형식이 틀리면
         */
        fun create(code: String, salt: ByteArray): AdminCredential {
            require(isValidFormat(code)) { "관리자 코드는 영문·숫자 8자리여야 한다" }
            val encoder = Base64.getEncoder()
            return AdminCredential(
                saltBase64 = encoder.encodeToString(salt),
                hashBase64 = encoder.encodeToString(hash(salt, normalize(code))),
                failedAttempts = 0,
                lockedUntil = null,
            )
        }

        /**
         * 대소문자를 구분하지 않도록 코드를 대문자로 맞춘다.
         *
         * @param code 입력한 코드
         * @return 대문자로 맞춘 코드
         */
        private fun normalize(code: String): String = code.uppercase(Locale.ROOT)

        /**
         * 관리자 코드에 쓸 수 있는 글자인지 판단한다. ASCII 영문 대소문자와 숫자만 허용한다.
         *
         * @param char 검사할 글자
         * @return 허용하는 글자면 true
         */
        private fun isAllowedChar(char: Char): Boolean =
            char in '0'..'9' || char in 'a'..'z' || char in 'A'..'Z'

        /**
         * SHA-256(salt + 코드)를 계산한다.
         *
         * @param salt salt 바이트
         * @param code 코드
         * @return 해시 바이트
         */
        private fun hash(salt: ByteArray, code: String): ByteArray {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(salt)
            return digest.digest(code.toByteArray(Charsets.UTF_8))
        }
    }
}
