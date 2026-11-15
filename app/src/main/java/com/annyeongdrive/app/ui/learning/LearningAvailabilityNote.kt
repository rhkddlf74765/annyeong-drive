package com.annyeongdrive.app.ui.learning

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.annyeongdrive.app.ui.theme.ScreenSection

/** 교육 버튼이 비활성화된 이유를 표시한다. */
@Composable
internal fun LearningAvailabilityNote(model: LearningAccessModel) {
    model.message?.let { message ->
        ScreenSection { Text(stringResource(message)) }
    }
}
