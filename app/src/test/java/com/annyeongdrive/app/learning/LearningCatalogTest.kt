package com.annyeongdrive.app.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 교육 콘텐츠 검사: 1차 콘텐츠의 구성, 문항 형식, 금지 표현. 콘텐츠를 늘릴 때 빠뜨리거나 잘못 넣는 것을 막는다.
 * 근거: 기능 실행안 부록 A·B(카드 A-1~A-6, 문항 5개, 선택지 4개, 정답 1개), CLAUDE.md 도메인 원칙(금지 표현), implementation-plan 6단계.
 */
class LearningCatalogTest {

    /**
     * 기본 strings.xml에서 교육 문구(learn_로 시작)만 읽는다.
     *
     * @return 문구 이름 → 문장
     */
    private fun learningStrings(): Map<String, String> {
        val file = listOf("src/main/res/values/strings.xml", "app/src/main/res/values/strings.xml")
            .map(::File).first { it.exists() }
        val pattern = Regex("""<string name="(learn_[^"]+)">(.*?)</string>""")
        return pattern.findAll(file.readText()).associate { it.groupValues[1] to it.groupValues[2] }
    }

    /** 1차 콘텐츠는 카드 6장(A-1~A-6)과 문항 5개(Q1~Q5)이고 앱의 문구 버전과 같다. */
    @Test
    fun `1차 콘텐츠는 카드 6장과 문항 5개다`() {
        // 상황 · 행동
        val catalog = CORE_CATALOG

        // 결과
        assertEquals(6, catalog.cards.size)
        assertEquals(5, catalog.questions.size)
        assertEquals(CURRENT_CONTENT_VERSION, catalog.version)
    }

    /** 선택지가 4개가 아니면 문항을 만들 수 없다. */
    @Test
    fun `선택지가 4개가 아니면 문항을 만들 수 없다`() {
        // 상황
        val three = List(3) { TextId(it) }

        // 행동 · 결과
        assertThrows(IllegalArgumentException::class.java) { QuestionTexts(TextId(9), three, TextId(10)) }
    }

    /** 정답 위치가 선택지 범위 밖이면 문항을 만들 수 없다. */
    @Test
    fun `정답 위치가 선택지 밖이면 문항을 만들 수 없다`() {
        // 상황
        val texts = QuestionTexts(TextId(9), List(4) { TextId(it) }, TextId(10))

        // 행동 · 결과
        assertThrows(IllegalArgumentException::class.java) { QuestionContent("quiz.x", texts, 4, "card.x") }
    }

    /** 관련 카드가 콘텐츠에 없는 문항은 넣을 수 없다. */
    @Test
    fun `관련 카드가 없는 문항은 콘텐츠에 넣을 수 없다`() {
        // 상황
        val texts = QuestionTexts(TextId(9), List(4) { TextId(it) }, TextId(10))
        val orphan = QuestionContent("quiz.x", texts, 0, "card.missing")

        // 행동 · 결과
        assertThrows(IllegalArgumentException::class.java) { LearningCatalog("v", emptyList(), listOf(orphan)) }
    }

    /** 같은 식별자의 카드를 두 번 넣을 수 없다(진행 기록이 식별자로 남기 때문이다). */
    @Test
    fun `같은 식별자의 카드는 넣을 수 없다`() {
        // 상황
        val card = CardContent("card.a", CardTexts(TextId(1), TextId(2)))

        // 행동 · 결과
        assertThrows(IllegalArgumentException::class.java) { LearningCatalog("v", listOf(card, card), emptyList()) }
    }

    /** 교육 문구에 점수·합격·준비 완료 같은 평가 표현과 고정 제한속도, +5km/h 표현이 없다. */
    @Test
    fun `교육 문구에 평가 표현과 고정 제한속도가 없다`() {
        // 상황
        val forbidden = listOf("점수", "합격", "준비 완료", "30km", "30 km", "+5", "통과")

        // 행동
        val found = learningStrings().filter { (_, text) -> forbidden.any { it in text } }.keys

        // 결과
        assertTrue("금지 표현이 있는 문구: $found", found.isEmpty())
    }

    /** "보행자가 없어도 일시정지"는 어린이보호구역 신호 없는 횡단보도 문구에만 쓴다. */
    @Test
    fun `보행자가 없어도 일시정지는 어린이보호구역 문구에만 쓴다`() {
        // 상황
        val allowed = setOf("learn_card_zone_body", "learn_q2_question", "learn_q2_o2", "learn_q2_explain")

        // 행동
        val used = learningStrings().filter { (_, text) -> "보행자가 없어도" in text }.keys

        // 결과
        assertTrue("허용되지 않은 곳에 쓴 문구: ${used - allowed}", (used - allowed).isEmpty())
    }
}
