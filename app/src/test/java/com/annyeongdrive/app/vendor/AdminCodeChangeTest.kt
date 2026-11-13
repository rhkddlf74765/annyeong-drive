package com.annyeongdrive.app.vendor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * 관리자 코드 변경: 지금 코드를 확인한 뒤에만 새 코드로 바꾼다.
 * 근거: 결정 문서 5장(관리자 코드·오류 잠금), implementation-plan 4단계 "관리자 코드 변경".
 */
class AdminCodeChangeTest {

    private val config = VendorConfig.initial().setInitialAdminCode(CODE, SALT)

    /** 지금 코드가 맞으면 새 코드로 바뀌고 옛 코드는 더 이상 맞지 않는지 확인한다. */
    @Test
    fun `지금 코드가 맞으면 새 코드로 바뀐다`() {
        // 상황
        val request = AdminCodeChangeRequest(CODE, NEW_CODE)
        // 행동
        val change = config.changeAdminCode(request, NOW, SALT)
        // 결과
        assertTrue(change is AdminCodeChange.Changed)
        assertEquals(AdminCodeCheck.Verified, change.config.verifyAdminCode(NEW_CODE, NOW).check)
        assertEquals(AdminCodeCheck.Wrong(4), change.config.verifyAdminCode(CODE, NOW).check)
    }

    /** 지금 코드가 틀리면 바꾸지 않고 실패로 세는지 확인한다. */
    @Test
    fun `지금 코드가 틀리면 바꾸지 않고 실패로 센다`() {
        // 상황
        val request = AdminCodeChangeRequest("Wrong123", NEW_CODE)
        // 행동
        val change = config.changeAdminCode(request, NOW, SALT)
        // 결과
        assertEquals(AdminCodeChange.Refused(AdminCodeCheck.Wrong(4), change.config), change)
        assertEquals(1, change.config.credential?.failedAttempts)
    }

    /** 새 코드 형식이 틀리면 지금 코드를 확인하지 않아 실패 횟수가 늘지 않는지 확인한다. */
    @Test
    fun `새 코드 형식이 틀리면 지금 코드를 확인하지 않는다`() {
        // 상황
        val request = AdminCodeChangeRequest("Wrong123", "short")
        // 행동
        val change = config.changeAdminCode(request, NOW, SALT)
        // 결과
        assertEquals(AdminCodeChange.InvalidNewCode(config), change)
    }

    /** 잠긴 동안에는 맞는 지금 코드로도 바꿀 수 없는지 확인한다. */
    @Test
    fun `잠긴 동안에는 코드를 바꿀 수 없다`() {
        // 상황
        var locked = config
        repeat(5) { locked = locked.verifyAdminCode("Wrong123", NOW).config }
        // 행동
        val change = locked.changeAdminCode(AdminCodeChangeRequest(CODE, NEW_CODE), NOW, SALT)
        // 결과
        assertEquals(AdminCodeCheck.Locked(NOW.plusSeconds(30)), (change as AdminCodeChange.Refused).check)
    }

    private companion object {
        const val CODE = "Rent2026"
        const val NEW_CODE = "NewCode9"
        val SALT = ByteArray(16) { it.toByte() }
        val NOW: Instant = Instant.parse("2026-11-13T09:00:00Z")
    }
}
