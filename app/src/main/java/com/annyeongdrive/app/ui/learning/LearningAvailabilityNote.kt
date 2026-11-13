package com.annyeongdrive.app.ui.learning

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.annyeongdrive.app.R
import com.annyeongdrive.app.drive.LearningAvailability

/**
 * 교육·퀴즈를 열 수 없을 때 그 이유를 한 줄로 보여 준다. 열 수 있으면 아무것도 그리지 않는다.
 * 이유는 차량 스냅샷이 정하고, 이 화면은 문구만 고른다.
 *
 * @param availability 교육을 열 수 있는지
 * @param modifier 바깥에서 주는 배치 설정
 */
@Composable
fun LearningAvailabilityNote(availability: LearningAvailability, modifier: Modifier = Modifier) {
    val messageId = when (availability) {
        LearningAvailability.AVAILABLE -> return
        LearningAvailability.CHECKING_VEHICLE -> R.string.learning_unavailable_checking
        LearningAvailability.RESTRICTED -> R.string.learning_unavailable_restricted
    }
    Text(stringResource(messageId), style = MaterialTheme.typography.bodyMedium, modifier = modifier)
}
