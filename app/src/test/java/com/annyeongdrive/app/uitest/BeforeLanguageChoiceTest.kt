package com.annyeongdrive.app.uitest

import com.annyeongdrive.app.R
import com.annyeongdrive.app.rental.AppLanguage
import com.annyeongdrive.app.setup.SELECTABLE_LANGUAGES
import com.annyeongdrive.app.ui.linesBeforeLanguageChoice
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 언어를 고르기 전 화면: 대기 화면과 언어 선택 제목을 고를 수 있는 모든 언어로 함께 보여 주고, 외국인 운전자를 위해 영어를 먼저 둔다.
 * 근거: 11/15 사용자 결정(처음 화면부터 한국어만이 아니라 언어를 알아볼 수 있게, 방법 A: 상태 머신은 그대로 두고 문구만 함께 표시),
 * 결정 문서 지원 언어(1차 제출 한국어·영어).
 */
class BeforeLanguageChoiceTest {

    /** 언어를 고르기 전 문구는 영어를 먼저, 한국어를 다음에 보여 주는지 확인한다. */
    @Test
    fun `언어를 고르기 전 문구는 영어를 먼저 한국어를 다음에 보여 준다`() {
        // 상황 · 행동
        val lines = linesBeforeLanguageChoice(R.string.home_idle)

        // 결과
        assertEquals(listOf(AppLanguage.ENGLISH, AppLanguage.KOREAN), lines.map { it.language })
    }

    /** 고를 수 있는 언어를 빠짐없이 함께 보여 주는지 확인한다(언어를 늘리면 따라 늘어난다). */
    @Test
    fun `고를 수 있는 언어를 빠짐없이 함께 보여 준다`() {
        // 상황 · 행동
        val languages = linesBeforeLanguageChoice(R.string.setup_language_title).map { it.language }

        // 결과
        assertEquals(SELECTABLE_LANGUAGES.toSet(), languages.toSet())
        assertEquals(SELECTABLE_LANGUAGES.size, languages.size)
    }

    /** 줄마다 같은 문구 ID를 언어만 바꿔 보여 주는지 확인한다. */
    @Test
    fun `줄마다 같은 문구를 언어만 바꿔 보여 준다`() {
        // 상황 · 행동
        val lines = linesBeforeLanguageChoice(R.string.setup_language_title)

        // 결과
        assertEquals(setOf(R.string.setup_language_title), lines.map { it.textId }.toSet())
    }
}
