package com.annyeongdrive.app.ui.learning

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.annyeongdrive.app.ui.theme.Annyeong_driveTheme
import com.annyeongdrive.app.ui.theme.PrimaryAction
import com.annyeongdrive.app.ui.theme.QuietAction
import com.annyeongdrive.app.ui.theme.ScreenHeading
import com.annyeongdrive.app.ui.theme.VehicleScreen

/** 교육을 시작하거나 나중에 보는 화면(T25·T23). */
@Composable
fun LearningStartScreen(onBegin: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    val model = LEARNING_START_CONTENT
    VehicleScreen(modifier) {
        ScreenHeading(stringResource(model.title))
        model.body.forEach { Text(stringResource(it)) }
        PrimaryAction(model.primaryAction, onBegin)
        model.secondaryAction?.let { QuietAction(it, onLater) }
    }
}

/** 교육 시작 화면 미리보기. */
@Preview(showBackground = true)
@Composable
private fun LearningStartScreenPreview() {
    Annyeong_driveTheme { LearningStartScreen(onBegin = {}, onLater = {}) }
}
