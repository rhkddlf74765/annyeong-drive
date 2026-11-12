package com.annyeongdrive.app.service

import android.content.Context
import com.annyeongdrive.app.speech.SpeechHub
import com.annyeongdrive.app.storage.DriverSessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * 교육 화면 닫기 창구. 교육·퀴즈 화면(6·7단계)이 생기기 전이라 닫을 창이 없다.
 * 화면은 운전 상태를 그대로 그리므로, 상태가 교육·퀴즈를 떠나면 화면도 바뀐다. 따로 띄운 창이 생기면 여기서 닫는다.
 */
class NoLearningScreen : LearningScreenPort {

    /** 닫을 창이 없어 아무것도 하지 않는다. */
    override fun close() = Unit
}

/**
 * Pleos TTS(SpeechHub)로 학습 음성을 멈추는 창구. 대기 중인 문장도 버린다.
 *
 * @param context 앱 Context
 */
class SpeechHubLearningSpeech(private val context: Context) : LearningSpeechPort {

    /** 읽는 중인 문장과 대기 중인 문장을 모두 멈춘다. */
    override fun stop() = SpeechHub.obtain(context).stopAll()
}

/**
 * 저장소로 운전자 데이터를 지우는 창구. 지우기를 바로 시작해(UNDISPATCHED) 부르는 순간 메모리가 비게 한다.
 * 파일 쓰기는 그 뒤에 이어서 끝난다. 상태 머신: T5
 *
 * @param repository 운전자 데이터 저장소
 * @param scope 파일 쓰기를 이어 갈 범위(앱 전체 범위)
 */
class RepositoryDriverDataEraser(
    private val repository: DriverSessionRepository,
    private val scope: CoroutineScope,
) : DriverDataEraser {

    /** 메모리의 대여를 바로 비우고 파일 지우기를 이어서 진행한다. */
    override fun erase() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { repository.clear() }
    }
}

/**
 * 주정차 경고·오주유 안내 창구. 문구와 heads-up 알림은 8단계에서 만든다.
 * 지금은 검수 구역 조회가 늘 "없음"(NoStopPlaces)이고 오주유 기능이 꺼져 있어 불리지 않는다.
 */
class PendingDriveNotifier : DriveNotifierPort {

    /**
     * [구현 예정] 검수된 주정차 구역에서 확인을 요청하는 경고를 음성과 heads-up 알림으로 낸다.
     * implementation-plan.md 8단계 "주정차 경고"에서 구현한다.
     *
     * @param zoneId 검수된 구역 식별자
     */
    override fun announceParkingWarning(zoneId: String): Unit = TODO("구현 예정: 8단계 주정차 경고 음성·알림")

    /**
     * [구현 예정] 오주유 방지 확인 안내를 낸다.
     * implementation-plan.md P1 "오주유 방지 뼈대"에서 구현한다.
     */
    override fun announceFuelCheck(): Unit = TODO("구현 예정: P1 오주유 방지 안내")
}
