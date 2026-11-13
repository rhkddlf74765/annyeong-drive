package com.annyeongdrive.app.admin

import com.annyeongdrive.app.storage.StorageFixtures
import com.annyeongdrive.app.storage.StorageFixtures.ADMIN_CODE
import com.annyeongdrive.app.storage.StorageFixtures.SALT
import com.annyeongdrive.app.storage.TempDataStores
import com.annyeongdrive.app.storage.VendorConfigRepository
import com.annyeongdrive.app.vendor.AdminCodeCheck
import com.annyeongdrive.app.vendor.SaltSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 관리자 코드 확인 창구: 확인 결과(실패 횟수·잠금)를 바로 저장하고, 코드 미설정·파일 손상을 구분한다.
 * 근거: 결정 문서 5장(같은 잠금 규칙을 반납과 업체 설정 진입에 적용), 11/13 결정.
 */
class AdminCodeGateTest {

    private val stores = TempDataStores()
    private val clock = MutableClock(StorageFixtures.NOW)

    /** 임시 파일을 지운다. */
    @After
    fun tearDown() = stores.delete()

    /**
     * 업체 설정 저장소를 열어 읽고 창구를 만든다.
     *
     * @param repository 업체 설정 저장소
     * @return 관리자 코드 창구
     */
    private suspend fun gateOf(repository: VendorConfigRepository): AdminCodeGate {
        repository.load()
        return AdminCodeGate(repository, clock, SaltSource { SALT })
    }

    /** 틀린 코드의 실패 횟수가 바로 저장돼, 앱을 다시 켜도 남은 횟수가 이어지는지 확인한다. */
    @Test
    fun `틀린 코드는 바로 저장돼 앱을 다시 켜도 남은 횟수가 이어진다`() = runBlocking {
        // 상황
        VendorConfigRepository(stores.open(VENDOR)).update { it.setInitialAdminCode(ADMIN_CODE, SALT) }
        stores.close()
        gateOf(VendorConfigRepository(stores.open(VENDOR))).verify("Wrong123")
        stores.close()
        // 행동
        val result = gateOf(VendorConfigRepository(stores.open(VENDOR))).verify("Wrong123")
        // 결과
        assertEquals(AdminGateResult.Checked(AdminCodeCheck.Wrong(3)), result)
    }

    /** 코드를 정하지 않았으면 확인하지 않고 미설정을 알리며 파일에 쓰지 않는지 확인한다. */
    @Test
    fun `코드를 정하지 않았으면 미설정을 알리고 파일에 쓰지 않는다`() = runBlocking {
        // 상황
        val gate = gateOf(VendorConfigRepository(stores.open(VENDOR)))
        // 행동
        val result = gate.verify(ADMIN_CODE)
        stores.close()
        // 결과
        assertEquals(AdminGateResult.Checked(AdminCodeCheck.NotSet), result)
        assertFalse(stores.fileOf(VENDOR).exists())
    }

    /** 업체 설정 파일을 읽지 못하면 확인하지 않는지 확인한다. */
    @Test
    fun `업체 설정 파일을 읽지 못하면 확인하지 않는다`() = runBlocking {
        // 상황
        stores.fileOf(VENDOR).writeBytes(byteArrayOf(1, 2, 3))
        val gate = gateOf(VendorConfigRepository(stores.open(VENDOR)))
        // 행동
        val result = gate.verify(ADMIN_CODE)
        // 결과
        assertEquals(AdminGateResult.ConfigUnreadable, result)
        assertFalse(gate.isAvailable())
    }

    private companion object {
        const val VENDOR = "vendor_config"
    }
}
