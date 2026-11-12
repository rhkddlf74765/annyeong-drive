package com.annyeongdrive.app.drive.state

import com.annyeongdrive.app.drive.DriveContext
import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.drive.Transition

/**
 * 앱 전체 운전 상태. docs/state-machine.md의 상태와 일대일로 대응한다.
 * 복합 상태는 sealed interface, 말단 상태는 데이터 없는 불변 객체(data object)다.
 * 화면은 현재 말단 상태를 그대로 그린다.
 */
sealed interface DriveState {

    /**
     * 이 상태에서 이벤트를 처리한다.
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 이 상태가 처리하지 않는 이벤트면 null을 돌려 부모 복합 상태에 맡긴다
     */
    fun handle(event: DriveEvent, context: DriveContext): Transition?
}

/**
 * 머무르지 않고 곧바로 다음 상태로 넘어가는 상태(초기화, 맥락 판단).
 * Coordinator는 이 상태에 들어가면 바로 [resolve]를 불러 다음 상태까지 진행한다.
 */
sealed interface TransientState : DriveState {

    /**
     * 거쳐 가는 상태는 이벤트를 받지 않는다.
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 항상 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? = null

    /**
     * 판단 정보만 보고 다음 상태와 할 일을 정한다.
     *
     * @param context 판단 정보
     * @return 다음 상태로 가는 전이
     */
    fun resolve(context: DriveContext): Transition
}
