package com.annyeongdrive.app.service

import com.annyeongdrive.app.storage.DriverSessionRepository
import com.annyeongdrive.app.storage.StorageFixtures
import com.annyeongdrive.app.storage.TempDataStores
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 운전자 데이터 지우기 순서: 메모리를 먼저 비우고 파일은 이어서 지운다.
 * 반납 직후 착좌 신호가 와도 Coordinator가 이전 손님의 대여를 읽지 않게 한다.
 * 근거: 결정 문서 3·5장(초기화), 상태 머신 T5 → 대기 → T2·T3 가드.
 */
class DriverDataEraserTest {

    private val stores = TempDataStores()

    /** 임시 파일을 지운다. */
    @After
    fun tearDown() = stores.delete()

    /** 지우기를 부른 순간, 파일 쓰기가 끝나기 전에도 메모리의 대여가 비는지 확인한다. */
    @Test
    fun `지우기를 시작하면 파일 저장이 끝나기 전에도 대여가 없다`() = runBlocking {
        // 상황
        val repository = DriverSessionRepository(stores.open("driver_session"), StorageFixtures.CONTENT_VERSION)
        repository.update { StorageFixtures.startedSession().completePrecheck() }
        val eraser = RepositoryDriverDataEraser(repository, this)
        // 행동
        eraser.erase()
        val inMemoryRightAfter = repository.session.value
        // 결과
        assertNull(inMemoryRightAfter)
    }
}
