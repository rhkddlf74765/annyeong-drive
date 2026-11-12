package com.annyeongdrive.app.drive.state

import com.annyeongdrive.app.drive.DriveContext
import com.annyeongdrive.app.drive.Transition

/**
 * 초기화. 머무르지 않는다. 운전자 데이터를 지우고 대기로 돌아간다(T5).
 * 들어오는 전이: T4(앱 시작 시 예비 초기화), T32(관리자 코드 확인). 착좌 해제로는 들어오지 않는다.
 */
data object Reset : TransientState {

    /**
     * [구현 예정] 운전자 데이터 초기화를 지시하고 대기로 간다.
     * implementation-plan.md 1단계 1-b에서 T5를 구현한다.
     *
     * @param context 판단 정보
     * @return 대기로 가는 전이
     */
    override fun resolve(context: DriveContext): Transition =
        TODO("구현 예정: 1-b T5")
}
