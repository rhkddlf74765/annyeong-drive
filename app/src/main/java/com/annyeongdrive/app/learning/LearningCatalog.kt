package com.annyeongdrive.app.learning

import com.annyeongdrive.app.rental.AppLanguage
import com.annyeongdrive.app.rental.RentalSession
import com.annyeongdrive.app.vehicle.FuelType
import com.annyeongdrive.app.vendor.HiPassAvailability
import com.annyeongdrive.app.vendor.VendorConfig

/**
 * 화면 문구 하나를 가리키는 문구 ID. strings.xml의 R.string 값을 담는다.
 * 언어는 Android 언어별 리소스(values, values-en …)가 고르므로 콘텐츠는 언어를 모른다(결정 문서 7장).
 *
 * @property res R.string 값
 */
@JvmInline
value class TextId(val res: Int)

/**
 * 이 대여의 교육 콘텐츠를 고를 때 보는 조건. 읽기 전용이다.
 *
 * @property language 대여에서 고른 언어
 * @property drivingCountry 운전 국가. 아직 받지 않아 지금은 null
 * @property fuelType 업체가 정한 유종
 * @property hiPass 업체가 정한 하이패스 이용 여부
 */
data class LearningSituation(
    val language: AppLanguage?,
    val drivingCountry: String?,
    val fuelType: FuelType,
    val hiPass: HiPassAvailability,
) {
    companion object {
        /**
         * 대여와 업체 설정으로 상황을 만든다.
         *
         * @param session 지금 대여. 없으면 null
         * @param vendor 업체 설정
         * @return 상황
         */
        fun of(session: RentalSession?, vendor: VendorConfig): LearningSituation = LearningSituation(
            language = session?.language,
            drivingCountry = session?.drivingCountry,
            fuelType = vendor.vehicle.fuelType,
            hiPass = vendor.vehicle.hiPass,
        )
    }
}

/** 상황에 따라 카드·문항을 보여 줄지, 어떤 변형을 쓸지 정하는 조건. */
fun interface SituationCondition {

    /**
     * 상황이 조건에 맞는지 판단한다.
     *
     * @param situation 이 대여의 상황
     * @return 맞으면 true
     */
    fun matches(situation: LearningSituation): Boolean

    companion object {
        /** 언제나 맞는 조건. */
        val ALWAYS = SituationCondition { true }

        /**
         * 업체가 정한 유종이 [fuelType]일 때 맞는 조건.
         *
         * @param fuelType 유종
         * @return 조건
         */
        fun fuelIs(fuelType: FuelType) = SituationCondition { it.fuelType == fuelType }

        /**
         * 운전 국가가 [countries] 중 하나일 때 맞는 조건. 운전 국가를 모르면 맞지 않는다.
         *
         * @param countries 국가 코드(ISO 3166-1 alpha-2)
         * @return 조건
         */
        fun drivingCountryIn(vararg countries: String) =
            SituationCondition { it.drivingCountry in countries.toSet() }
    }
}

/**
 * 조건이 맞을 때 기본 내용 대신 쓰는 변형.
 *
 * @property condition 변형을 쓸 조건
 * @property content 바꿔 쓸 내용
 */
data class Variant<T>(val condition: SituationCondition, val content: T)

/**
 * 학습 카드 한 장의 문구. 카드 하나에 행동 하나를 담는다.
 *
 * @property title 제목 문구
 * @property body 본문 문구
 */
data class CardTexts(val title: TextId, val body: TextId)

/**
 * 핵심 퀴즈 문항 하나의 문구. 선택지는 4개로 고정한다(부록 B).
 *
 * @property question 문제 문구
 * @property options 선택지 문구 4개
 * @property explanation 해설 문구
 */
data class QuestionTexts(val question: TextId, val options: List<TextId>, val explanation: TextId) {
    init {
        require(options.size == OPTION_COUNT) { "선택지는 ${OPTION_COUNT}개여야 한다" }
    }

    companion object {
        /** 선택지 개수. */
        const val OPTION_COUNT = 4
    }
}

/**
 * 학습 카드 정의. 상황에 따라 보여 줄지([appliesWhen])와 어떤 문구로 보여 줄지([variants])를 함께 둔다.
 *
 * @property id 카드 식별자. 진행 기록이 이 값으로 남으므로 바꾸지 않는다
 * @property texts 기본 문구
 * @property variants 조건별 변형. 앞에서부터 처음 맞는 것을 쓴다
 * @property appliesWhen 이 카드를 보여 줄 조건
 */
data class CardContent(
    val id: String,
    val texts: CardTexts,
    val variants: List<Variant<CardTexts>> = emptyList(),
    val appliesWhen: SituationCondition = SituationCondition.ALWAYS,
) {

    /**
     * 상황에 맞는 문구를 고른다.
     *
     * @param situation 이 대여의 상황
     * @return 처음 맞는 변형의 문구, 없으면 기본 문구
     */
    fun textsFor(situation: LearningSituation): CardTexts =
        variants.firstOrNull { it.condition.matches(situation) }?.content ?: texts
}

/**
 * 핵심 퀴즈 문항 정의. 정답은 하나다.
 *
 * @property id 문항 식별자. 진행 기록이 이 값으로 남으므로 바꾸지 않는다
 * @property texts 기본 문구
 * @property correctIndex 정답 선택지 위치(0부터)
 * @property relatedCardId 틀리거나 건너뛰었을 때 "다시 볼 규칙"으로 보여 줄 카드
 * @property variants 조건별 변형. 정답 위치는 바꾸지 않는다
 * @property appliesWhen 이 문항을 낼 조건
 */
data class QuestionContent(
    val id: String,
    val texts: QuestionTexts,
    val correctIndex: Int,
    val relatedCardId: String,
    val variants: List<Variant<QuestionTexts>> = emptyList(),
    val appliesWhen: SituationCondition = SituationCondition.ALWAYS,
) {
    init {
        require(correctIndex in 0 until QuestionTexts.OPTION_COUNT) { "정답 위치가 선택지 범위 밖이다: $id" }
    }

    /**
     * 상황에 맞는 문구를 고른다.
     *
     * @param situation 이 대여의 상황
     * @return 처음 맞는 변형의 문구, 없으면 기본 문구
     */
    fun textsFor(situation: LearningSituation): QuestionTexts =
        variants.firstOrNull { it.condition.matches(situation) }?.content ?: texts
}

/**
 * 교육 콘텐츠 전체. 카드와 문항을 화면에 나오는 순서대로 담고, 문구가 바뀌면 [version]을 올린다.
 * 버전이 바뀌면 저장된 교육 진행은 새로 시작한다(결정 문서 4장, LearningProgress.alignedTo).
 *
 * @property version 교육 문구 버전
 * @property cards 학습 카드(순서대로)
 * @property questions 핵심 퀴즈 문항(순서대로)
 */
class LearningCatalog(
    val version: String,
    val cards: List<CardContent>,
    val questions: List<QuestionContent>,
) {
    init {
        val cardIds = cards.map { it.id }
        val questionIds = questions.map { it.id }
        require(cardIds.toSet().size == cardIds.size) { "카드 식별자가 겹친다" }
        require(questionIds.toSet().size == questionIds.size) { "문항 식별자가 겹친다" }
        val unknown = questions.filter { it.relatedCardId !in cardIds }.map { it.id }
        require(unknown.isEmpty()) { "관련 카드가 없는 문항: $unknown" }
    }

    /**
     * 카드를 식별자로 찾는다.
     *
     * @param id 카드 식별자
     * @return 카드. 없으면 null
     */
    fun card(id: String): CardContent? = cards.firstOrNull { it.id == id }
}
