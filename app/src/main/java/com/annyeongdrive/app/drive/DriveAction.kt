package com.annyeongdrive.app.drive

import com.annyeongdrive.app.guide.RoadRuleReminder

/**
 * DriveCoordinator가 출력 실행기에 시키는 부수효과 하나.
 * 화면 전환은 할 일이 아니다. 화면은 현재 상태를 그대로 그린다.
 * 실행기는 같은 할 일을 여러 번 받아도 결과가 같게(멱등) 처리한다.
 */
sealed interface DriveAction {

    /**
     * 교육·퀴즈 화면을 팝업 없이 닫는다.
     * 상태 머신: T9, T31, B7 (교육·퀴즈를 떠날 때)
     */
    data object CloseLearningScreen : DriveAction

    /**
     * 학습 음성을 멈춘다. 교육 화면 닫기와 같은 전이에서 함께 내고, 사용자가 닫을 때도 낸다.
     * 상태 머신: T9, T31, B7, T23
     */
    data object StopLearningSpeech : DriveAction

    /**
     * 검수된 주정차 구역에서 확인을 요청하는 경고를 음성과 heads-up 알림으로 낸다.
     * 상태 머신: T13
     *
     * @property zoneId 검수된 구역 식별자
     */
    data class AnnounceParkingWarning(val zoneId: String) : DriveAction

    /**
     * 오주유 방지 확인 안내를 낸다. 기능 플래그가 켜졌을 때만 나온다.
     * 상태 머신: T14
     */
    data object AnnounceFuelCheck : DriveAction

    /**
     * 주행 중 규칙(보호구역·제한속도·단속 카메라)을 짧게 다시 알려 준다. 처음 만나면 2문장, 다시 만나면 1문장이다.
     * 상태 머신: B11
     *
     * @property reminder 알려 줄 규칙
     */
    data class RemindRoadRule(val reminder: RoadRuleReminder) : DriveAction

    /**
     * 운전자 데이터(driver_session)를 지운다. 업체 데이터는 남긴다.
     * 상태 머신: T5
     */
    data object ResetDriverData : DriveAction
}
