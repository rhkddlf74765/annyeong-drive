package com.annyeongdrive.app.debugtools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.gear.Gear
import com.annyeongdrive.app.rental.AppLanguage
import com.annyeongdrive.app.service.DriveRuntime
import com.annyeongdrive.app.vehicle.UxRestriction

/**
 * 가짜 신호 조작 영역(debug 빌드 전용). 버튼은 가짜 입력 신호의 값만 바꾸거나 사용자 동작 이벤트를 보내고,
 * 판단은 지금처럼 엔진과 Coordinator가 한다. 지금 운전 상태를 함께 보여 줘 상태 머신이 어떻게 움직이는지 본다.
 * 사용자 동작 버튼은 상태 머신만 움직인다. 대여 저장(언어·점검 기록)은 5단계 화면이 맡으므로 여기서는 하지 않는다.
 *
 * @param runtime 앱 전체에서 하나인 조립 지점
 * @param modifier 바깥에서 주는 배치 설정
 */
@Composable
fun SignalPanel(runtime: DriveRuntime, modifier: Modifier = Modifier) {
    val state by runtime.engine.state.collectAsState()
    val signals = runtime.fakeSignals
    val send: (DriveEvent) -> Unit = runtime.engine::send

    Column(modifier = modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("가짜 신호 조작 · 운전 상태", style = MaterialTheme.typography.titleMedium)
        Text("지금 상태: $state", style = MaterialTheme.typography.titleLarge)

        ButtonRow(
            "기어",
            listOf(
                "P" to { signals.gear.shift(Gear.P) },
                "R" to { signals.gear.shift(Gear.R) },
                "N" to { signals.gear.shift(Gear.N) },
                "D" to { signals.gear.shift(Gear.D) },
            ),
        )
        ButtonRow(
            "차속",
            listOf(
                "0 km/h" to { signals.speed.changeSpeed(0f) },
                "30 km/h" to { signals.speed.changeSpeed(30f) },
            ),
        )
        ButtonRow(
            "운전석",
            listOf(
                "앉기" to { signals.driverSeat.sit() },
                "일어나기" to { signals.driverSeat.leave() },
            ),
        )
        ButtonRow(
            "UX 제한",
            listOf(
                "켜기" to { signals.ux.change(UxRestriction.RESTRICTED) },
                "끄기" to { signals.ux.change(UxRestriction.NONE) },
            ),
        )
        ButtonRow(
            "대여 설정",
            listOf(
                "언어 선택(영어) T7" to { send(DriveEvent.LanguageSelected(AppLanguage.ENGLISH)) },
                "점검 완료 T8" to { send(DriveEvent.PrecheckCompleted) },
            ),
        )
        ButtonRow(
            "정차",
            listOf(
                "경고 확인 T17·T18" to { send(DriveEvent.WarningAcknowledged) },
                "이어 보기 T19" to { send(DriveEvent.ResumeAccepted) },
                "나중에 보기 T20" to { send(DriveEvent.ResumePostponed) },
                "교육 열기 T21" to { send(DriveEvent.LearningOpenRequested) },
                "도움 메뉴 T22" to { send(DriveEvent.HelpRequested) },
                "도움 닫기 B5" to { send(DriveEvent.HelpClosed) },
                "업체 설정 B9" to { send(DriveEvent.VendorSettingsRequested) },
                "업체 설정 닫기 B10" to { send(DriveEvent.VendorSettingsClosed) },
            ),
        )
        ButtonRow(
            "교육·퀴즈",
            listOf(
                "교육 시작 T25" to { send(DriveEvent.LearningBegin) },
                "퀴즈 시작 T26" to { send(DriveEvent.QuizStartRequested) },
                "정답 확인 T27" to { send(DriveEvent.AnswerChecked) },
                "건너뛰기 T28·B6" to { send(DriveEvent.QuestionSkipped) },
                "다음 T29·T30" to { send(DriveEvent.NextRequested) },
                "교육 닫기 T23" to { send(DriveEvent.LearningClosed) },
            ),
        )
        ButtonRow(
            "반납",
            listOf(
                "반납 완료 T31" to { send(DriveEvent.ReturnRequested) },
                "코드 확인됨 T32" to { send(DriveEvent.AdminCodeVerified) },
                "반납 취소 B3" to { send(DriveEvent.ReturnCancelled) },
            ),
        )
    }
}

/**
 * 이름표와 버튼 한 줄. 화면 폭이 좁으면 버튼이 다음 줄로 넘어간다.
 *
 * @param label 줄 이름
 * @param buttons 버튼 글자와 누르면 할 일
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ButtonRow(label: String, buttons: List<Pair<String, () -> Unit>>) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        buttons.forEach { (text, onClick) ->
            OutlinedButton(onClick = onClick) { Text(text) }
        }
    }
}
