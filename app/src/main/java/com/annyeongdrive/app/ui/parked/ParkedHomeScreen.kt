package com.annyeongdrive.app.ui.parked

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.annyeongdrive.app.R
import com.annyeongdrive.app.drive.LearningAvailability
import com.annyeongdrive.app.ui.learning.LearningAvailabilityNote
import com.annyeongdrive.app.ui.theme.Annyeong_driveTheme

/**
 * 정차 대기 화면에서 누를 수 있는 동작 묶음.
 *
 * @property onOpenLearning 교육·퀴즈 열기(T21)
 * @property onReturn 반납 완료(T31)
 * @property onVendorSettings 업체 설정(B9)
 */
data class ParkedHomeActions(
    val onOpenLearning: () -> Unit,
    val onReturn: () -> Unit,
    val onVendorSettings: () -> Unit,
)

/**
 * 정차 대기 화면. 교육·퀴즈 열기(T21), 반납 완료(T31), 작은 업체 설정 버튼(B9)이 있다. 도움 메뉴는 P1이다.
 * 교육을 열 수 없으면 버튼을 막고 이유를 보여 준다. 이유는 차량 스냅샷이 정한다.
 *
 * @param availability 교육을 열 수 있는지
 * @param actions 누를 수 있는 동작
 * @param modifier 바깥에서 주는 배치 설정
 */
@Composable
fun ParkedHome(availability: LearningAvailability, actions: ParkedHomeActions, modifier: Modifier = Modifier) {
    val canOpen = availability == LearningAvailability.AVAILABLE
    Column(modifier = modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.parked_home_title), style = MaterialTheme.typography.titleLarge)
        Button(onClick = actions.onOpenLearning, enabled = canOpen) { Text(stringResource(R.string.action_learning_open)) }
        LearningAvailabilityNote(availability)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = actions.onReturn) { Text(stringResource(R.string.action_return)) }
            TextButton(onClick = actions.onVendorSettings) { Text(stringResource(R.string.action_vendor_settings)) }
        }
    }
}

/** 정차 대기 화면 미리보기(교육을 열 수 있음). */
@Preview(showBackground = true)
@Composable
private fun ParkedHomePreview() {
    Annyeong_driveTheme {
        ParkedHome(LearningAvailability.AVAILABLE, ParkedHomeActions({}, {}, {}))
    }
}

/** 정차 대기 화면 미리보기(진입 조건이 아님). */
@Preview(showBackground = true)
@Composable
private fun ParkedHomeRestrictedPreview() {
    Annyeong_driveTheme {
        ParkedHome(LearningAvailability.RESTRICTED, ParkedHomeActions({}, {}, {}))
    }
}
