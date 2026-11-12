package com.annyeongdrive.app.drive.state

import com.annyeongdrive.app.drive.DriveAction
import com.annyeongdrive.app.drive.DriveContext
import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.drive.LearningResumePoint
import com.annyeongdrive.app.drive.Transition

/**
 * 정차 — 교육·퀴즈(복합 상태). 시작 상태는 [LearningStart](T24)이고, 이어 보기는 저장된 위치로 들어간다(B2).
 * 교육·퀴즈 안 어디서든 완료·닫기면 정차 대기로 간다(T23). UX 제한이 생기면 화면·음성을 닫고 정차 대기로 간다(B7).
 */
sealed interface Learning : Parked

/** 교육·퀴즈 — 교육 시작. "나중에 보기"는 닫기(T23)로 처리한다. */
data object LearningStart : Learning {

    /**
     * "교육 시작"을 누르면 학습 카드로 간다(T25).
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? =
        handleInLearning(event, context) ?: when (event) {
            DriveEvent.LearningBegin -> Transition(LearningCard)
            else -> null
        }
}

/** 교육·퀴즈 — 학습 카드. 카드 사이 이동(이전·다음 규칙)은 화면 안의 일이라 전이가 아니다. */
data object LearningCard : Learning {

    /**
     * "핵심 퀴즈 시작"을 누르면 문제로 간다(T26).
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? =
        handleInLearning(event, context) ?: when (event) {
            DriveEvent.QuizStartRequested -> Transition(LearningQuiz)
            else -> null
        }
}

/** 교육·퀴즈 — 문제. */
data object LearningQuiz : Learning {

    /**
     * 정답 확인이면 해설로(T27), 건너뛰기면 다음 문제(T28) 또는 마지막 문항이면 결과·복습(B6)으로 간다.
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? =
        handleInLearning(event, context) ?: when (event) {
            DriveEvent.AnswerChecked -> Transition(LearningExplain)
            DriveEvent.QuestionSkipped -> toNextQuestionOrResult(context)
            else -> null
        }
}

/** 교육·퀴즈 — 해설. */
data object LearningExplain : Learning {

    /**
     * 다음을 누르면 다음 문제(T29) 또는 마지막 문항이면 결과·복습(T30)으로 간다.
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? =
        handleInLearning(event, context) ?: when (event) {
            DriveEvent.NextRequested -> toNextQuestionOrResult(context)
            else -> null
        }
}

/** 교육·퀴즈 — 결과·복습. 나가는 길은 부모의 완료·닫기(T23)뿐이다. */
data object LearningResult : Learning {

    /**
     * 결과·복습 화면의 이벤트를 처리한다. 자기 전이는 없고 교육·퀴즈 공통 규칙만 따른다.
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? =
        handleInLearning(event, context)
}

/** 교육 화면 닫기와 학습 음성 중단. 교육·퀴즈를 떠날 때 항상 함께 낸다. */
private val CLOSE_LEARNING = listOf(DriveAction.CloseLearningScreen, DriveAction.StopLearningSpeech)

/**
 * 저장된 이어 보기 위치에 해당하는 교육·퀴즈 상태를 돌려준다(B2).
 *
 * @param point 저장된 위치
 * @return 들어갈 교육·퀴즈 상태
 */
internal fun learningStateAt(point: LearningResumePoint): Learning = when (point) {
    LearningResumePoint.CARD -> LearningCard
    LearningResumePoint.QUIZ -> LearningQuiz
}

/**
 * 교육·퀴즈 하위 상태들이 함께 따르는 규칙. 하위 상태의 규칙보다 먼저 본다.
 * 정차 공통 규칙(T9 주행 진입, T31 반납)으로 교육을 떠나면 교육 화면 닫기와 학습 음성 중단을 붙인다.
 * 그 밖에 UX 제한이면 닫고 정차 대기로(B7), 완료·닫기면 음성을 멈추고 정차 대기로(T23) 간다.
 *
 * @param event 받은 이벤트
 * @param context 판단 정보
 * @return 전이. 해당하지 않으면 null
 */
private fun handleInLearning(event: DriveEvent, context: DriveContext): Transition? {
    val leavingParked = handleInParked(event, context)
    if (leavingParked != null) return leavingParked.copy(actions = CLOSE_LEARNING + leavingParked.actions)
    return when {
        context.vehicle.isUxRestricted() -> Transition(ParkedRest, CLOSE_LEARNING)
        event == DriveEvent.LearningClosed -> Transition(ParkedRest, listOf(DriveAction.StopLearningSpeech))
        else -> null
    }
}

/**
 * 지금 문제가 마지막 문항이면 결과·복습으로, 아니면 다음 문제로 간다(T28·T29, B6·T30).
 *
 * @param context 판단 정보
 * @return 다음 상태로 가는 전이
 */
private fun toNextQuestionOrResult(context: DriveContext): Transition = when {
    context.learning.isLastQuestion() -> Transition(LearningResult)
    else -> Transition(LearningQuiz)
}
