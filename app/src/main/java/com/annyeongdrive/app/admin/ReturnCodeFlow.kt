package com.annyeongdrive.app.admin

import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.drive.state.Returning
import com.annyeongdrive.app.service.DriveEngine
import com.annyeongdrive.app.vendor.AdminCodeCheck
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/** 반납 처리 화면이 보여 줄 것. */
sealed interface ReturnScreenState {

    /** 관리자 코드를 입력받는다. */
    data object Entry : ReturnScreenState

    /**
     * 코드가 틀렸다.
     *
     * @property remainingAttempts 잠기기 전까지 남은 횟수
     */
    data class WrongCode(val remainingAttempts: Int) : ReturnScreenState

    /**
     * 잠겨 있다.
     *
     * @property until 잠금이 풀리는 시각
     */
    data class Locked(val until: Instant) : ReturnScreenState

    /** 관리자 코드를 아직 정하지 않았다. 업체 설정에서 먼저 정하라고 안내하고 반납은 진행하지 않는다. */
    data object AdminCodeNotSet : ReturnScreenState

    /** 업체 설정 파일을 읽지 못해 반납을 확인할 수 없다. 업체에 문의하라고 안내한다. */
    data object ConfigUnreadable : ReturnScreenState
}

/**
 * 반납 처리 화면의 흐름. 운전 상태가 반납 처리(Returning)일 때 관리자 코드를 확인하고,
 * 맞으면 AdminCodeVerified를 보내 초기화(T32 → T5)로 넘긴다. 상태 전이 판단은 Coordinator가 한다.
 * 상태 머신: T32, B3 (들어옴 T31·B8)
 *
 * @param gate 관리자 코드 확인 창구
 * @param engine 입력 → Coordinator → 출력 연결
 */
class ReturnCodeFlow(
    private val gate: AdminCodeGate,
    private val engine: DriveEngine,
) {

    private val current = MutableStateFlow<ReturnScreenState>(ReturnScreenState.Entry)

    /** 반납 처리 화면이 그릴 상태. */
    val screen: StateFlow<ReturnScreenState> = current.asStateFlow()

    /** 반납 처리 화면이 열릴 때 부른다. 코드를 입력받을 수 있는지에 따라 첫 화면을 정한다. */
    fun open() {
        current.value = firstScreen()
    }

    /**
     * 입력한 코드를 확인한다. 맞으면 AdminCodeVerified를 보낸다.
     * 확인하는 사이 출발해 상태가 바뀌었다면 Coordinator가 그 이벤트를 무시하고 데이터는 남는다.
     * 반납 처리 상태가 아닐 때(이미 지난 화면에서 누른 경우)는 아무것도 하지 않는다.
     *
     * @param code 입력한 코드
     */
    suspend fun submit(code: String) {
        if (engine.state.value != Returning) return
        val result = gate.verify(code)
        current.value = screenAfter(result)
        if (result == AdminGateResult.Checked(AdminCodeCheck.Verified)) engine.send(DriveEvent.AdminCodeVerified) // T32
    }

    /** 반납 처리를 취소하고 정차 대기로 돌아간다. 상태 머신: B3 */
    fun cancel() = engine.send(DriveEvent.ReturnCancelled) // B3

    /**
     * 반납 처리 화면을 처음 열었을 때 보여 줄 상태를 정한다.
     *
     * @return 읽지 못함·코드 미설정·입력 중 하나
     */
    private fun firstScreen(): ReturnScreenState = when {
        !gate.isAvailable() -> ReturnScreenState.ConfigUnreadable
        !gate.hasAdminCode() -> ReturnScreenState.AdminCodeNotSet
        else -> ReturnScreenState.Entry
    }

    /**
     * 확인 결과를 화면 상태로 바꾼다.
     *
     * @param result 확인 결과
     * @return 보여 줄 화면 상태
     */
    private fun screenAfter(result: AdminGateResult): ReturnScreenState = when (result) {
        AdminGateResult.ConfigUnreadable -> ReturnScreenState.ConfigUnreadable
        is AdminGateResult.Checked -> screenAfter(result.check)
    }

    /**
     * 코드 확인 결과를 화면 상태로 바꾼다.
     *
     * @param check 코드 확인 결과
     * @return 보여 줄 화면 상태
     */
    private fun screenAfter(check: AdminCodeCheck): ReturnScreenState = when (check) {
        AdminCodeCheck.Verified -> ReturnScreenState.Entry
        is AdminCodeCheck.Wrong -> ReturnScreenState.WrongCode(check.remainingAttempts)
        is AdminCodeCheck.Locked -> ReturnScreenState.Locked(check.until)
        AdminCodeCheck.NotSet -> ReturnScreenState.AdminCodeNotSet
    }
}
