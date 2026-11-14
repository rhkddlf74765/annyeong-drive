package com.annyeongdrive.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 영어 화면 문구: 영어 대여를 고르면 모든 화면 문구가 영어로 나와야 한다. 한국어 문구 ID마다 영어 문구가 있고,
 * 자리 표시(%1$d 같은 값)가 같아야 한다. 앱 이름과 언어 이름(각 언어로 쓴 이름)은 번역하지 않는다.
 * 근거: 결정 문서 1장(1차 제출 언어 한국어·영어)·7장(values-en), implementation-plan 10단계.
 */
class EnglishScreenTextTest {

    private val korean = stringsIn("src/main/res/values/strings.xml")
    private val english = stringsIn("src/main/res/values-en/strings.xml")

    /** 번역하지 않는 문구 ID. */
    private val untranslated = setOf("app_name", "language_korean", "language_english", "language_japanese", "language_spanish")

    /** 번역할 한국어 문구는 모두 영어 문구가 있다. */
    @Test
    fun `한국어 문구마다 영어 문구가 있다`() {
        // 상황 · 행동
        val missing = korean.keys - untranslated - english.keys

        // 결과
        assertTrue("영어 문구가 없는 ID: $missing", missing.isEmpty())
    }

    /** 영어 파일에만 있는 문구 ID는 없다(오타나 지운 문구가 남지 않게). */
    @Test
    fun `영어 파일에만 있는 문구는 없다`() {
        // 상황 · 행동
        val extra = english.keys - korean.keys

        // 결과
        assertTrue("한국어에 없는 ID: $extra", extra.isEmpty())
    }

    /** 영어 문구의 자리 표시가 한국어와 같다. 다르면 화면에서 값이 빠지거나 앱이 멈춘다. */
    @Test
    fun `영어 문구의 자리 표시가 한국어와 같다`() {
        // 상황 · 행동
        val different = english.keys.intersect(korean.keys).filter { placeholders(korean.getValue(it)) != placeholders(english.getValue(it)) }

        // 결과
        assertEquals(emptyList<String>(), different)
    }

    /**
     * strings.xml을 읽어 문구 ID와 문장을 돌려준다.
     *
     * @param path 모듈 기준 경로
     * @return 문구 ID → 문장
     */
    private fun stringsIn(path: String): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
    }

    /**
     * 문장 속 자리 표시(%1$d, %2$s 등)를 모은다.
     *
     * @param text 문장
     * @return 자리 표시 집합
     */
    private fun placeholders(text: String): Set<String> = PLACEHOLDER.findAll(text).map { it.value }.toSet()

    private companion object {
        /** 위치 지정 자리 표시. */
        val PLACEHOLDER = Regex("%\\d+\\$[a-z]")
    }
}
