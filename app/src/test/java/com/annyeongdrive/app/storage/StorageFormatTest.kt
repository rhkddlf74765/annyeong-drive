package com.annyeongdrive.app.storage

import com.annyeongdrive.app.storage.StorageFixtures.ADMIN_CODE
import com.annyeongdrive.app.storage.StorageFixtures.NOW
import com.annyeongdrive.app.storage.StorageFixtures.SALT
import com.annyeongdrive.app.vendor.AdminCodeCheck
import com.annyeongdrive.app.vendor.ReturnTerms
import com.annyeongdrive.app.vendor.VendorConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 저장 형식(JSON): 왕복, 저장 구조 버전, 깨진 내용, 코드 원문 비저장.
 * 근거: 결정 문서 4장(schemaVersion·형식 규칙)·5장(해시와 salt만 저장).
 */
class StorageFormatTest {

    private val sessionCodec = DocumentCodec.forRentalSession()
    private val vendorCodec = DocumentCodec.forVendorConfig()

    /** 대여를 저장 문자열로 바꿨다가 되돌리면 같은 대여인지 확인한다. */
    @Test
    fun `대여를 저장했다가 읽으면 같은 대여다`() {
        // 상황
        val session = StorageFixtures.startedSession().completePrecheck().beginLearning()
            .answerQuestion("quiz.lane_direction", correct = false).skipQuestion("quiz.school_zone_stop")
        // 행동
        val restored = sessionCodec.decode(sessionCodec.encode(session))
        // 결과
        assertEquals(session, restored)
    }

    /** 저장 문자열에 저장 구조 버전과 ISO-8601 시각이 들어가는지 확인한다. */
    @Test
    fun `저장 문자열에는 저장 구조 버전과 ISO 시각이 들어간다`() {
        // 상황
        val session = StorageFixtures.startedSession()
        // 행동
        val text = sessionCodec.encode(session)
        // 결과
        assertTrue(text.contains("\"schemaVersion\":1"))
        assertTrue(text.contains("\"startedAt\":\"2026-11-12T09:00:00Z\""))
    }

    /** 저장 구조 버전이 다른 문서는 읽지 않는지 확인한다. */
    @Test
    fun `저장 구조 버전이 다르면 읽지 않는다`() {
        // 상황
        val text = sessionCodec.encode(StorageFixtures.startedSession()).replace("\"schemaVersion\":1", "\"schemaVersion\":99")
        // 행동
        val restored = sessionCodec.decode(text)
        // 결과
        assertNull(restored)
    }

    /** 깨진 내용은 예외 없이 읽지 않는지 확인한다. */
    @Test
    fun `깨진 내용은 읽지 않는다`() {
        // 상황
        val text = "{\"schemaVersion\":1,\"data\":{\"rentalId\":"
        // 행동
        val restored = sessionCodec.decode(text)
        // 결과
        assertNull(restored)
    }

    /** 모르는 항목이 섞여 있어도 아는 항목으로 읽는지 확인한다(나중 버전에서 항목이 늘어난 경우). */
    @Test
    fun `모르는 항목이 있어도 읽는다`() {
        // 상황
        val session = StorageFixtures.startedSession()
        val text = sessionCodec.encode(session).replace("\"schemaVersion\":1,", "\"schemaVersion\":1,\"extra\":true,")
        // 행동
        val restored = sessionCodec.decode(text)
        // 결과
        assertEquals(session, restored)
    }

    /** 업체 설정을 저장했다가 읽어도 같은 코드로 확인되는지 확인한다. */
    @Test
    fun `업체 설정을 저장했다가 읽어도 관리자 코드로 확인된다`() {
        // 상황
        val config = VendorConfig.initial().setInitialAdminCode(ADMIN_CODE, SALT)
            .withReturnTerms(ReturnTerms(place = "본점", dueAt = NOW))
        // 행동
        val restored = vendorCodec.decode(vendorCodec.encode(config))
        // 결과
        assertEquals(config, restored)
        assertEquals(AdminCodeCheck.Verified, restored?.verifyAdminCode(ADMIN_CODE, NOW)?.check)
    }

    /** 저장 문자열에 관리자 코드 원문이 들어가지 않는지 확인한다. */
    @Test
    fun `저장 문자열에는 관리자 코드 원문이 없다`() {
        // 상황
        val config = VendorConfig.initial().setInitialAdminCode(ADMIN_CODE, SALT)
        // 행동
        val text = vendorCodec.encode(config)
        // 결과
        assertFalse(text.contains(ADMIN_CODE, ignoreCase = true))
        assertTrue(text.contains("\"resetGrace\":\"PT24H\""))
    }
}
