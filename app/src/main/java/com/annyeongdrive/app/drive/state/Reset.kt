package com.annyeongdrive.app.drive.state

import com.annyeongdrive.app.drive.DriveAction
import com.annyeongdrive.app.drive.DriveContext
import com.annyeongdrive.app.drive.Transition

/**
 * 초기화. 머무르지 않는다. 운전자 데이터를 지우고 대기로 돌아간다. 착좌 해제로는 들어오지 않는다.
 * 상태 머신: 들어옴 T4·T32, 나감 T5
 */
data object Reset : TransientState {

    /**
     * 운전자 데이터 초기화를 지시하고 대기로 간다.
     * 상태 머신: T5
     *
     * @param context 판단 정보
     * @return 대기로 가는 전이
     */
    override fun resolve(context: DriveContext): Transition =
        Transition(Idle, listOf(DriveAction.ResetDriverData)) // T5
}
