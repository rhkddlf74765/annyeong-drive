package com.annyeongdrive.app.drive

import com.annyeongdrive.app.drive.state.DriveState
import com.annyeongdrive.app.drive.state.Idle

/**
 * 앱 전체 운전 상태를 하나로 관리한다. 이벤트를 받아 현재 상태에 맡기고, 다음 상태와 할 일 목록을 정한다.
 * docs/state-machine.md(원본 T1~T32 + 보완 B1~B7)를 따른다. Android API를 쓰지 않는다.
 * 메인 스레드 한 곳에서만 부른다고 가정한다.
 *
 * @param initialContext 처음 판단 정보. 보통 차량 신호는 [VehicleSnapshot.UNKNOWN]으로 시작한다
 */
class DriveCoordinator(initialContext: DriveContext) {

    /** 현재 상태. 화면은 이 값만 읽어 그린다. 처음은 대기(T1)다. */
    var state: DriveState = Idle
        private set

    /** 판단 정보. 차량 신호는 이벤트마다 갱신한다. */
    var context: DriveContext = initialContext
        private set

    /**
     * [구현 예정] 이벤트 하나를 처리하고 할 일 목록을 돌려준다.
     * 차량 신호를 갱신한 뒤 말단 상태 → 부모 복합 상태 순으로 맡기고, 거쳐 가는 상태는 이어서 해소한다.
     * implementation-plan.md 1단계 1-b에서 구현한다.
     *
     * @param event 받은 이벤트
     * @return 이번 이벤트로 낼 할 일. 전이가 없으면 빈 목록
     */
    fun handle(event: DriveEvent): List<DriveAction> = TODO("구현 예정: 1-b 이벤트 처리와 거쳐 가는 상태 해소")
}
