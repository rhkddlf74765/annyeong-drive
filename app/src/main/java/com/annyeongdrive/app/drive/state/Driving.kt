package com.annyeongdrive.app.drive.state

import com.annyeongdrive.app.drive.DriveContext
import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.drive.Transition

/**
 * 주행(복합 상태). 시작 상태는 [DrivingCruising](T10)이다.
 * 기어 P + 차속 0이 되면 정차의 맥락 판단으로 간다(T11).
 */
sealed interface Driving : DriveState

/** 주행 — 일반 주행. 규칙 상기(자기 전이)는 8단계에서 붙인다. */
data object DrivingCruising : Driving {

    /**
     * [구현 예정] 일반 주행 중 이벤트를 처리한다.
     * implementation-plan.md 1단계 1-b에서 T11을 구현한다. 규칙 상기 자기 전이는 8단계에서 구현한다.
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? =
        TODO("구현 예정: 1-b T11")
}
