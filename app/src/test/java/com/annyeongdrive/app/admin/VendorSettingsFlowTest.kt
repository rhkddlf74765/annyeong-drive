package com.annyeongdrive.app.admin

import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.drive.state.Idle
import com.annyeongdrive.app.drive.state.ParkedRest
import com.annyeongdrive.app.drive.state.Returning
import com.annyeongdrive.app.gear.Gear
import com.annyeongdrive.app.storage.StorageFixtures.ADMIN_CODE
import com.annyeongdrive.app.storage.StorageFixtures.NOW
import com.annyeongdrive.app.storage.TempDataStores
import com.annyeongdrive.app.vehicle.FuelType
import com.annyeongdrive.app.vendor.AdminCodeChangeRequest
import com.annyeongdrive.app.vendor.AdminCodeCheck
import com.annyeongdrive.app.vendor.HiPassAvailability
import com.annyeongdrive.app.vendor.ReturnTerms
import com.annyeongdrive.app.vendor.VehicleInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/**
 * 업체 설정 흐름: 처음 코드 정하기, 코드 입력 후 진입, 편집·저장, 코드 변경, 닫기, 출발 시 닫힘, 파일 손상 시 차단.
 * 업체 설정에서 정한 값이 반납·예비 초기화 흐름까지 이어지는지도 확인한다. 실제 DataStore 파일·엔진·Coordinator를 연결한다.
 * 근거: 결정 문서 5장, docs/state-machine.md B9·B10·T9·T4·T5, implementation-plan 4단계.
 */
class VendorSettingsFlowTest {

    private val stores = TempDataStores()
    private val writeScope = CoroutineScope(SupervisorJob())

    /** 파일 쓰기 범위를 닫고 임시 파일을 지운다. */
    @After
    fun tearDown() {
        writeScope.cancel()
        stores.delete()
    }

    /**
     * 앱을 켜고 운전석에 앉아 정차 대기에서 업체 설정을 연다.
     *
     * @param adminCode 지난 실행에서 정해 둔 관리자 코드. 처음 설치면 null
     * @return 업체 설정 화면을 연 앱
     */
    private suspend fun openSettings(adminCode: String? = ADMIN_CODE): AdminTestHarness {
        AdminTestHarness.seedPreviousRun(stores, adminCode = adminCode)
        val app = AdminTestHarness(stores, writeScope)
        app.startAndSit()
        app.engine.send(DriveEvent.VendorSettingsRequested)
        app.vendorFlow.open()
        return app
    }

    /**
     * 업체 설정을 열고 코드를 넣어 편집 단계까지 간다.
     *
     * @return 편집 단계의 앱
     */
    private suspend fun editing(): AdminTestHarness {
        val app = openSettings()
        app.vendorFlow.enterCode(ADMIN_CODE)
        return app
    }

    /**
     * 같은 저장 파일로 앱을 다시 켠다.
     *
     * @param app 지금 앱
     * @return 다시 켠 앱(아직 시작 전)
     */
    private suspend fun restart(app: AdminTestHarness): AdminTestHarness {
        app.awaitWrites()
        stores.close()
        return AdminTestHarness(stores, writeScope, app.clock)
    }

    /** 처음 설치한 기기에서 업체 설정을 열면 관리자 코드부터 정하는지 확인한다. */
    @Test
    fun `처음 설치한 기기에서 업체 설정을 열면 관리자 코드부터 정한다`() = runBlocking {
        // 상황
        val app = openSettings(adminCode = null)
        // 행동
        val screen = app.vendorFlow.screen.value
        // 결과
        assertEquals(VendorSettingsScreen.FirstCodeSetup(notice = null), screen)
    }

    /** 처음 코드를 두 번 다르게 입력하면 정하지 않는지 확인한다. */
    @Test
    fun `처음 코드를 두 번 다르게 입력하면 정하지 않는다`() = runBlocking {
        // 상황
        val app = openSettings(adminCode = null)
        // 행동
        app.vendorFlow.setFirstCode("Rent2026", "Rent2027")
        // 결과
        assertEquals(VendorSettingsScreen.FirstCodeSetup(CodeNotice.ConfirmMismatch), app.vendorFlow.screen.value)
        assertTrue(!app.vendorConfigs.config.value.hasAdminCode())
    }

