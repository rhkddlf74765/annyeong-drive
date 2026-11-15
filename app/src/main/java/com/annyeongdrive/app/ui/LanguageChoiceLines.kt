package com.annyeongdrive.app.ui

import androidx.annotation.StringRes
import com.annyeongdrive.app.rental.AppLanguage
import com.annyeongdrive.app.setup.SELECTABLE_LANGUAGES

/**
 * 언어를 고르기 전 화면에서 보여 줄 한 줄: 같은 문구를 어느 언어로 보여 줄지.
 *
 * @property textId 문구 ID
 * @property language 그 줄의 언어
 */
data class LocalizedLine(@param:StringRes val textId: Int, val language: AppLanguage)

/**
 * 언어를 고르기 전 화면(대기, 언어 선택)에 같은 문구를 고를 수 있는 모든 언어로 한 줄씩 만든다.
 * 외국인 운전자를 위해 영어를 먼저 두고 나머지는 고를 수 있는 순서대로 둔다(11/15 결정 A, 상태 머신 변경 없음).
 *
 * @param textId 문구 ID
 * @return 언어별 줄 목록
 */
fun linesBeforeLanguageChoice(@StringRes textId: Int): List<LocalizedLine> {
    val others = SELECTABLE_LANGUAGES.filterNot { it == AppLanguage.ENGLISH }
    return (listOf(AppLanguage.ENGLISH) + others).map { LocalizedLine(textId, it) }
}
