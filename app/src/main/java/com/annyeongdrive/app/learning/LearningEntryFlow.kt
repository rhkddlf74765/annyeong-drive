package com.annyeongdrive.app.learning

import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.drive.state.LearningStart
import com.annyeongdrive.app.service.DriveEngine
import com.annyeongdrive.app.storage.DriverSessionRepository

/**
 * 교육 시작 화면의 흐름: "교육 시작" 또는 "나중에 보기". 학습 카드·퀴즈는 6단계에서 붙인다.
 * "교육 시작"을 누르면 시작했다는 기록을 먼저 저장해, 다음 정차부터 교육 시작 화면(B1)이 다시 열리지 않게 한다.
 * 상태 머신: T25, T23 (들어옴 B1·T21·T24)
 *
 * @param driverSessions 운전자 데이터 저장소
 * @param engine 입력 → Coordinator → 출력 연결
 */
class LearningEntryFlow(
    private val driverSessions: DriverSessionRepository,
    private val engine: DriveEngine,
) {

    /** "교육 시작"을 누른다. 시작 기록을 저장한 뒤 학습 카드로 넘긴다. 교육 시작 상태가 아니면 아무것도 하지 않는다. */
    suspend fun begin() {
        if (engine.state.value != LearningStart) return
        driverSessions.update { it?.beginLearning() }
        engine.send(DriveEvent.LearningBegin) // T25
    }

    /** "나중에 보기"를 누른다. 정차 대기로 가고, 교육을 시작하지 않았으므로 다음 정차 때 다시 교육 시작이 열린다. */
    fun later() {
        if (engine.state.value != LearningStart) return
        engine.send(DriveEvent.LearningClosed) // T23
    }
}
