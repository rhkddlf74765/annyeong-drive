package com.annyeongdrive.app.service

import com.annyeongdrive.app.drive.DriveAction

/**
 * Coordinator가 낸 할 일을 출력 창구에 넘긴다. 판단하지 않고 할 일마다 정해진 창구 하나만 부른다.
 *
 * @param ports 출력 창구 묶음
 */
class DriveActionExecutor(private val ports: DriveOutputPorts) {

    /**
     * 할 일을 순서대로 실행한다.
     *
     * @param actions 할 일 목록
     */
    fun execute(actions: List<DriveAction>) {
        actions.forEach(::execute)
    }

    /**
     * 할 일 하나를 맞는 창구로 넘긴다.
     *
     * @param action 할 일
     */
    private fun execute(action: DriveAction) = when (action) {
        DriveAction.CloseLearningScreen -> ports.learningScreen.close()
        DriveAction.StopLearningSpeech -> ports.learningSpeech.stop()
        DriveAction.ResetDriverData -> ports.driverData.erase()
        is DriveAction.AnnounceParkingWarning -> ports.notifier.announceParkingWarning(action.zoneId)
        DriveAction.AnnounceFuelCheck -> ports.notifier.announceFuelCheck()
    }
}
