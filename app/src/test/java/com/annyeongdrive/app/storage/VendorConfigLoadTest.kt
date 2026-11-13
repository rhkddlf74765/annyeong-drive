package com.annyeongdrive.app.storage

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.annyeongdrive.app.storage.StorageFixtures.ADMIN_CODE
import com.annyeongdrive.app.storage.StorageFixtures.SALT
import com.annyeongdrive.app.vendor.VendorConfig
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 저장 파일이 깨졌을 때: 앱이 멈추지 않고, 업체 설정은 "읽지 못함"으로 표시해 덮어쓰지 않으며, 운전자 데이터는 없는 것으로 본다.
 * 근거: 결정 문서 4·5장, 11/13 결정(깨진 업체 설정 파일이면 업체 설정 진입·반납 확인 차단).
 */
class VendorConfigLoadTest {

    private val stores = TempDataStores()

    /** 임시 파일을 지운다. */
    @After
    fun tearDown() = stores.delete()

    /**
     * 업체 설정 파일의 JSON 자리에 원하는 글자를 직접 써 둔다.
     *
     * @param text 써 둘 글자
     */
    private fun writeVendorText(text: String) = runBlocking {
        stores.open(VENDOR).edit { it[stringPreferencesKey("document")] = text }
        stores.close()
    }

    /** 저장된 업체 설정이 있으면 읽음으로 표시하는지 확인한다. */
    @Test
    fun `저장된 업체 설정이 있으면 읽음으로 표시한다`() = runBlocking {
        // 상황
        VendorConfigRepository(stores.open(VENDOR)).update { it.setInitialAdminCode(ADMIN_CODE, SALT) }
        stores.close()
        // 행동
        val result = VendorConfigRepository(stores.open(VENDOR)).load()
        // 결과
        assertEquals(VendorLoadState.LOADED, result)
    }

    /** 파일 자체가 손상돼 있으면 앱을 멈추지 않고 읽지 못함으로 표시하는지 확인한다. */
    @Test
    fun `파일이 손상돼 있으면 읽지 못함으로 표시하고 앱을 멈추지 않는다`() = runBlocking {
        // 상황
        stores.fileOf(VENDOR).writeBytes(byteArrayOf(1, 2, 3, 4))
        val repository = VendorConfigRepository(stores.open(VENDOR))
        // 행동
        val result = repository.load()
        // 결과
        assertEquals(VendorLoadState.UNREADABLE, result)
        assertEquals(VendorConfig.initial(), repository.config.value)
    }

    /** 파일은 멀쩡하지만 안의 내용이 깨진 경우에도 읽지 못함으로 표시하는지 확인한다. */
    @Test
    fun `내용이 깨져 있으면 읽지 못함으로 표시한다`() = runBlocking {
        // 상황
        writeVendorText("{\"schemaVersion\":1,\"data\":{\"vehicle\":")
        // 행동
        val result = VendorConfigRepository(stores.open(VENDOR)).load()
        // 결과
        assertEquals(VendorLoadState.UNREADABLE, result)
    }

    /** 다른 저장 구조 버전(앱 업데이트 등)이면 읽지 못함으로 표시하는지 확인한다. */
    @Test
    fun `저장 구조 버전이 다르면 읽지 못함으로 표시한다`() = runBlocking {
        // 상황
        val saved = DocumentCodec.forVendorConfig().encode(VendorConfig.initial())
        writeVendorText(saved.replace("\"schemaVersion\":1", "\"schemaVersion\":2"))
        // 행동
        val result = VendorConfigRepository(stores.open(VENDOR)).load()
        // 결과
        assertEquals(VendorLoadState.UNREADABLE, result)
    }

    /** 읽지 못한 업체 설정 파일은 덮어쓰지 않는지 확인한다(관리자 코드가 사라져 누구나 새로 정하는 일을 막음). */
    @Test(expected = IllegalStateException::class)
    fun `읽지 못한 업체 설정 파일은 덮어쓰지 않는다`() = runBlocking {
        // 상황
        stores.fileOf(VENDOR).writeBytes(byteArrayOf(1, 2, 3, 4))
        val repository = VendorConfigRepository(stores.open(VENDOR))
        repository.load()
        // 행동
        repository.update { it.setInitialAdminCode("Hack2026", SALT) }
        // 결과: IllegalStateException
        Unit
    }

    /** 운전자 데이터 파일이 손상돼 있으면 앱을 멈추지 않고 대여가 없는 것으로 시작하는지 확인한다. */
    @Test
    fun `운전자 데이터 파일이 손상돼 있으면 대여 없이 시작한다`() = runBlocking {
        // 상황
        stores.fileOf(DRIVER).writeBytes(byteArrayOf(7, 7, 7))
        val repository = DriverSessionRepository(stores.open(DRIVER, replaceCorrupt = true), StorageFixtures.CONTENT_VERSION)
        // 행동
        val loaded = repository.load()
        // 결과
        assertNull(loaded)
    }

    /** 손상된 운전자 데이터 파일 위에도 새 대여를 저장할 수 있는지 확인한다(깨진 파일 때문에 저장이 막히지 않음). */
    @Test
    fun `운전자 데이터 파일이 손상돼 있어도 새 대여를 저장할 수 있다`() = runBlocking {
        // 상황
        stores.fileOf(DRIVER).writeBytes(byteArrayOf(7, 7, 7))
        val repository = DriverSessionRepository(stores.open(DRIVER, replaceCorrupt = true), StorageFixtures.CONTENT_VERSION)
        repository.load()
        // 행동
        repository.update { StorageFixtures.startedSession() }
        stores.close()
        // 결과
        val reopened = DriverSessionRepository(stores.open(DRIVER, replaceCorrupt = true), StorageFixtures.CONTENT_VERSION)
        assertEquals("rental-0001", reopened.load()?.rentalId)
    }

    private companion object {
        const val VENDOR = "vendor_config"
        const val DRIVER = "driver_session"
    }
}
