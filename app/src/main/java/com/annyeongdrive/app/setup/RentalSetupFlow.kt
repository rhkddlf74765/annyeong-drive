package com.annyeongdrive.app.setup

import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.drive.state.SetupLanguage
import com.annyeongdrive.app.drive.state.SetupPrecheck
import com.annyeongdrive.app.rental.AppLanguage
import com.annyeongdrive.app.rental.RentalSession
import com.annyeongdrive.app.service.DriveEngine
import com.annyeongdrive.app.storage.DriverSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 1차 제출에서 고를 수 있는 언어(결정 문서 1장). */
val SELECTABLE_LANGUAGES: List<AppLanguage> = listOf(AppLanguage.KOREAN, AppLanguage.ENGLISH)

/**
 * 출발 전 점검 화면 안쪽 상태.
 *
 * @property seatBeltConfirmed 운전자가 "모든 탑승자가 안전띠를 맸어요"를 체크했는지
 * @property needsSeatBeltConfirmation 체크하지 않고 마치려 해 안내가 필요한지
 */
data class PrecheckProgress(
    val seatBeltConfirmed: Boolean = false,
    val needsSeatBeltConfirmation: Boolean = false,
)

/**
 * 대여 설정 화면의 흐름: 언어 선택 → 출발 전 점검. 운전자 데이터는 RentalSession의 메서드로만 바꾸고,
 * 저장을 먼저 한 뒤 이벤트를 보내 Coordinator가 판단할 때 Reader가 새 값을 읽게 한다.
 * 점검 도중 앱을 다시 켜면 상태 머신대로 언어 선택부터 다시 하되, 이미 만든 대여는 언어만 바꿔 대여가 둘 생기지 않게 한다.
 * 상태 머신: T7, T8 (들어옴 T2·T6)
 *
 * @param driverSessions 운전자 데이터 저장소
 * @param engine 입력 → Coordinator → 출력 연결
 * @param starts 새 대여 시작 값
 */
class RentalSetupFlow(
    private val driverSessions: DriverSessionRepository,
    private val engine: DriveEngine,
    private val starts: RentalStarts,
) {

    private val current = MutableStateFlow(PrecheckProgress())

    /** 출발 전 점검 화면 안쪽 상태. */
    val precheck: StateFlow<PrecheckProgress> = current.asStateFlow()

    /**
     * 언어를 고른다. 대여가 없으면 새로 시작하고, 이미 있으면(점검 도중 재시작) 언어만 바꾼다. 저장한 뒤 T7로 넘긴다.
     * 언어 선택 상태가 아니면(지난 화면의 입력) 아무것도 하지 않는다.
     *
     * @param language 고른 언어
     */
    suspend fun selectLanguage(language: AppLanguage) {
        if (engine.state.value != SetupLanguage) return
        driverSessions.update { existing -> withLanguage(existing, language) }
        current.value = PrecheckProgress()
        engine.send(DriveEvent.LanguageSelected(language)) // T7
    }

    /**
     * 안전띠 수동 확인 체크를 바꾼다. 점검을 마치는 데 필요한 유일한 확인이다(11/13 결정).
     *
     * @param confirmed 체크했는지
     */
    fun confirmSeatBelts(confirmed: Boolean) {
        if (engine.state.value != SetupPrecheck) return
        current.value = PrecheckProgress(seatBeltConfirmed = confirmed)
    }

    /**
     * 출발 전 점검을 마친다. 안전띠 수동 확인이 없으면 마치지 않고 안내한다. 저장한 뒤 T8로 넘긴다.
     * 차량 경고가 없다는 것은 착용으로 보지 않으므로, 경고가 없어도 수동 확인은 꼭 필요하다.
     */
    suspend fun completePrecheck() {
        if (engine.state.value != SetupPrecheck) return
        if (driverSessions.session.value == null) return
        if (!current.value.seatBeltConfirmed) {
            current.value = current.value.copy(needsSeatBeltConfirmation = true)
            return
        }
        driverSessions.update { it?.completePrecheck() }
        current.value = PrecheckProgress()
        engine.send(DriveEvent.PrecheckCompleted) // T8
    }

    /**
     * 고른 언어를 대여에 반영한다.
     *
     * @param existing 지금 대여. 없으면 null
     * @param language 고른 언어
     * @return 언어가 반영된 대여
     */
    private fun withLanguage(existing: RentalSession?, language: AppLanguage): RentalSession {
        if (existing == null) return RentalSession.start(starts.request(language))
        return existing.changeLanguage(language)
    }
}
