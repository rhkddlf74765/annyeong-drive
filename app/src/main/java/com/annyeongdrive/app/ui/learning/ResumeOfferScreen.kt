package com.annyeongdrive.app.ui.learning

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.annyeongdrive.app.R
import com.annyeongdrive.app.drive.LearningAvailability
import com.annyeongdrive.app.ui.theme.Annyeong_driveTheme
import com.annyeongdrive.app.ui.theme.PrimaryAction
import com.annyeongdrive.app.ui.theme.QuietAction
import com.annyeongdrive.app.ui.theme.ScreenHeading
import com.annyeongdrive.app.ui.theme.SecondaryAction
import com.annyeongdrive.app.ui.theme.VehicleScreen

/** 이어 보기(T19·B2), 처음부터 보기(T19·B2), 나중에 보기(T20)에 연결된 동작. */
data class ResumeOfferActions(
    val onResume: () -> Unit,
    val onRestart: () -> Unit,
    val onLater: () -> Unit,
)

/** 정차 후 표시하는 이어 보기 안내(T15). */
@Composable
internal fun ResumeOfferScreen(model: LearningAccessModel, actions: ResumeOfferActions, modifier: Modifier = Modifier) {
    VehicleScreen(modifier) {
        ScreenHeading(stringResource(R.string.resume_offer_title))
        Text(stringResource(R.string.resume_offer_body))
        LearningAvailabilityNote(model)
        PrimaryAction(R.string.action_resume, actions.onResume, enabled = model.canOpen)
        SecondaryAction(R.string.action_restart, actions.onRestart, enabled = model.canOpen)
        QuietAction(R.string.action_later, actions.onLater)
    }
}

/** 교육을 열 수 있는 이어 보기 화면 미리보기. */
@Preview(showBackground = true)
@Composable
private fun ResumeOfferScreenPreview() {
    Annyeong_driveTheme {
        ResumeOfferScreen(LearningAccessModel.of(LearningAvailability.AVAILABLE), ResumeOfferActions({}, {}, {}))
    }
}

/** 차량 상태를 확인하는 이어 보기 화면 미리보기. */
@Preview(showBackground = true)
@Composable
private fun ResumeOfferScreenCheckingPreview() {
    Annyeong_driveTheme {
        ResumeOfferScreen(LearningAccessModel.of(LearningAvailability.CHECKING_VEHICLE), ResumeOfferActions({}, {}, {}))
    }
}
