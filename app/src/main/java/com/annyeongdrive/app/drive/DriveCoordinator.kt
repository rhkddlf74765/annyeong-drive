package com.annyeongdrive.app.drive

import com.annyeongdrive.app.drive.state.DriveState
import com.annyeongdrive.app.drive.state.Driving
import com.annyeongdrive.app.drive.state.Idle
import com.annyeongdrive.app.drive.state.TransientState

/**
 * 앱 전체 운전 상태를 하나로 관리한다. 이벤트를 받아 현재 상태에 맡기고, 다음 상태와 할 일 목록을 정한다.
 * docs/state-machine.md(원본 T1~T32 + 보완 B1~B8)를 따른다. Android API를 쓰지 않는다.
 * 메인 스레드 한 곳에서만 부른다고 가정한다.
 *
 * @param initialContext 처음 판단 정보. 보통 차량 신호는 [VehicleSnapshot.UNKNOWN]으로 시작한다
 */
class DriveCoordinator(initialContext: DriveContext) {

    /**
     * 현재 상태. 화면은 이 값만 읽어 그린다. 처음은 대기다.
     * 상태 머신: T1
     */
    var state: DriveState = Idle // T1
        private set

    /** 판단 정보. 차량 신호는 이벤트마다 갱신한다. */
    var context: DriveContext = initialContext
        private set

    /**
     * 이벤트 하나를 처리하고 할 일 목록을 돌려준다.
     * 차량 신호를 먼저 갱신한 뒤 현재 상태에 맡긴다. 부모 복합 상태의 규칙은 각 상태가 위임한다.
     * 처리 뒤 주행 상태가 아니면 P로 바꾼 기억을 지운다(T13은 주행을 마친 그 정차에서 한 번만 판단).
     *
     * @param event 받은 이벤트
     * @return 이번 이벤트로 낼 할 일. 전이가 없으면 빈 목록
     */
    fun handle(event: DriveEvent): List<DriveAction> {
        context = context.copy(vehicle = context.vehicle.updatedBy(event))
        val transition = state.handle(event, context)
        val actions = if (transition == null) emptyList() else enter(transition)
        forgetParkShiftOutsideDriving()
        return actions
    }

    /**
     * 주행 상태가 아니면 P로 바꾼 기억을 지운다. 주행 중에는 차속이 0이 되어 정차할 때까지 남겨 둔다.
     * 그래서 반납 뒤 다음 손님의 점검 완료(T8)·착좌(T3)나 정차 중 N → P는 P로 바꾼 순간으로 보지 않는다.
     * 상태 머신: T13 가드(가드 해석 보완 11/14)
     */
    private fun forgetParkShiftOutsideDriving() {
        if (state is Driving) return
        context = context.copy(vehicle = context.vehicle.withoutParkShift())
    }

    /**
     * 전이를 적용한다. 다음 상태가 거쳐 가는 상태면 머무를 상태가 나올 때까지 이어서 해소한다.
     * 상태 머신: T5(초기화 → 대기), T12(정차 → 맥락 판단 → 분기)를 여기서 이어 간다
     *
     * @param transition 적용할 전이
     * @return 지나온 모든 전이의 할 일을 순서대로 모은 목록
     */
    private fun enter(transition: Transition): List<DriveAction> {
        val actions = transition.actions.toMutableList()
        var next = transition.next
        while (next is TransientState) {
            val resolved = next.resolve(context)
            actions += resolved.actions
            next = resolved.next
        }
        state = next
        return actions
    }
}
