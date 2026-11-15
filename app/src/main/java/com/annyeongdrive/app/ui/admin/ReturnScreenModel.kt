package com.annyeongdrive.app.ui.admin

import com.annyeongdrive.app.R
import com.annyeongdrive.app.admin.ReturnScreenState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val LOCK_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

/** 잠금 해제 시각을 기기 시간대의 시:분:초로 바꾼다. */
internal fun lockTimeText(until: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    LOCK_TIME_FORMAT.format(until.atZone(zone))

/** 반납 안내 문구와 코드 입력 표시 여부. */
internal data class ReturnScreenModel(
    val message: Int,
    val showCodeEntry: Boolean,
    val messageArgs: List<Any> = emptyList(),
) {
    companion object {
        /** 반납 흐름이 준 상태에 따라 안내와 기존 입력 표시 여부를 정한다. */
        fun of(state: ReturnScreenState, zone: ZoneId): ReturnScreenModel = when (state) {
            ReturnScreenState.Entry -> ReturnScreenModel(R.string.return_entry, true)
            is ReturnScreenState.WrongCode -> ReturnScreenModel(R.string.code_wrong, true, listOf(state.remainingAttempts))
            is ReturnScreenState.Locked -> ReturnScreenModel(R.string.code_locked, true, listOf(lockTimeText(state.until, zone)))
            ReturnScreenState.AdminCodeNotSet -> ReturnScreenModel(R.string.return_code_not_set, false)
            ReturnScreenState.ConfigUnreadable -> ReturnScreenModel(R.string.config_unreadable, false)
        }
    }
}
