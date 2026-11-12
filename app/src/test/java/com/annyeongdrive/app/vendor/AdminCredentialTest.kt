package com.annyeongdrive.app.vendor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * 관리자 코드: 형식(영문·숫자 8자리), 해시 저장, 확인, 오류 잠금.
 * 근거: 결정 문서 5장(SHA-256 + salt, 5회 실패 시 30초 잠금), 상태 머신 T32.
 */
class AdminCredentialTest {

    private val policy = LockPolicy()

    /** 영문 대소문자와 숫자를 섞은 8자리, 숫자만, 영문만 8자리가 모두 형식에 맞는지 확인한다. */
    @Test
    fun `관리자 코드는 영문과 숫자로 된 8자리를 쓸 수 있다`() {
        // 상황
        val candidates = listOf(CODE, "20261114", "abcdEFGH")
        // 행동
        val results = candidates.map(AdminCredential::isValidFormat)
        // 결과
        assertTrue(results.all { it })
    }

    /** 8자리가 아니거나 영문·숫자 밖의 글자(기호, 공백, 한글)가 있으면 형식이 틀린지 확인한다. */
    @Test
    fun `8자리가 아니거나 영문과 숫자 밖의 글자가 있으면 관리자 코드로 쓸 수 없다`() {
        // 상황
        val candidates = listOf("Ab12cD3", "Ab12cD345", "Ab12-D34", "Ab12 D34", "코드Ab12cD", "")
        // 행동
        val results = candidates.map(AdminCredential::isValidFormat)
        // 결과
        assertTrue(results.none { it })
    }

    /** 자격에는 코드 원문이 남지 않고 salt와 해시만 남는지 확인한다. */
    @Test
    fun `코드 원문은 저장하지 않고 salt와 해시만 남긴다`() {
        // 상황
        val code = CODE
        // 행동
        val credential = AdminCredential.create(code, SALT_A)
        // 결과
        assertFalse(credential.toString().contains(code))
        assertNotEquals(code, credential.hashBase64)
    }

    /** 같은 코드라도 salt가 다르면 해시가 다른지 확인한다(기기별 salt). */
    @Test
    fun `같은 코드라도 salt가 다르면 해시가 다르다`() {
        // 상황
        val code = CODE
        // 행동
        val first = AdminCredential.create(code, SALT_A)
        val second = AdminCredential.create(code, SALT_B)
        // 결과
        assertNotEquals(first.hashBase64, second.hashBase64)
    }

    /** 맞는 코드를 넣으면 확인되는지 확인한다. */
    @Test
    fun `맞는 코드를 넣으면 확인된다`() {
        // 상황
        val credential = AdminCredential.create(CODE, SALT_A)
        // 행동
        val result = credential.verify(CODE, NOW, policy)
        // 결과
        assertEquals(AdminCodeCheck.Verified, result.check)
    }

    /** 대소문자만 다른 코드는 같은 코드로 보는지 확인한다. */
    @Test
    fun `대소문자가 달라도 같은 코드로 확인된다`() {
        // 상황
        val credential = AdminCredential.create(CODE, SALT_A)
        // 행동
        val lower = credential.verify(CODE.lowercase(), NOW, policy)
        val upper = credential.verify(CODE.uppercase(), NOW, policy)
        // 결과
        assertEquals(AdminCodeCheck.Verified, lower.check)
        assertEquals(AdminCodeCheck.Verified, upper.check)
    }

    /** 틀린 코드를 넣으면 잠기기 전까지 남은 횟수가 줄어드는지 확인한다. */
    @Test
    fun `틀린 코드를 넣으면 남은 횟수가 줄어든다`() {
        // 상황
        val credential = AdminCredential.create(CODE, SALT_A)
        // 행동
        val first = credential.verify(WRONG, NOW, policy)
        val second = first.credential.verify(WRONG, NOW, policy)
        // 결과
        assertEquals(AdminCodeCheck.Wrong(4), first.check)
        assertEquals(AdminCodeCheck.Wrong(3), second.check)
    }

    /** 5번 연속으로 틀리면 30초 동안 잠기는지 확인한다. */
    @Test
    fun `5번 틀리면 30초 동안 잠긴다`() {
        // 상황
        val credential = failedTimes(4)
        // 행동
        val result = credential.verify(WRONG, NOW, policy)
        // 결과
        assertEquals(AdminCodeCheck.Locked(NOW.plusSeconds(30)), result.check)
    }

    /** 잠긴 동안에는 맞는 코드를 넣어도 확인되지 않는지 확인한다. */
    @Test
    fun `잠긴 동안에는 맞는 코드도 확인되지 않는다`() {
        // 상황
        val locked = failedTimes(5)
        // 행동
        val result = locked.verify(CODE, NOW.plusSeconds(29), policy)
        // 결과
        assertEquals(AdminCodeCheck.Locked(NOW.plusSeconds(30)), result.check)
    }

    /** 잠금이 끝나면 다시 5번 시도할 수 있는지 확인한다. */
    @Test
    fun `잠금이 끝나면 다시 5번 시도할 수 있다`() {
        // 상황
        val locked = failedTimes(5)
        // 행동
        val result = locked.verify(WRONG, NOW.plusSeconds(30), policy)
        // 결과
        assertEquals(AdminCodeCheck.Wrong(4), result.check)
        assertNull(result.credential.lockedUntil)
    }

    /** 맞히면 실패 횟수가 0으로 돌아가는지 확인한다. */
    @Test
    fun `맞히면 실패 횟수가 처음으로 돌아간다`() {
        // 상황
        val credential = failedTimes(3)
        // 행동
        val result = credential.verify(CODE, NOW, policy)
        // 결과
        assertEquals(0, result.credential.failedAttempts)
    }

    /** 잠금 기준이 바뀌면 그 기준대로 잠기는지 확인한다(업체 설정 값). */
    @Test
    fun `잠금 기준은 업체가 정한 값을 따른다`() {
        // 상황
        val strict = LockPolicy(maxFailures = 2, lockDuration = Duration.ofMinutes(5))
        val credential = AdminCredential.create(CODE, SALT_A).verify(WRONG, NOW, strict).credential
        // 행동
        val result = credential.verify(WRONG, NOW, strict)
        // 결과
        assertEquals(AdminCodeCheck.Locked(NOW.plus(Duration.ofMinutes(5))), result.check)
    }

    /**
     * 같은 시각에 틀린 코드를 여러 번 넣은 자격을 만든다.
     *
     * @param times 틀린 횟수
     * @return 그만큼 틀린 뒤의 자격
     */
    private fun failedTimes(times: Int): AdminCredential {
        var credential = AdminCredential.create(CODE, SALT_A)
        repeat(times) { credential = credential.verify(WRONG, NOW, policy).credential }
        return credential
    }

    private companion object {
        const val CODE = "Rent2026"
        const val WRONG = "Wrong123"
        val SALT_A = ByteArray(16) { it.toByte() }
        val SALT_B = ByteArray(16) { (it + 1).toByte() }
        val NOW: Instant = Instant.parse("2026-11-12T09:00:00Z")
    }
}
