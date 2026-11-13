package com.annyeongdrive.app.ui.learning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.annyeongdrive.app.R
import com.annyeongdrive.app.ui.theme.Annyeong_driveTheme

/**
 * 교육 시작 화면. "교육 시작"(T25)과 "나중에 보기"(T23)만 있다. 학습 카드·퀴즈는 6단계에서 붙인다.
 *
 * @param onBegin 교육 시작을 눌렀을 때
 * @param onLater 나중에 보기를 눌렀을 때
 * @param modifier 바깥에서 주는 배치 설정
 */
@Composable
fun LearningStartScreen(onBegin: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.learning_start_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.learning_start_body))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBegin) { Text(stringResource(R.string.action_learning_begin)) }
            TextButton(onClick = onLater) { Text(stringResource(R.string.action_later)) }
        }
    }
}

/** 교육 시작 화면 미리보기. */
@Preview(showBackground = true)
@Composable
private fun LearningStartScreenPreview() {
    Annyeong_driveTheme {
        LearningStartScreen(onBegin = {}, onLater = {})
    }
}
