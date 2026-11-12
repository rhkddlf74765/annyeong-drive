package com.annyeongdrive.app.drive

import com.annyeongdrive.app.gear.Gear
import com.annyeongdrive.app.location.GeoPoint
import com.annyeongdrive.app.rental.AppLanguage
import com.annyeongdrive.app.vehicle.SeatOccupancy
import com.annyeongdrive.app.vehicle.UxRestriction

/**
 * DriveCoordinator가 받는 입력 하나. 입력 어댑터와 화면의 사용자 동작이 만든다.
 * 이벤트는 사실만 담고 판단은 담지 않는다.
 * 각 이벤트의 "상태 머신:" 줄은 이 이벤트가 계기가 되는 흐름 코드다(docs/state-machine.md 흐름 코드 표).
 */
sealed interface DriveEvent {

    // ── 차량·위치 신호 ──

    /**
     * 기어가 바뀌었다.
     * 상태 머신: T9, T11, B4
     *
     * @property gear 바뀐 기어
     */
    data class GearChanged(val gear: Gear) : DriveEvent

    /**
     * 차속이 바뀌었다.
     * 상태 머신: T9, T11, B4
     *
     * @property kmh 차속(km/h)
     */
    data class SpeedChanged(val kmh: Float) : DriveEvent

    /**
     * 주행 중 화면 제한이 바뀌었다. 교육 진입 조건의 일부이기도 하다.
     * 상태 머신: B7
     *
     * @property restriction 바뀐 제한 상태
     */
    data class UxRestrictionChanged(val restriction: UxRestriction) : DriveEvent

    /**
     * 운전석 착좌가 바뀌었다. 착좌 해제는 어떤 전이도 만들지 않는다.
     * 상태 머신: T2, T3
     *
     * @property occupancy 바뀐 착좌 상태
     */
    data class DriverSeatChanged(val occupancy: SeatOccupancy) : DriveEvent

    /**
     * 위치가 바뀌었다. 정차 순간 맥락 판단에 쓰고, 주행 중 규칙 상기는 8단계에서 붙인다.
     * 상태 머신: T13, T14 (가드 정보)
     *
     * @property point 현재 위치
     */
    data class LocationChanged(val point: GeoPoint) : DriveEvent

    // ── 앱 ──

    /**
     * 앱이 시작됐다. 예비 초기화를 확인하는 계기다.
     * 상태 머신: T4
     */
    data object AppStarted : DriveEvent

    // ── 사용자 동작: 대여 설정 ──

    /**
     * 언어를 골랐다.
     * 상태 머신: T7
     *
     * @property language 고른 언어
     */
    data class LanguageSelected(val language: AppLanguage) : DriveEvent

    /**
     * 출발 전 점검을 마쳤다.
     * 상태 머신: T8
     */
    data object PrecheckCompleted : DriveEvent

    // ── 사용자 동작: 정차 ──

    /**
     * 주정차 경고 또는 오주유 안내를 확인했다.
     * 상태 머신: T17, T18
     */
    data object WarningAcknowledged : DriveEvent

    /**
     * 이어 보기 제안에서 "이어 보기"를 눌렀다.
     * 상태 머신: T19, B2
     */
    data object ResumeAccepted : DriveEvent

    /**
     * 이어 보기 제안에서 "나중에 보기"를 눌렀다.
     * 상태 머신: T20
     */
    data object ResumePostponed : DriveEvent

    /**
     * 정차 대기에서 교육·퀴즈를 열었다.
     * 상태 머신: T21
     */
    data object LearningOpenRequested : DriveEvent

    /**
     * 정차 대기에서 도움 메뉴를 골랐다.
     * 상태 머신: T22
     */
    data object HelpRequested : DriveEvent

    /**
     * 수동 도움 카드를 닫았다.
     * 상태 머신: B5
     */
    data object HelpClosed : DriveEvent

    // ── 사용자 동작: 교육·퀴즈 ──

    /**
     * 교육·퀴즈를 완료하거나 닫았다. 교육 시작 화면의 "나중에 보기"도 여기에 해당한다.
     * 상태 머신: T23
     */
    data object LearningClosed : DriveEvent

    /**
     * 교육 시작 화면에서 "교육 시작"을 눌렀다.
     * 상태 머신: T25
     */
    data object LearningBegin : DriveEvent

    /**
     * 학습 카드에서 "핵심 퀴즈 시작"을 눌렀다.
     * 상태 머신: T26
     */
    data object QuizStartRequested : DriveEvent

    /**
     * 문제에서 "정답 확인"을 눌렀다.
     * 상태 머신: T27
     */
    data object AnswerChecked : DriveEvent

    /**
     * 문제에서 "이번 문제 건너뛰기"를 눌렀다.
     * 상태 머신: T28, B6
     */
    data object QuestionSkipped : DriveEvent

    /**
     * 해설에서 "다음 문제" 또는 "학습 결과 보기"를 눌렀다.
     * 상태 머신: T29, T30
     */
    data object NextRequested : DriveEvent

    // ── 사용자 동작: 반납 ──

    /**
     * 정차 중 "반납 완료"를 눌렀다.
     * 상태 머신: T31 (→ B8)
     */
    data object ReturnRequested : DriveEvent

    /**
     * 관리자 코드 입력을 취소했다.
     * 상태 머신: B3
     */
    data object ReturnCancelled : DriveEvent

    /**
     * 관리자 코드가 맞다고 확인됐다. 검증과 잠금은 VendorConfig가 한다.
     * 상태 머신: T32
     */
    data object AdminCodeVerified : DriveEvent
}
