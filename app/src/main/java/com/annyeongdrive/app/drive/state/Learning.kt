package com.annyeongdrive.app.drive.state

import com.annyeongdrive.app.drive.DriveAction
import com.annyeongdrive.app.drive.DriveContext
import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.drive.LearningResumePoint
import com.annyeongdrive.app.drive.Transition

/**
 * 정차 — 교육·퀴즈(복합 상태). 이어 보기는 저장된 위치로 들어간다.
 * 교육·퀴즈 안 어디서든 완료·닫기면 정차 대기로, UX 제한이 생기면 화면·음성을 닫고 정차 대기로 간다.
 * 상태 머신: 들어옴 B1·T19·T21, 시작 상태 T24, 나감 T23·B7 (정차 공통 T9·T31)
 */
sealed interface Learning : Parked

/**
 * 교육·퀴즈 — 교육 시작. "나중에 보기"는 닫기로 처리한다.
 * 상태 머신: 들어옴 T24, 나감 T25
 */
data object LearningStart : Learning {

    /**
     * "교육 시작"을 누르면 학습 카드로 간다.
     * 상태 머신: T25 (부모 규칙 먼저)
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? =
        handleInLearning(event, context) ?: when (event) {
            DriveEvent.LearningBegin -> Transition(LearningCard) // T25
            else -> null
        }
}

/**
 * 교육·퀴즈 — 학습 카드. 카드 사이 이동(이전·다음 규칙)은 화면 안의 일이라 전이가 아니다.
 * 상태 머신: 들어옴 T25·B2, 나감 T26
 */
data object LearningCard : Learning {

    /**
     * "핵심 퀴즈 시작"을 누르면 문제로 간다.
     * 상태 머신: T26 (부모 규칙 먼저)
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? =
        handleInLearning(event, context) ?: when (event) {
            DriveEvent.QuizStartRequested -> Transition(LearningQuiz) // T26
            else -> null
        }
}

/**
 * 교육·퀴즈 — 문제.
 * 상태 머신: 들어옴 T26·T28·T29·B2, 나감 T27·T28·B6
 */
data object LearningQuiz : Learning {

    /**
     * 정답 확인이면 해설로, 건너뛰기면 다음 문제 또는 마지막 문항이면 결과·복습으로 간다.
     * 상태 머신: T27, T28, B6 (부모 규칙 먼저)
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? =
        handleInLearning(event, context) ?: when (event) {
            DriveEvent.AnswerChecked -> Transition(LearningExplain) // T27
            DriveEvent.QuestionSkipped -> toNextQuestionOrResult(context) // T28, B6
            else -> null
        }
}

/**
 * 교육·퀴즈 — 해설.
 * 상태 머신: 들어옴 T27, 나감 T29·T30
 */
data object LearningExplain : Learning {

    /**
     * 다음을 누르면 다음 문제 또는 마지막 문항이면 결과·복습으로 간다.
     * 상태 머신: T29, T30 (부모 규칙 먼저)
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? =
        handleInLearning(event, context) ?: when (event) {
            DriveEvent.NextRequested -> toNextQuestionOrResult(context) // T29, T30
            else -> null
        }
}

/**
 * 교육·퀴즈 — 결과·복습. 나가는 길은 부모의 완료·닫기뿐이다.
 * 상태 머신: 들어옴 T30·B6, 나감 T23(부모 규칙)
 */
data object LearningResult : Learning {

    /**
     * 결과·복습 화면의 이벤트를 처리한다. 자기 전이는 없고 교육·퀴즈 공통 규칙만 따른다.
     * 상태 머신: T23, B7 (부모 규칙)
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? =
        handleInLearning(event, context)
}

/**
 * 교육 화면 닫기와 학습 음성 중단. 교육·퀴즈를 떠날 때 항상 함께 낸다.
 * 상태 머신: T9, T31, B7
 */
private val CLOSE_LEARNING = listOf(DriveAction.CloseLearningScreen, DriveAction.StopLearningSpeech)

/**
 * 저장된 이어 보기 위치에 해당하는 교육·퀴즈 상태를 돌려준다.
 * 상태 머신: B2
 *
 * @param point 저장된 위치
 * @return 들어갈 교육·퀴즈 상태
 */
internal fun learningStateAt(point: LearningResumePoint): Learning = when (point) {
    LearningResumePoint.CARD -> LearningCard // B2
    LearningResumePoint.QUIZ -> LearningQuiz // B2
}

/**
 * 교육·퀴즈 하위 상태들이 함께 따르는 규칙. 하위 상태의 규칙보다 먼저 본다.
 * 정차 공통 규칙으로 교육을 떠나면 교육 화면 닫기와 학습 음성 중단을 붙인다.
 * 그 밖에 UX 제한이면 닫고 정차 대기로, 완료·닫기면 음성을 멈추고 정차 대기로 간다.
 * 상태 머신: T9, T31(정차 공통), B7, T23
 *
 * @param event 받은 이벤트
 * @param context 판단 정보
 * @return 전이. 해당하지 않으면 null
 */
private fun handleInLearning(event: DriveEvent, context: DriveContext): Transition? {
    val leavingParked = handleInParked(event, context)
    if (leavingParked != null) return leavingParked.copy(actions = CLOSE_LEARNING + leavingParked.actions) // T9, T31
    return when {
        context.vehicle.isUxRestricted() -> Transition(ParkedRest, CLOSE_LEARNING) // B7
        event == DriveEvent.LearningClosed -> Transition(ParkedRest, listOf(DriveAction.StopLearningSpeech)) // T23
        else -> null
    }
}

/**
 * 지금 문제가 마지막 문항이면 결과·복습으로, 아니면 다음 문제로 간다.
 * 상태 머신: T28·T29(다음 문제), B6·T30(결과·복습)
 *
 * @param context 판단 정보
 * @return 다음 상태로 가는 전이
 */
private fun toNextQuestionOrResult(context: DriveContext): Transition = when {
    context.learning.isLastQuestion() -> Transition(LearningResult) // B6, T30
    else -> Transition(LearningQuiz) // T28, T29
}
