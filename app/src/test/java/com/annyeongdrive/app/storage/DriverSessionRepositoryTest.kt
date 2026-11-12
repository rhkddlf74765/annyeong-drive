package com.annyeongdrive.app.storage

import com.annyeongdrive.app.rental.LearningProgress
import com.annyeongdrive.app.storage.StorageFixtures.CONTENT_VERSION
import com.annyeongdrive.app.storage.StorageFixtures.NEW_CONTENT_VERSION
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 운전자 데이터 저장소(driver_session): 즉시 저장, 앱을 다시 켜도 유지, 문구 버전 변경, 지우기.
 * 근거: 결정 문서 4장(진행 저장, 즉시 저장, contentVersion), 상태 머신 T5.
 */
class DriverSessionRepositoryTest {

    private val stores = TempDataStores()

    /** 임시 파일을 지운다. */
    @After
    fun tearDown() = stores.delete()

    /**
     * driver_session 저장소를 연다.
     *
     * @param contentVersion 앱의 교육 문구 버전
     * @return 열린 저장소
     */
    private fun openRepository(contentVersion: String = CONTENT_VERSION) =
        DriverSessionRepository(stores.open(DRIVER_FILE), contentVersion)

    /** 저장한 대여가 앱을 껐다 켠 뒤에도 남아 있는지 확인한다. */
    @Test
    fun `저장한 대여는 앱을 다시 켜도 남아 있다`() = runBlocking {
        // 상황
        val saved = openRepository().update { StorageFixtures.startedSession().completePrecheck().beginLearning() }
        stores.close()
        // 행동
        val loaded = openRepository().load()
        // 결과
        assertEquals(saved, loaded)
    }

    /** 문항에 답하자마자 저장돼 바로 다시 열어도 기록이 있는지 확인한다. */
    @Test
    fun `답하면 바로 저장된다`() = runBlocking {
        // 상황
        val repository = openRepository()
        repository.update { StorageFixtures.startedSession().beginLearning() }
        // 행동
        repository.update { it?.skipQuestion("quiz.lane_direction") }
        stores.close()
        // 결과
        val loaded = openRepository().load()
        assertEquals(listOf("quiz.lane_direction"), loaded?.learning?.reviewIds)
    }

    /** 교육 문구 버전이 바뀐 앱으로 열면 대여는 남기고 교육 진행만 새로 시작하는지 확인한다. */
    @Test
    fun `교육 문구 버전이 바뀌면 교육 진행만 새로 시작한다`() = runBlocking {
        // 상황
        openRepository().update { StorageFixtures.startedSession().completePrecheck().beginLearning() }
        stores.close()
        // 행동
        val loaded = openRepository(NEW_CONTENT_VERSION).load()
        // 결과
        assertTrue(loaded?.hasRentalSetup() == true)
        assertEquals(LearningProgress.fresh(NEW_CONTENT_VERSION), loaded?.learning)
    }

    /** 운전자 데이터를 지우면 다시 켜도 대여가 없는지 확인한다(T5). */
    @Test
    fun `운전자 데이터를 지우면 다시 켜도 대여가 없다`() = runBlocking {
        // 상황
        val repository = openRepository()
        repository.update { StorageFixtures.startedSession() }
        // 행동
        repository.clear()
        stores.close()
        // 결과
        assertNull(repository.session.value)
        assertNull(openRepository().load())
    }

    private companion object {
        const val DRIVER_FILE = "driver_session"
    }
}
