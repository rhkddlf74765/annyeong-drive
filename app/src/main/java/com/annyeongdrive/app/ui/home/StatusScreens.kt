package com.annyeongdrive.app.ui.home

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.annyeongdrive.app.R
import com.annyeongdrive.app.ui.theme.Annyeong_driveTheme
import com.annyeongdrive.app.ui.theme.PrimaryAction
import com.annyeongdrive.app.ui.theme.ScreenHeading
import com.annyeongdrive.app.ui.theme.ScreenSection
import com.annyeongdrive.app.ui.theme.VehicleScreen

/** 주행 중에는 상태와 음성·알림 안내만 표시한다. */
@Composable
fun DrivingHome(modifier: Modifier = Modifier) {
    VehicleScreen(modifier) {
        ScreenHeading(stringResource(R.string.driving_home_title))
        Text(stringResource(R.string.driving_home_body))
    }
}

/** 주정차 경고 안내와 확인 동작(T13·T17)을 표시한다. */
@Composable
fun ParkingWarningScreen(onAcknowledge: () -> Unit, modifier: Modifier = Modifier) {
    val model = PARKING_WARNING_CONTENT
    VehicleScreen(modifier) {
        ScreenHeading(stringResource(model.title))
        ScreenSection(warning = true) {
            model.body.forEach { Text(stringResource(it)) }
        }
        PrimaryAction(model.primaryAction, onAcknowledge)
    }
}

/** 주행 중 화면 미리보기. */
@Preview(showBackground = true)
@Composable
private fun DrivingHomePreview() {
    Annyeong_driveTheme { DrivingHome() }
}

/** 주정차 경고 화면 미리보기. */
@Preview(showBackground = true)
@Composable
private fun ParkingWarningPreview() {
    Annyeong_driveTheme { ParkingWarningScreen(onAcknowledge = {}) }
}
