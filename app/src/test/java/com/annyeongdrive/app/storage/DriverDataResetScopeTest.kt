package com.annyeongdrive.app.storage

import com.annyeongdrive.app.storage.StorageFixtures.ADMIN_CODE
import com.annyeongdrive.app.storage.StorageFixtures.CONTENT_VERSION
import com.annyeongdrive.app.storage.StorageFixtures.SALT
import com.annyeongdrive.app.vendor.ReturnTerms
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 초기화 범위: 운전자 데이터만 지우고 업체 데이터는 남긴다.
 * 근거: 결정 문서 4장(파일 분리, 초기화는 driver_session만)·5장(초기화 범위), 상태 머신 T5.
 */
class DriverDataResetScopeTest {

    private val stores = TempDataStores()

    /** 임시 파일을 지운다. */
    @After
    fun tearDown() = stores.delete()

    /** 운전자 데이터를 지워도 업체 설정(관리자 코드·반납 조건)은 남는지 확인한다. */
    @Test
    fun `운전자 데이터를 지워도 업체 설정은 남는다`() = runBlocking {
        // 상황
        val driver = DriverSessionRepository(stores.open("driver_session"), CONTENT_VERSION)
        val vendor = VendorConfigRepository(stores.open("vendor_config"))
        driver.update { StorageFixtures.startedSession().completePrecheck() }
        val savedVendor = vendor.update { it.setInitialAdminCode(ADMIN_CODE, SALT).withReturnTerms(ReturnTerms(place = "본점")) }
        // 행동
        driver.clear()
        stores.close()
        // 결과
        assertNull(DriverSessionRepository(stores.open("driver_session"), CONTENT_VERSION).load())
        val reopenedVendor = VendorConfigRepository(stores.open("vendor_config"))
        reopenedVendor.load()
        val reloadedVendor = reopenedVendor.config.value
        assertEquals(savedVendor, reloadedVendor)
        assertTrue(reloadedVendor.hasAdminCode())
    }
}
