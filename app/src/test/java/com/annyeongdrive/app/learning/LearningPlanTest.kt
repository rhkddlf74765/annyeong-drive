package com.annyeongdrive.app.learning

import com.annyeongdrive.app.learning.SituationCondition.Companion.drivingCountryIn
import com.annyeongdrive.app.learning.SituationCondition.Companion.fuelIs
import com.annyeongdrive.app.vehicle.FuelType
import com.annyeongdrive.app.vendor.HiPassAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 상황 대응: 콘텐츠 × 상황 → 이 대여의 교육 계획. 적용 조건과 변형(유종, 운전 국가 등)이 기대대로 골라지는지 확인한다.
 * 실제 변형 수와 상관없이 구조를 확인하도록 가짜 콘텐츠를 쓴다.
 * 근거: 결정 문서 7장(국가별 차이는 설계만 대비), 11/13 결정(상황 변형은 구조 + 가짜 변형 테스트), implementation-plan 6단계.
 */
class LearningPlanTest {

    private val base = CardTexts(TextId(1), TextId(2))
    private val ev = CardTexts(TextId(1), TextId(3))
    private val leftDriving = CardTexts(TextId(1), TextId(4))

    private val catalog = LearningCatalog(
        version = "test",
        cards = listOf(
            CardContent(
                id = "card.a",
                texts = base,
                variants = listOf(Variant(drivingCountryIn("GB", "JP"), leftDriving), Variant(fuelIs(FuelType.ELECTRIC), ev)),
            ),
            CardContent(id = "card.hipass", texts = base, appliesWhen = { it.hiPass == HiPassAvailability.AVAILABLE }),
        ),
        questions = listOf(
            QuestionContent("quiz.a", questionTexts(), correctIndex = 2, relatedCardId = "card.a"),
            QuestionContent("quiz.b", questionTexts(), correctIndex = 0, relatedCardId = "card.a"),
        ),
    )

    /**
     * 시험용 문항 문구를 만든다.
     *
     * @return 문항 문구
     */
    private fun questionTexts() = QuestionTexts(TextId(10), List(4) { TextId(11 + it) }, TextId(20))

    /**
     * 시험용 상황을 만든다.
     *
     * @param fuel 유종
     * @param country 운전 국가
     * @param hiPass 하이패스
     * @return 상황
     */
    private fun situation(
        fuel: FuelType = FuelType.GASOLINE,
        country: String? = null,
        hiPass: HiPassAvailability = HiPassAvailability.UNKNOWN,
    ) = LearningSituation(null, country, fuel, hiPass)

    /** 조건에 맞는 변형이 없으면 기본 문구를 쓴다. */
    @Test
    fun `맞는 변형이 없으면 기본 문구를 쓴다`() {
        // 상황 · 행동
        val plan = LearningPlan.of(catalog, situation())

        // 결과
        assertEquals(base, plan.card("card.a")!!.texts)
    }

    /** 업체가 전기차로 정하면 전기차 변형을 쓴다. */
    @Test
    fun `전기차면 전기차 변형을 쓴다`() {
        // 상황 · 행동
        val plan = LearningPlan.of(catalog, situation(fuel = FuelType.ELECTRIC))

        // 결과
        assertEquals(ev, plan.card("card.a")!!.texts)
    }

    /** 운전 국가 변형과 유종 변형이 함께 맞으면 앞에 적은 변형을 쓴다. */
    @Test
    fun `여러 변형이 맞으면 앞에 적은 변형을 쓴다`() {
        // 상황 · 행동
        val plan = LearningPlan.of(catalog, situation(fuel = FuelType.ELECTRIC, country = "GB"))

        // 결과
        assertEquals(leftDriving, plan.card("card.a")!!.texts)
    }

    /** 운전 국가를 모르면(지금은 늘 null) 국가 변형은 쓰지 않는다. */
    @Test
    fun `운전 국가를 모르면 국가 변형을 쓰지 않는다`() {
        // 상황
        val unknownCountry = situation(country = null)

        // 행동
        val plan = LearningPlan.of(catalog, unknownCountry)

        // 결과
        assertFalse(drivingCountryIn("GB").matches(unknownCountry))
        assertEquals(base, plan.card("card.a")!!.texts)
    }

    /** 적용 조건이 맞지 않는 카드는 계획에서 빠진다. 업체가 하이패스를 정하지 않으면 하이패스 카드는 없다. */
    @Test
    fun `적용 조건이 맞지 않는 카드는 계획에서 빠진다`() {
        // 상황 · 행동
        val unknown = LearningPlan.of(catalog, situation(hiPass = HiPassAvailability.UNKNOWN))
        val available = LearningPlan.of(catalog, situation(hiPass = HiPassAvailability.AVAILABLE))

        // 결과
        assertEquals(listOf("card.a"), unknown.cards.map { it.id })
        assertEquals(listOf("card.a", "card.hipass"), available.cards.map { it.id })
    }

    /** 정답 판단은 정답 위치 하나만 맞다고 본다. */
    @Test
    fun `정답 위치의 선택지만 정답이다`() {
        // 상황
        val question = LearningPlan.of(catalog, situation()).question("quiz.a")!!

        // 행동 · 결과
        assertTrue(question.isCorrect(2))
        assertFalse(question.isCorrect(0))
    }

    /** 다시 볼 규칙은 복습 문항의 관련 카드를 겹치지 않게 돌려준다. */
    @Test
    fun `다시 볼 규칙은 관련 카드를 겹치지 않게 돌려준다`() {
        // 상황
        val plan = LearningPlan.of(catalog, situation())

        // 행동
        val review = plan.reviewCards(listOf("quiz.a", "quiz.b"))

        // 결과
        assertEquals(listOf("card.a"), review.map { it.id })
    }
}
