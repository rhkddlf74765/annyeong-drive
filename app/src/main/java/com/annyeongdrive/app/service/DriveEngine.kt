package com.annyeongdrive.app.service

import com.annyeongdrive.app.drive.DriveCoordinator
import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.drive.state.DriveState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 입력 → Coordinator → 출력을 잇는다. 신호를 DriveEvent로 바꿔 넘기기만 하고 판단하지 않는다.
 * 처리 순서: Coordinator 처리 → 화면 상태 갱신 → 할 일 실행.
 * Android를 쓰지 않아 JVM에서 테스트한다. 메인 스레드 한 곳에서만 부른다고 가정한다.
 *
 * @param coordinator 판단 핵심
 * @param sources 구독할 입력 어댑터
 * @param executor 할 일 실행기
 */
class DriveEngine(
    private val coordinator: DriveCoordinator,
    private val sources: DriveSignalSources,
    private val executor: DriveActionExecutor,
) {

    private val current = MutableStateFlow(coordinator.state)
    private var running = false

    /** 현재 운전 상태. 화면은 이 값만 읽어 그린다. */
    val state: StateFlow<DriveState> = current.asStateFlow()

    /** 입력 신호 구독을 시작한다. 이미 시작했으면 아무것도 하지 않는다. */
    fun start() {
        if (running) return
        running = true
        sources.gear.start { dispatch(DriveEvent.GearChanged(it)) }
        sources.speed.start { dispatch(DriveEvent.SpeedChanged(it)) }
        sources.ux.start { dispatch(DriveEvent.UxRestrictionChanged(it)) }
        sources.driverSeat.start { dispatch(DriveEvent.DriverSeatChanged(it)) }
        sources.location.start { dispatch(DriveEvent.LocationChanged(it)) }
    }

    /** 입력 신호 구독을 멈춘다. 멈춘 뒤 들어오는 신호는 처리하지 않는다. */
    fun stop() {
        running = false
        sources.gear.stop()
        sources.speed.stop()
        sources.ux.stop()
        sources.driverSeat.stop()
        sources.location.stop()
    }

    /**
     * 화면의 사용자 동작이나 앱 이벤트를 넘긴다. 신호와 같은 흐름으로 처리한다.
     *
     * @param event 사용자 동작·앱 이벤트
     */
    fun send(event: DriveEvent) = dispatch(event)

    /**
     * 이벤트 하나를 Coordinator에 맡기고, 상태를 갱신한 뒤 할 일을 실행한다.
     *
     * @param event 처리할 이벤트
     */
    private fun dispatch(event: DriveEvent) {
        val actions = coordinator.handle(event)
        current.value = coordinator.state
        executor.execute(actions)
    }
}
