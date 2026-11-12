package com.annyeongdrive.app.storage

import com.annyeongdrive.app.storage.StorageFixtures.ADMIN_CODE
import com.annyeongdrive.app.storage.StorageFixtures.NOW
import com.annyeongdrive.app.storage.StorageFixtures.SALT
import com.annyeongdrive.app.vendor.AdminCodeCheck
import com.annyeongdrive.app.vendor.VendorConfig
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 업체 데이터 저장소(vendor_config): 처음 설치 기본값, 앱을 다시 켜도 유지, 잠금 유지.
 * 근거: 결정 문서 4장(파일 분리)·5장(업체 설정·오류 잠금).
 */
class VendorConfigRepositoryTest {

    private val stores = TempDataStores()

    /** 임시 파일을 지운다. */
    @After
    fun tearDown() = stores.delete()

    /**
     * vendor_config 저장소를 연다.
     *
     * @return 열린 저장소
     */
    private fun openRepository() = VendorConfigRepository(stores.open(VENDOR_FILE))

    /** 저장된 업체 설정이 없으면 처음 설치 기본값인지 확인한다. */
    @Test
    fun `저장된 업체 설정이 없으면 처음 설치 기본값이다`() = runBlocking {
        // 상황
        val repository = openRepository()
        // 행동
        val loaded = repository.load()
        // 결과
        assertEquals(VendorConfig.initial(), loaded)
    }

    /** 정한 관리자 코드가 앱을 다시 켜도 남아 있는지 확인한다. */
    @Test
    fun `정한 관리자 코드는 앱을 다시 켜도 남아 있다`() = runBlocking {
        // 상황
        openRepository().update { it.setInitialAdminCode(ADMIN_CODE, SALT) }
        stores.close()
        // 행동
        val loaded = openRepository().load()
        // 결과
        assertTrue(loaded.hasAdminCode())
        assertEquals(AdminCodeCheck.Verified, loaded.verifyAdminCode(ADMIN_CODE, NOW).check)
    }

    /** 오류 잠금이 앱을 다시 켜도 풀리지 않는지 확인한다(껐다 켜서 잠금을 피할 수 없음). */
    @Test
    fun `오류 잠금은 앱을 다시 켜도 풀리지 않는다`() = runBlocking {
        // 상황
        val repository = openRepository()
        repository.update { it.setInitialAdminCode(ADMIN_CODE, SALT) }
        repeat(5) { repository.update { config -> config.verifyAdminCode("Wrong123", NOW).config } }
        stores.close()
        // 행동
        val loaded = openRepository().load()
        // 결과
        assertEquals(AdminCodeCheck.Locked(NOW.plusSeconds(30)), loaded.verifyAdminCode(ADMIN_CODE, NOW.plusSeconds(1)).check)
    }

    private companion object {
        const val VENDOR_FILE = "vendor_config"
    }
}
