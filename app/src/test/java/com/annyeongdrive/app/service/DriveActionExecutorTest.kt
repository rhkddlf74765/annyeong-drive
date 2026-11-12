package com.annyeongdrive.app.service

import com.annyeongdrive.app.drive.DriveAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 할 일 실행기: 할 일마다 정해진 출력 창구 하나만 부른다.
 * 근거: CLAUDE.md 아키텍처(DriveAction → 출력 실행기), 상태 머신 T5·T9·T13·T14·B7.
 */
class DriveActionExecutorTest {

    private val recorded = RecordingPorts()
    private val executor = DriveActionExecutor(recorded.asPorts())

    /** 교육 화면 닫기와 학습 음성 중단이 각각 화면·음성 창구로 가는지 확인한다(T9). */
    @Test
    fun `교육 화면 닫기와 학습 음성 중단은 화면과 음성 창구로 간다`() {
        // 상황
        val actions = listOf(DriveAction.CloseLearningScreen, DriveAction.StopLearningSpeech)
        // 행동
        executor.execute(actions)
        // 결과
        assertEquals(1, recorded.screenClosed)
        assertEquals(1, recorded.speechStopped)
    }

    /** 운전자 데이터 초기화가 지우기 창구로 가는지 확인한다(T5). */
    @Test
    fun `운전자 데이터 초기화는 지우기 창구로 간다`() {
        // 상황
        val actions = listOf(DriveAction.ResetDriverData)
        // 행동
        executor.execute(actions)
        // 결과
        assertEquals(1, recorded.driverDataErased)
    }

    /** 주정차 경고가 구역 식별자와 함께 알림 창구로 가는지 확인한다(T13). */
    @Test
    fun `주정차 경고는 구역 식별자와 함께 알림 창구로 간다`() {
        // 상황
        val actions = listOf(DriveAction.AnnounceParkingWarning("PZ1"))
        // 행동
        executor.execute(actions)
        // 결과
        assertEquals(listOf("PZ1"), recorded.parkingWarnings)
    }

    /** 할 일이 없으면 어떤 창구도 부르지 않는지 확인한다. */
    @Test
    fun `할 일이 없으면 아무 창구도 부르지 않는다`() {
        // 상황
        val actions = emptyList<DriveAction>()
        // 행동
        executor.execute(actions)
        // 결과
        assertTrue(recorded.nothingCalled())
    }
}