    /** 업체 설정에서 처음 정한 코드로 반납까지 할 수 있는지 확인한다(업체 설정 → 반납 흐름 연결). */
    @Test
    fun `처음 정한 관리자 코드로 반납할 수 있다`() = runBlocking {
        // 상황
        val app = openSettings(adminCode = null)
        app.vendorFlow.setFirstCode("Shop2026", "shop2026")
        app.vendorFlow.close()
        app.engine.send(DriveEvent.ReturnRequested)
        app.returnFlow.open()
        // 행동
        app.returnFlow.submit("SHOP2026")
        // 결과
        assertSame(Idle, app.engine.state.value)
    }

    /** 관리자 코드를 정한 뒤에는 코드를 입력해야 편집할 수 있는지 확인한다. */
    @Test
    fun `관리자 코드를 정한 뒤에는 코드를 입력해야 편집할 수 있다`() = runBlocking {
        // 상황
        val app = openSettings()
        // 행동
        app.vendorFlow.editContact("010-0000-0000")
        // 결과
        assertEquals(VendorSettingsScreen.CodeEntry(notice = null), app.vendorFlow.screen.value)
    }

    /** 업체 설정에서 5번 틀려 잠기면 반납 처리도 같은 잠금을 따르는지 확인한다(같은 규칙). */
    @Test
    fun `업체 설정에서 잠기면 반납 처리도 잠긴다`() = runBlocking {
        // 상황
        val app = openSettings()
        repeat(5) { app.vendorFlow.enterCode("Wrong123") }
        app.vendorFlow.close()
        app.engine.send(DriveEvent.ReturnRequested)
        app.returnFlow.open()
        // 행동
        app.returnFlow.submit(ADMIN_CODE)
        // 결과
        assertEquals(ReturnScreenState.Locked(NOW.plusSeconds(30)), app.returnFlow.screen.value)
        assertSame(Returning, app.engine.state.value)
    }

    /** 저장 버튼을 누르기 전에는 바뀐 항목이 저장되지 않는지 확인한다(결정 5). */
    @Test
    fun `저장 버튼을 누르기 전에는 바뀐 항목이 저장되지 않는다`() = runBlocking {
        // 상황
        val app = editing()
        // 행동
        app.vendorFlow.editVehicle(VehicleInfo(FuelType.DIESEL, HiPassAvailability.AVAILABLE, null))
        // 결과
        assertEquals(FuelType.UNKNOWN, app.vendorConfigs.config.value.vehicle.fuelType)
        assertTrue((app.vendorFlow.screen.value as VendorSettingsScreen.Editing).unsaved)
    }

    /** 저장하면 앱을 다시 켜도 바뀐 항목이 남는지 확인한다. */
    @Test
    fun `저장하면 앱을 다시 켜도 바뀐 항목이 남는다`() = runBlocking {
        // 상황
        val first = editing()
        first.vendorFlow.editVehicle(VehicleInfo(FuelType.DIESEL, HiPassAvailability.AVAILABLE, "반납 시 정산"))
        first.vendorFlow.editContact("02-000-0000")
        // 행동
        first.vendorFlow.save()
        val app = restart(first)
        app.startup.start()
        // 결과
        assertEquals(FuelType.DIESEL, app.vendorConfigs.config.value.vehicle.fuelType)
        assertEquals("02-000-0000", app.vendorConfigs.config.value.contact)
    }

    /** 편집 중 출발하면 업체 설정이 닫히고 저장하지 않은 변경은 버려지는지 확인한다(T9, 결정 5). */
    @Test
    fun `편집 중 출발하면 업체 설정이 닫히고 저장하지 않은 변경은 버린다`() = runBlocking {
        // 상황
        val app = editing()
        app.vendorFlow.editContact("010-1111-1111")
        // 행동
        app.signals.gear.shift(Gear.D)
        app.vendorFlow.syncWithDrive()
        app.vendorFlow.save()
        // 결과
        assertEquals(VendorSettingsScreen.Closed, app.vendorFlow.screen.value)
        assertNull(app.vendorConfigs.config.value.contact)
    }

