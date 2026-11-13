package com.annyeongdrive.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.annyeongdrive.app.R
import com.annyeongdrive.app.ui.theme.Annyeong_driveTheme

/**
 * 주행 중 화면. 버튼 없이 상태만 보여 준다. 안내는 음성과 알림으로 하고, 교육은 정차한 뒤 이어 볼 수 있다고 알린다.
 * 상태 머신: 주행(T9·T10·B11) 동안
 *
 * @param modifier 바깥에서 주는 배치 설정
 */
@Composable
fun DrivingHome(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.driving_home_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.driving_home_body))
    }
}

/**
 * 주정차 경고 화면. 금지를 확정하지 않고 표지판 확인을 요청한다. 문장은 알림과 같은 주정차 안내 문장이다.
 * 상태 머신: 들어옴 T13, 나감 T17("확인했어요")
 *
 * @param onAcknowledge "확인했어요"를 눌렀을 때
 * @param modifier 바깥에서 주는 배치 설정
 */
@Composable
fun ParkingWarningScreen(onAcknowledge: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.guide_channel_parking), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.guide_parking_core))
        Text(stringResource(R.string.guide_parking_consequence))
        Button(onClick = onAcknowledge) { Text(stringResource(R.string.parking_warning_acknowledge)) }
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
