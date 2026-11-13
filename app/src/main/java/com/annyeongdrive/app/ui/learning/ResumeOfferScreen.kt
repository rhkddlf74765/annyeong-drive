package com.annyeongdrive.app.ui.learning

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
import com.annyeongdrive.app.ui.theme.Annyeong_driveTheme

/**
 * "이어서 확인할까요?" 화면에서 누를 수 있는 동작 묶음.
 *
 * @property onResume 이어 보기(T19·B2)
 * @property onRestart 처음부터 보기(새 회차 저장 후 T19·B2)
 * @property onLater 나중에 보기(T20)
 */
data class ResumeOfferActions(
    val onResume: () -> Unit,
    val onRestart: () -> Unit,
    val onLater: () -> Unit,
)

/**
 * "이어서 확인할까요?" 화면. 미완료 교육이 있을 때 정차하면 보인다(T15).
 * 교육을 열 수 없으면 이어 보기·처음부터 보기를 막고 이유를 보여 준다. 나중에 보기는 언제나 누를 수 있다.
 *
 * @param availability 교육을 열 수 있는지(차량 스냅샷이 정한다)
 * @param actions 누를 수 있는 동작
 * @param modifier 바깥에서 주는 배치 설정
 */
@Composable
fun ResumeOfferScreen(availability: LearningAvailability, actions: ResumeOfferActions, modifier: Modifier = Modifier) {
    val canOpen = availability == LearningAvailability.AVAILABLE
    Column(modifier = modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.resume_offer_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.resume_offer_body))
        LearningAvailabilityNote(availability)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = actions.onResume, enabled = canOpen) { Text(stringResource(R.string.action_resume)) }
            OutlinedButton(onClick = actions.onRestart, enabled = canOpen) { Text(stringResource(R.string.action_restart)) }
            TextButton(onClick = actions.onLater) { Text(stringResource(R.string.action_later)) }
        }
    }
}

/** 이어서 확인할까요 화면 미리보기(열 수 있음). */
@Preview(showBackground = true)
@Composable
private fun ResumeOfferScreenPreview() {
    Annyeong_driveTheme {
        ResumeOfferScreen(LearningAvailability.AVAILABLE, ResumeOfferActions({}, {}, {}))
    }
}

/** 이어서 확인할까요 화면 미리보기(차량 상태 확인 중). */
@Preview(showBackground = true)
@Composable
private fun ResumeOfferScreenCheckingPreview() {
    Annyeong_driveTheme {
        ResumeOfferScreen(LearningAvailability.CHECKING_VEHICLE, ResumeOfferActions({}, {}, {}))
    }
}
