package com.annyeongdrive.app.drive.state

import com.annyeongdrive.app.drive.DriveContext
import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.drive.Transition

/**
 * 대기. 앱 시작 직후와 초기화 뒤의 상태다. 운전자 데이터가 없거나 아직 착좌 전이다.
 * 나가는 전이: T2(착좌, 대여 설정 없음), T3(착좌, 대여 설정 있음), T4(앱 시작 시 예비 초기화).
 */
data object Idle : DriveState {

    /**
     * [구현 예정] 대기 상태에서 착좌·앱 시작 이벤트를 처리한다.
     * implementation-plan.md 1단계 1-b에서 T2·T3·T4를 구현한다.
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? =
        TODO("구현 예정: 1-b T2·T3·T4")
}
