package com.annyeongdrive.app.ui

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.annyeongdrive.app.ui.theme.ScreenHeading
import com.annyeongdrive.app.ui.theme.VehicleScreen

/**
 * 언어를 고르기 전 제목. 같은 문구를 고를 수 있는 모든 언어로 보여 준다(첫 줄 영어는 제목, 나머지는 한 단계 작은 글씨).
 * 외국인 운전자가 첫 화면부터 내용을 알아볼 수 있게 한다(11/15 결정 A).
 *
 * @param textId 문구 ID
 */
@Composable
internal fun HeadingBeforeLanguageChoice(@StringRes textId: Int) {
    val lines = linesBeforeLanguageChoice(textId)
    ScreenHeading(localizedString(lines.first()))
    lines.drop(1).forEach { line -> Text(localizedString(line), style = MaterialTheme.typography.titleMedium) }
}

/**
 * 대기 화면(운전석에 앉기 전·반납 뒤). 아직 대여 언어가 없으므로 모든 언어로 함께 보여 준다.
 *
 * @param messageId 보여 줄 문구 ID(대기 안내 또는 위치 권한 안내)
 * @param modifier 바깥에서 주는 배치 설정
 */
@Composable
internal fun WaitingMessage(@StringRes messageId: Int, modifier: Modifier = Modifier) {
    VehicleScreen(modifier) {
        HeadingBeforeLanguageChoice(messageId)
    }
}
