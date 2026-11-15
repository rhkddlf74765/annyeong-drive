package com.annyeongdrive.app.ui.parked

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.annyeongdrive.app.R
import com.annyeongdrive.app.drive.LearningAvailability
import com.annyeongdrive.app.ui.learning.LearningAccessModel
import com.annyeongdrive.app.ui.learning.LearningAvailabilityNote
import com.annyeongdrive.app.ui.theme.Annyeong_driveTheme
import com.annyeongdrive.app.ui.theme.PrimaryAction
import com.annyeongdrive.app.ui.theme.QuietAction
import com.annyeongdrive.app.ui.theme.ScreenHeading
import com.annyeongdrive.app.ui.theme.SecondaryAction
import com.annyeongdrive.app.ui.theme.VehicleScreen

/** 교육 열기(T21), 반납(T31), 업체 설정(B9)에 연결된 기존 동작. */
data class ParkedHomeActions(
    val onOpenLearning: () -> Unit,
    val onReturn: () -> Unit,
    val onVendorSettings: () -> Unit,
)

/** 정차 대기 화면. 교육 버튼 상태와 안내는 화면 모델에서 받는다. */
@Composable
internal fun ParkedHome(model: LearningAccessModel, actions: ParkedHomeActions, modifier: Modifier = Modifier) {
    VehicleScreen(modifier) {
        ScreenHeading(stringResource(R.string.parked_home_title))
        LearningAvailabilityNote(model)
        PrimaryAction(R.string.action_learning_open, actions.onOpenLearning, enabled = model.canOpen)
        SecondaryAction(R.string.action_return, actions.onReturn)
        QuietAction(R.string.action_vendor_settings, actions.onVendorSettings)
    }
}

/** 교육을 열 수 있는 정차 대기 화면 미리보기. */
@Preview(showBackground = true)
@Composable
private fun ParkedHomePreview() {
    Annyeong_driveTheme {
        ParkedHome(LearningAccessModel.of(LearningAvailability.AVAILABLE), ParkedHomeActions({}, {}, {}))
    }
}

/** 교육을 열 수 없는 정차 대기 화면 미리보기. */
@Preview(showBackground = true)
@Composable
private fun ParkedHomeRestrictedPreview() {
    Annyeong_driveTheme {
        ParkedHome(LearningAccessModel.of(LearningAvailability.RESTRICTED), ParkedHomeActions({}, {}, {}))
    }
}
