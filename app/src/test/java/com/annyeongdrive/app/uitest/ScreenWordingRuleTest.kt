package com.annyeongdrive.app.uitest

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 화면 문구 규칙: 한국어·영어 화면 문구에 평가 표현(점수·합격·"준비 완료" 등)과 허용 속도 표현(+5km/h)이 없다.
 * 화면 작업(feature/ui)에서 문구를 바꾸거나 새로 넣어도 이 규칙이 지켜지는지 UI Test 워크플로가 확인한다.
 * 근거: CLAUDE.md 도메인 원칙(과속·위반을 판정하거나 평가하지 않는다, 점수·합격·"준비 완료" 표현 금지, +5km/h 허용 표현 금지),
 * 11/15 사용자 결정(화면 테스트는 별도 워크플로), 노션 "화면 작업 안내서 (11/15)" 8장.
 */
class ScreenWordingRuleTest {

    private val korean = stringsIn("src/main/res/values/strings.xml")
    private val english = stringsIn("src/main/res/values-en/strings.xml")

    /** 한국어 화면 문구에 평가 표현과 허용 속도 표현이 없는지 확인한다. */
    @Test
    fun `한국어 화면 문구에 평가 표현이 없다`() {
        // 상황 · 행동
        val found = violations(korean, KOREAN_FORBIDDEN)

        // 결과
        assertEquals(emptyMap<String, String>(), found)
    }

    /** 영어 화면 문구에 평가 표현과 허용 속도 표현이 없는지 확인한다(대소문자 무시). */
    @Test
    fun `영어 화면 문구에 평가 표현이 없다`() {
        // 상황 · 행동
        val found = violations(english, ENGLISH_FORBIDDEN)

        // 결과
        assertEquals(emptyMap<String, String>(), found)
    }

    /**
     * 금지 표현이 들어간 문구를 찾는다.
     *
     * @param strings 문구 ID → 문구
     * @param forbidden 금지 표현 목록
     * @return 금지 표현이 들어간 문구 ID → 그 문구
     */
    private fun violations(strings: Map<String, String>, forbidden: List<String>): Map<String, String> =
        strings.filterValues { text -> forbidden.any { text.contains(it, ignoreCase = true) } }

    /**
     * strings.xml에서 문구 ID와 문구를 읽는다.
     *
     * @param path 모듈 기준 파일 경로
     * @return 문구 ID → 문구
     */
    private fun stringsIn(path: String): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
    }

    private companion object {
        /** 한국어 금지 표현. */
        val KOREAN_FORBIDDEN = listOf("점수", "합격", "준비 완료", "준비완료", "+5")

        /** 영어 금지 표현. */
        val ENGLISH_FORBIDDEN = listOf("score", "you passed", "ready to go", "all set", "+5")
    }
}
