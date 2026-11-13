package com.annyeongdrive.app.zones

import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.drive.state.ParkedNoParkingWarning
import com.annyeongdrive.app.service.DriveEngine

/**
 * 주정차 경고 화면의 흐름: 운전자가 경고를 확인하면 정차 대기로 간다.
 * 주정차 경고 상태가 아닐 때 들어온 입력은 무시한다(늦게 눌린 버튼이 다른 상태를 바꾸지 않게).
 * 상태 머신: T17 (들어옴 T13)
 *
 * @param engine 입력 → Coordinator → 출력 연결
 */
class ParkingWarningFlow(private val engine: DriveEngine) {

    /** "확인했어요"를 누른다. 정차 대기로 간다. */
    fun acknowledge() {
        if (engine.state.value != ParkedNoParkingWarning) return
        engine.send(DriveEvent.WarningAcknowledged) // T17
    }
}
