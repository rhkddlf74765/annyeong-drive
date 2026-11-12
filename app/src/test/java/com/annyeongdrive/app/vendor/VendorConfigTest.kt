package com.annyeongdrive.app.vendor

import com.annyeongdrive.app.vehicle.FuelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * 업체 데이터 루트: 처음 설치, 관리자 코드 설정·확인, 기본값.
 * 근거: 결정 문서 5장(업체 설정·반납 처리), 6장(업체 데이터는 초기화하지 않음), 기능 실행안 1번(하이패스·유종).
 */
class VendorConfigTest {

    /** 처음 설치한 업체 설정에는 관리자 코드가 없는지 확인한다. */
    @Test
    fun `처음 설치한 업체 설정에는 관리자 코드가 없다`() {
        // 상황
        val config = VendorConfig.initial()
        // 행동
        val hasCode = config.hasAdminCode()
        // 결과
        assertFalse(hasCode)
    }

    /** 처음 설치 때 코드를 정하면 코드가 있는 상태가 되는지 확인한다. */
    @Test
    fun `처음 설치 때 관리자 코드를 정할 수 있다`() {
        // 상황
        val config = VendorConfig.initial()
        // 행동
        val withCode = config.setInitialAdminCode(CODE, SALT)
        // 결과
        assertTrue(withCode.hasAdminCode())
    }

    /** 코드가 이미 있으면 "처음 코드 정하기"로 다시 정할 수 없는지 확인한다(변경은 4단계에서 따로 추가). */
    @Test(expected = IllegalStateException::class)
    fun `관리자 코드가 이미 있으면 처음 코드 정하기를 다시 할 수 없다`() {
        // 상황
        val config = VendorConfig.initial().setInitialAdminCode(CODE, SALT)
        // 행동
        config.setInitialAdminCode("Other123", SALT)
        // 결과: IllegalStateException
    }

    /** 영문·숫자 8자리가 아닌 코드로는 정할 수 없는지 확인한다. */
    @Test(expected = IllegalArgumentException::class)
    fun `영문과 숫자로 된 8자리가 아닌 코드로는 정할 수 없다`() {
        // 상황
        val config = VendorConfig.initial()
        // 행동
        config.setInitialAdminCode("Rent-26!", SALT)
        // 결과: IllegalArgumentException
    }

    /** 정한 코드를 넣으면 확인되고, 틀린 기록이 업체 설정에 남는지 확인한다. */
    @Test
    fun `정한 코드로 확인하면 확인되고 틀린 기록은 업체 설정에 남는다`() {
        // 상황
        val config = VendorConfig.initial().setInitialAdminCode(CODE, SALT)
        // 행동
        val wrong = config.verifyAdminCode("Wrong123", NOW)
        val right = wrong.config.verifyAdminCode(CODE, NOW)
        // 결과
        assertEquals(1, wrong.config.credential?.failedAttempts)
        assertEquals(AdminCodeCheck.Verified, right.check)
    }

    /** 코드를 정하기 전에 확인하면 "코드 미설정"이 나오는지 확인한다. */
    @Test
    fun `관리자 코드를 정하기 전에 확인하면 코드 미설정이 나온다`() {
        // 상황
        val config = VendorConfig.initial()
        // 행동
        val result = config.verifyAdminCode(CODE, NOW)
        // 결과
        assertEquals(AdminCodeCheck.NotSet, result.check)
    }

    /** 하이패스와 유종은 업체가 정하기 전까지 알 수 없음인지 확인한다(단말기 존재를 이용 가능으로 보지 않음). */
    @Test
    fun `하이패스와 유종은 업체가 정하기 전까지 알 수 없음이다`() {
        // 상황
        val config = VendorConfig.initial()
        // 행동
        val vehicle = config.vehicle
        // 결과
        assertEquals(HiPassAvailability.UNKNOWN, vehicle.hiPass)
        assertEquals(FuelType.UNKNOWN, vehicle.fuelType)
    }

    /** 차량 정보를 바꿔도 관리자 자격은 그대로인지 확인한다. */
    @Test
    fun `차량 정보를 바꿔도 관리자 자격은 그대로다`() {
        // 상황
        val config = VendorConfig.initial().setInitialAdminCode(CODE, SALT)
        // 행동
        val changed = config.withVehicle(VehicleInfo(FuelType.DIESEL, HiPassAvailability.AVAILABLE, "반납 시 정산"))
        // 결과
        assertEquals(FuelType.DIESEL, changed.vehicle.fuelType)
        assertEquals(config.credential, changed.credential)
    }

    private companion object {
        const val CODE = "Rent2026"
        val SALT = ByteArray(16) { it.toByte() }
        val NOW: Instant = Instant.parse("2026-11-12T09:00:00Z")
    }
}
