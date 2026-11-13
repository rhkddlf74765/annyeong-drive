package com.annyeongdrive.app.learning

import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.drive.state.Learning
import com.annyeongdrive.app.drive.state.LearningCard
import com.annyeongdrive.app.drive.state.LearningExplain
import com.annyeongdrive.app.drive.state.LearningQuiz
import com.annyeongdrive.app.drive.state.LearningResult
import com.annyeongdrive.app.rental.LearningProgress
import com.annyeongdrive.app.rental.LearningScreen
import com.annyeongdrive.app.rental.RentalSession
import com.annyeongdrive.app.service.DriveEngine
import com.annyeongdrive.app.storage.DriverSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 교육·퀴즈 화면 안쪽 상태. 화면은 이것만 받아 그린다. */
sealed interface LearningView {

    /** 교육·퀴즈 화면이 아니다. */
    data object Hidden : LearningView

    /**
     * 학습 카드.
     *
     * @property card 보여 줄 카드
     * @property number 몇 번째 카드인지(1부터)
     * @property total 카드 수
     */
    data class Card(val card: PlannedCard, val number: Int, val total: Int) : LearningView {

        /** 이전 카드가 있는지. */
        val hasPrevious: Boolean get() = number > 1

        /** 마지막 카드인지. 마지막 카드에서 핵심 퀴즈를 시작한다. */
        val isLast: Boolean get() = number == total
    }

    /**
     * 문제. 정답을 확인하기 전까지는 고른 답을 바꿀 수 있다.
     *
     * @property question 문항
     * @property number 몇 번째 문항인지(1부터)
     * @property total 문항 수
     * @property selected 고른 선택지 위치. 아직 고르지 않았으면 null
     */
    data class Question(val question: PlannedQuestion, val number: Int, val total: Int, val selected: Int?) :
        LearningView {

        /** 답을 골라 정답 확인을 할 수 있는지. */
        val canCheck: Boolean get() = selected != null
    }

    /**
     * 해설. 확인한 답은 바꿀 수 없다.
     *
     * @property question 문항
     * @property number 몇 번째 문항인지(1부터)
     * @property total 문항 수
     * @property correct 정답이었는지
     */
    data class Explanation(val question: PlannedQuestion, val number: Int, val total: Int, val correct: Boolean) :
        LearningView

    /**
     * 결과·복습. 점수가 아니라 응답한 문항 수만 보여 준다.
     *
     * @property answered 응답한 문항 수({완료 수})
     * @property total 문항 수
     * @property reviewCards 다시 볼 규칙
     */
    data class Result(val answered: Int, val total: Int, val reviewCards: List<PlannedCard>) : LearningView
}

/**
 * 교육·퀴즈 화면 안쪽 흐름: 카드 넘기기 → 핵심 퀴즈 → 정답 확인 → 해설 → 다음 → 결과·복습.
 * 4·5단계처럼 진행을 먼저 저장하고 이벤트를 보낸다. 상태 머신은 화면 단계(카드·문제·해설·결과)만 알고,
 * 몇 번째 카드·문항인지와 고른 답은 이 객체가 맡는다. 콘텐츠·상황은 [LearningPlans]의 계획으로만 본다.
 * 상태 머신: T26, T27, T28, T29, T30, B6, T23 (들어옴 T25·B2)
 *
 * @param driverSessions 운전자 데이터 저장소
 * @param engine 입력 → Coordinator → 출력 연결
 * @param plans 이 대여의 교육 계획
 */
