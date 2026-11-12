package com.annyeongdrive.app.drive.state

import com.annyeongdrive.app.drive.DriveContext
import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.drive.Transition

/**
 * 반납 처리. 정차 중 "반납 완료"로 들어오고(T31) 관리자 코드 입력 화면을 보여 준다.
 * 나가는 전이: T32(코드 확인 → 초기화), B3(취소 → 정차 대기), B4(기어 D/R 또는 차속 → 주행).
 * B4는 반납이 확정되기 전(코드 확인 전)의 출발이라 운전자 데이터를 지우지 않는다.
 */
data object Returning : DriveState {

    /**
     * 관리자 코드 확인·취소·주행 진입 이벤트를 처리한다. 주행 진입을 가장 먼저 본다.
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? = when {
        context.vehicle.hasDrivingSignal() -> enterDriving()
        event == DriveEvent.AdminCodeVerified -> Transition(Reset)
        event == DriveEvent.ReturnCancelled -> Transition(ParkedRest)
        else -> null
    }
}