    /** 닫으면 정차 대기로 돌아가고 저장하지 않은 변경은 버려지는지 확인한다(B10). */
    @Test
    fun `닫으면 정차 대기로 돌아가고 저장하지 않은 변경은 버린다`() = runBlocking {
        // 상황
        val app = editing()
        app.vendorFlow.editContact("010-2222-2222")
        // 행동
        app.vendorFlow.close()
        // 결과
        assertSame(ParkedRest, app.engine.state.value)
        assertNull(app.vendorConfigs.config.value.contact)
    }

    /** 관리자 코드를 바꾸면 새 코드로만 반납할 수 있는지 확인한다. */
    @Test
    fun `관리자 코드를 바꾸면 새 코드로만 반납할 수 있다`() = runBlocking {
        // 상황
        val app = editing()
        app.vendorFlow.changeCode(AdminCodeChangeRequest(ADMIN_CODE, "NewCode9"), "NewCode9")
        app.vendorFlow.close()
        app.engine.send(DriveEvent.ReturnRequested)
        app.returnFlow.open()
        // 행동
        app.returnFlow.submit(ADMIN_CODE)
        val withOld = app.returnFlow.screen.value
        app.returnFlow.submit("NewCode9")
        // 결과
        assertEquals(ReturnScreenState.WrongCode(remainingAttempts = 4), withOld)
        assertSame(Idle, app.engine.state.value)
    }

    /** 지금 코드가 틀리면 코드를 바꾸지 않고 실패로 세는지 확인한다. */
    @Test
    fun `지금 코드가 틀리면 관리자 코드를 바꾸지 않는다`() = runBlocking {
        // 상황
        val app = editing()
        // 행동
        app.vendorFlow.changeCode(AdminCodeChangeRequest("Wrong123", "NewCode9"), "NewCode9")
        // 결과
        val screen = app.vendorFlow.screen.value as VendorSettingsScreen.Editing
        assertEquals(CodeNotice.Wrong(remainingAttempts = 4), screen.codeNotice)
        assertTrue(acceptsCode(app, ADMIN_CODE))
        assertTrue(!acceptsCode(app, "NewCode9"))
    }

    /** 업체 설정 파일을 읽지 못하면 업체 설정에 들어갈 수 없는지 확인한다. */
    @Test
    fun `업체 설정 파일을 읽지 못하면 업체 설정에 들어갈 수 없다`() = runBlocking {
        // 상황
        AdminTestHarness.seedPreviousRun(stores)
        stores.fileOf(AdminTestHarness.VENDOR).writeBytes(byteArrayOf(9, 9, 9))
        val app = AdminTestHarness(stores, writeScope)
        app.startAndSit()
        app.engine.send(DriveEvent.VendorSettingsRequested)
        // 행동
        app.vendorFlow.open()
        app.vendorFlow.setFirstCode("Hack2026", "Hack2026")
        // 결과
        assertEquals(VendorSettingsScreen.ConfigUnreadable, app.vendorFlow.screen.value)
        assertEquals(3, stores.fileOf(AdminTestHarness.VENDOR).length())
    }

    /** 업체 설정에서 저장한 반납 예정 시각 + N이 지나면 다음 앱 시작 때 예비 초기화되는지 확인한다(T4 → T5). */
    @Test
    fun `저장한 반납 예정 시각과 N이 지나면 다음 앱 시작 때 예비 초기화된다`() = runBlocking {
        // 상황
        val first = editing()
        first.vendorFlow.editReturnTerms(ReturnTerms(place = "본점", dueAt = NOW, energyRule = null))
        first.vendorFlow.editResetGrace(Duration.ofHours(2))
        first.vendorFlow.save()
        val app = restart(first)
        app.clock.advance(Duration.ofHours(2))
        // 행동
        app.startup.start()
        // 결과
        assertNull(app.driverSessions.session.value)
        assertSame(Idle, app.engine.state.value)
    }

    /**
     * 저장된 관리자 코드가 주어진 코드로 확인되는지 본다. 저장하지 않고 Aggregate로 직접 확인한다.
     *
     * @param app 앱
     * @param code 확인할 코드
     * @return 맞으면 true
     */
    private fun acceptsCode(app: AdminTestHarness, code: String): Boolean =
        app.vendorConfigs.config.value.verifyAdminCode(code, NOW).check == AdminCodeCheck.Verified
}