class LearningFlow(
    private val driverSessions: DriverSessionRepository,
    private val engine: DriveEngine,
    private val plans: LearningPlans,
) {

    private val current = MutableStateFlow<LearningView>(LearningView.Hidden)

    /** 교육·퀴즈 화면 안쪽 상태. */
    val view: StateFlow<LearningView> = current.asStateFlow()

    /**
     * 운전 상태에 맞춰 화면을 다시 맞춘다. 운전 상태가 바뀔 때마다 부른다(여러 번 불러도 같다).
     * 이어 보기(B2)로 들어오면 저장된 위치를, 이미 답한 문항이면 응답하지 않은 첫 문항을 보여 준다.
     */
    suspend fun sync() {
        when (engine.state.value) {
            LearningCard -> showSavedCard()
            LearningQuiz -> showSavedQuestion()
            LearningExplain -> showExplanation()
            LearningResult -> showResult()
            else -> current.value = LearningView.Hidden
        }
    }

    /** 다음 카드로 넘긴다. 마지막 카드면 아무것도 하지 않는다. */
    suspend fun nextCard() = moveCard(+1)

    /** 이전 카드로 돌아간다. 첫 카드면 아무것도 하지 않는다. */
    suspend fun previousCard() = moveCard(-1)

    /**
     * 핵심 퀴즈를 시작한다. 응답하지 않은 첫 문항으로 가고, 모두 응답했으면 새 회차로 처음부터 낸다(복습 목록은 남는다).
     * 상태 머신: T26
     */
    suspend fun startQuiz() {
        if (engine.state.value != LearningCard) return
        val order = plans.current().questionIds()
        val first = order.firstOrNull() ?: return
        val learning = progress() ?: return
        if (learning.firstUnanswered(order) == null) driverSessions.update { it?.restartLearning() }
        val target = progress()?.firstUnanswered(order) ?: first
        driverSessions.update { it?.showQuestion(target) }
        engine.send(DriveEvent.QuizStartRequested) // T26
        showSavedQuestion()
    }

    /**
     * 답을 고른다. 정답 확인 전에는 바꿀 수 있다.
     *
     * @param choice 고른 선택지 위치
     */
    fun selectAnswer(choice: Int) {
        val shown = current.value as? LearningView.Question ?: return
        if (engine.state.value != LearningQuiz) return
        current.value = shown.copy(selected = choice)
    }

    /**
     * 정답을 확인한다. 답을 고르지 않았으면 아무것도 하지 않는다. 답을 저장한 뒤 해설로 간다.
     * 상태 머신: T27
     */
    suspend fun checkAnswer() {
        if (engine.state.value != LearningQuiz) return
        val shown = current.value as? LearningView.Question ?: return
        val choice = shown.selected ?: return
        val id = shown.question.id
        driverSessions.update { it?.answerQuestion(id, shown.question.isCorrect(choice)) }
        engine.send(DriveEvent.AnswerChecked) // T27
        showExplanation()
    }

    /**
     * 문항을 건너뛴다. 미완료로 남기고 다음 문항으로, 뒤에 남은 문항이 없으면 결과로 간다.
     * 상태 머신: T28, B6
     */
    suspend fun skip() {
        if (engine.state.value != LearningQuiz) return
        val shown = current.value as? LearningView.Question ?: return
        driverSessions.update { it?.skipQuestion(shown.question.id) }
        engine.send(DriveEvent.QuestionSkipped) // T28, B6
        afterQuestion()
    }

    /**
     * 해설에서 다음으로 간다. 뒤에 남은 문항이 없으면 결과로 간다.
     * 상태 머신: T29, T30
     */
    suspend fun next() {
        if (engine.state.value != LearningExplain) return
        engine.send(DriveEvent.NextRequested) // T29, T30
        afterQuestion()
    }

    /** 교육을 닫는다. 상태 머신: T23 */
    fun close() {
        if (engine.state.value !is Learning) return
        engine.send(DriveEvent.LearningClosed) // T23
        current.value = LearningView.Hidden
    }

    /**
     * 문항을 마친 뒤(건너뛰기·다음) 상태 머신이 고른 곳에 맞춰 다음 문항 위치를 저장하거나 결과를 보여 준다.
     */
    private suspend fun afterQuestion() {
        if (engine.state.value != LearningQuiz) return sync()
        val order = plans.current().questionIds()
        val next = progress()?.nextUnansweredAfterPosition(order) ?: return sync()
        driverSessions.update { it?.showQuestion(next) }
        showSavedQuestion()
    }

    /**
     * 카드를 앞뒤로 옮기고 본 카드로 기록한다.
     *
     * @param step 옮길 칸 수(+1 다음, -1 이전)
     */
    private suspend fun moveCard(step: Int) {
        if (engine.state.value != LearningCard) return
        val shown = current.value as? LearningView.Card ?: return
        val cards = plans.current().cards
        val target = cards.getOrNull(shown.number - 1 + step) ?: return
        recordAndShowCard(target, cards)
    }

    /** 저장된 카드 위치(없으면 첫 카드)를 보여 준다. 같은 카드를 이미 보여 주고 있으면 그대로 둔다. */
    private suspend fun showSavedCard() {
        val cards = plans.current().cards
        val savedId = progress()?.attempt?.position?.takeIf { it.screen == LearningScreen.CARD }?.itemId
        val first = cards.firstOrNull() ?: return
        val target = cards.firstOrNull { it.id == savedId } ?: first
        val shown = current.value
        if (shown is LearningView.Card && shown.card.id == target.id) return
        recordAndShowCard(target, cards)
    }

    /**
     * 카드를 본 것으로 저장하고 보여 준다.
     *
     * @param card 보여 줄 카드
     * @param cards 계획의 전체 카드
     */
    private suspend fun recordAndShowCard(card: PlannedCard, cards: List<PlannedCard>) {
        driverSessions.update { it?.viewCard(card.id) }
        current.value = LearningView.Card(card, cards.indexOf(card) + 1, cards.size)
    }

    /**
     * 저장된 문항 위치를 보여 준다. 그 문항을 이미 답했거나 계획에 없으면 응답하지 않은 첫 문항으로 옮긴다.
     * 같은 문항을 이미 보여 주고 있으면 고른 답을 지키도록 그대로 둔다.
     */
    private suspend fun showSavedQuestion() {
        val plan = plans.current()
        val learning = progress() ?: return
        val savedId = learning.attempt.position?.itemId
        val saved = savedId?.let { plan.question(it) }?.takeIf { !learning.attempt.isAnswered(it.id) }
        val fallback = firstUnansweredQuestion(plan, learning)
        val target = saved ?: fallback
        if (target == null) return showExplanation()
        if (saved == null) driverSessions.update { it?.showQuestion(target.id) }
        val shown = current.value
        if (shown is LearningView.Question && shown.question.id == target.id) return
        current.value = LearningView.Question(target, plan.questions.indexOf(target) + 1, plan.questions.size, null)
    }

    /**
     * 응답하지 않은 첫 문항을 찾는다.
     *
     * @param plan 교육 계획
     * @param learning 교육 진행
     * @return 문항. 모두 응답했으면 null
     */
    private fun firstUnansweredQuestion(plan: LearningPlan, learning: LearningProgress): PlannedQuestion? {
        val id = learning.firstUnanswered(plan.questionIds()) ?: return null
        return plan.question(id)
    }

    /** 저장된 위치의 문항 해설을 보여 준다. 답한 기록이 없으면 보여 주지 않는다. */
    private fun showExplanation() {
        val plan = plans.current()
        val learning = progress() ?: return
        val id = learning.attempt.position?.itemId ?: return
        val question = plan.question(id) ?: return
        val correct = learning.answeredCorrectly(id) ?: return
        current.value = LearningView.Explanation(question, plan.questions.indexOf(question) + 1, plan.questions.size, correct)
    }

    /** 결과·복습을 보여 준다. */
    private fun showResult() {
        val plan = plans.current()
        val learning = progress() ?: return
        current.value = LearningView.Result(
            answered = learning.answeredCount(),
            total = plan.questions.size,
            reviewCards = plan.reviewCards(learning.reviewIds),
        )
    }

    /**
     * 지금 대여의 교육 진행.
     *
     * @return 교육 진행. 대여가 없으면 null
     */
    private fun progress(): LearningProgress? = driverSessions.session.value?.let(RentalSession::learning)
}
