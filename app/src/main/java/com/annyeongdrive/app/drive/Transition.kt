package com.annyeongdrive.app.drive

import com.annyeongdrive.app.drive.state.DriveState

/**
 * 이벤트 하나를 처리한 결과: 다음 상태와 그때 할 일 목록.
 *
 * @property next 다음 상태. 거쳐 가는 상태면 Coordinator가 이어서 해소한다
 * @property actions 이 전이에서 낼 할 일. 순서대로 실행한다
 */
data class Transition(
    val next: DriveState,
    val actions: List<DriveAction> = emptyList(),
)
