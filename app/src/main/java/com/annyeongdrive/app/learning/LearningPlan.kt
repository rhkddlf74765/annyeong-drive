package com.annyeongdrive.app.learning

import com.annyeongdrive.app.rental.RentalSession
import com.annyeongdrive.app.vendor.VendorConfig
import kotlinx.coroutines.flow.StateFlow

/**
 * 이 대여에서 보여 줄 학습 카드 한 장. 상황에 맞는 변형이 이미 골라져 있다.
 *
 * @property id 카드 식별자
 * @property texts 고른 문구
 */
data class PlannedCard(val id: String, val texts: CardTexts)

/**
 * 이 대여에서 낼 문항 하나. 상황에 맞는 변형이 이미 골라져 있다.
 *
 * @property id 문항 식별자
 * @property texts 고른 문구
 * @property correctIndex 정답 선택지 위치
 * @property relatedCardId 다시 볼 규칙으로 보여 줄 카드
 */
data class PlannedQuestion(
    val id: String,
    val texts: QuestionTexts,
    val correctIndex: Int,
    val relatedCardId: String,
) {

    /**
     * 고른 선택지가 정답인지 판단한다. 결과는 복습 목록에만 쓰고 점수로 보여 주지 않는다.
     *
     * @param choice 고른 선택지 위치
     * @return 정답이면 true
     */
    fun isCorrect(choice: Int): Boolean = choice == correctIndex
}

/**
 * 콘텐츠 × 상황으로 정한 이 대여의 교육 계획. 화면과 흐름은 상황을 직접 보지 않고 이 계획만 쓴다.
 *
 * @property cards 보여 줄 카드(순서대로)
 * @property questions 낼 문항(순서대로)
 */
data class LearningPlan(val cards: List<PlannedCard>, val questions: List<PlannedQuestion>) {

    /**
     * 문항 식별자를 순서대로 돌려준다.
     *
     * @return 문항 식별자 목록
     */
    fun questionIds(): List<String> = questions.map { it.id }

    /**
     * 문항을 식별자로 찾는다.
     *
     * @param id 문항 식별자
     * @return 문항. 계획에 없으면 null
     */
    fun question(id: String): PlannedQuestion? = questions.firstOrNull { it.id == id }

    /**
     * 카드를 식별자로 찾는다.
     *
     * @param id 카드 식별자
     * @return 카드. 계획에 없으면 null
     */
    fun card(id: String): PlannedCard? = cards.firstOrNull { it.id == id }

    /**
     * 복습 목록의 문항들이 가리키는 "다시 볼 규칙" 카드를 겹치지 않게 카드 순서대로 돌려준다.
     *
     * @param reviewIds 복습 목록(문항 식별자)
     * @return 다시 볼 카드
     */
    fun reviewCards(reviewIds: List<String>): List<PlannedCard> {
        val related = reviewIds.mapNotNull { question(it)?.relatedCardId }.toSet()
        return cards.filter { it.id in related }
    }

    companion object {
        /**
         * 콘텐츠에서 상황에 맞는 카드·문항과 변형을 골라 계획을 만든다.
         *
         * @param catalog 교육 콘텐츠
         * @param situation 이 대여의 상황
         * @return 이 대여의 교육 계획
         */
        fun of(catalog: LearningCatalog, situation: LearningSituation): LearningPlan = LearningPlan(
            cards = catalog.cards
                .filter { it.appliesWhen.matches(situation) }
                .map { PlannedCard(it.id, it.textsFor(situation)) },
            questions = catalog.questions
                .filter { it.appliesWhen.matches(situation) }
                .map { PlannedQuestion(it.id, it.textsFor(situation), it.correctIndex, it.relatedCardId) },
        )
    }
}

/**
 * 지금 대여·업체 설정으로 교육 계획을 만들어 주는 창구. Coordinator에는 문항 순서([QuizOrder])로 쓰인다.
 * 계획은 부를 때마다 새로 만들어, 대여·업체 설정이 바뀌면 다음 화면부터 바로 따른다.
 * 진행은 식별자로 기록하므로 계획에서 빠진 항목의 기록은 무시되고 남은 항목은 그대로 이어진다.
 *
 * @param catalog 교육 콘텐츠
 * @param session 지금 대여
 * @param vendor 업체 설정
 */
class LearningPlans(
    private val catalog: LearningCatalog,
    private val session: StateFlow<RentalSession?>,
    private val vendor: StateFlow<VendorConfig>,
) : QuizOrder {

    /**
     * 지금 상황의 교육 계획을 만든다.
     *
     * @return 교육 계획
     */
    fun current(): LearningPlan = LearningPlan.of(catalog, LearningSituation.of(session.value, vendor.value))

    /**
     * 지금 계획의 문항 순서를 돌려준다.
     *
     * @return 문항 식별자 목록
     */
    override fun questionIds(): List<String> = current().questionIds()
}
