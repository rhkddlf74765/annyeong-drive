package com.annyeongdrive.app.learning

import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.drive.state.ParkedResumeOffer
import com.annyeongdrive.app.service.DriveEngine
import com.annyeongdrive.app.storage.DriverSessionRepository

/**
 * "이어서 확인할까요?" 화면의 흐름: 이어 보기 / 처음부터 보기 / 나중에 보기.
 * "처음부터 보기"는 새 전이를 만들지 않는다. 새 회차를 먼저 저장한 뒤 이어 보기(T19·B2)로 보내면,
 * 새 회차에는 저장된 위치가 없어 첫 학습 카드로 들어간다(11/14 결정). 복습 목록과 본 카드는 남는다.
 * 이어서 확인할까요 상태가 아닐 때 들어온 입력은 무시한다.
 * 상태 머신: T19·B2, T20 (들어옴 T15)
 *
 * @param driverSessions 운전자 데이터 저장소
 * @param engine 입력 → Coordinator → 출력 연결
 */
class ResumeOfferFlow(
    private val driverSessions: DriverSessionRepository,
    private val engine: DriveEngine,
) {

    /** "이어 보기"를 누른다. 저장된 위치(카드·문제)로 들어간다. 교육 진입 조건이 아니면 Coordinator가 무시한다. */
    fun resume() {
        if (engine.state.value != ParkedResumeOffer) return
        engine.send(DriveEvent.ResumeAccepted) // T19, B2
    }

    /**
     * "처음부터 보기"를 누른다. 새 회차를 저장한 뒤 이어 보기로 들어가 첫 학습 카드를 본다.
     * 교육 진입 조건이 아니면 저장하지 않는다. 들어가지 못하는데 진행만 바뀌는 일을 막기 위해서다.
     */
    suspend fun restart() {
        if (engine.state.value != ParkedResumeOffer) return
        if (!engine.vehicle.value.canOpenLearning()) return
        driverSessions.update { it?.restartLearning() }
        engine.send(DriveEvent.ResumeAccepted) // T19, B2 (새 회차라 첫 카드)
    }

    /** "나중에 보기"를 누른다. 정차 대기로 가고, 다음 정차 때 다시 제안한다. */
    fun later() {
        if (engine.state.value != ParkedResumeOffer) return
        engine.send(DriveEvent.ResumePostponed) // T20
    }
}
