package com.annyeongdrive.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.annyeongdrive.app.R
import com.annyeongdrive.app.admin.ReturnScreenState
import com.annyeongdrive.app.ui.theme.Annyeong_driveTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 잠금 해제 시각을 보여 줄 형식. */
private val LOCK_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

/**
 * 잠금 해제 시각을 기기 시간대의 시:분:초로 바꾼다.
 *
 * @param until 잠금 해제 시각
 * @return 보여 줄 글자
 */
internal fun lockTimeText(until: Instant): String = LOCK_TIME_FORMAT.format(until.atZone(ZoneId.systemDefault()))

/**
 * 반납 처리 화면. 상태(ReturnScreenState)를 그리기만 하고 판단은 ReturnCodeFlow가 한다.
 *
 * @param state 반납 처리 화면 상태
 * @param onSubmit 코드를 입력하고 확인을 눌렀을 때
 * @param onCancel 취소를 눌렀을 때
 * @param modifier 바깥에서 주는 배치 설정
 */
@Composable
fun ReturnScreen(
    state: ReturnScreenState,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var code by remember { mutableStateOf("") }
    val canEnter = state !is ReturnScreenState.AdminCodeNotSet && state !is ReturnScreenState.ConfigUnreadable
    Column(modifier = modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.return_title), style = MaterialTheme.typography.titleLarge)
        Text(returnMessage(state))
        if (canEnter) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text(stringResource(R.string.admin_code_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canEnter) {
                Button(onClick = { onSubmit(code) }) { Text(stringResource(R.string.action_confirm)) }
            }
            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

/**
 * 반납 처리 화면 상태에 맞는 안내 문구를 고른다.
 *
 * @param state 반납 처리 화면 상태
 * @return 안내 문구
 */
@Composable
private fun returnMessage(state: ReturnScreenState): String = when (state) {
    ReturnScreenState.Entry -> stringResource(R.string.return_entry)
    is ReturnScreenState.WrongCode -> stringResource(R.string.code_wrong, state.remainingAttempts)
    is ReturnScreenState.Locked -> stringResource(R.string.code_locked, lockTimeText(state.until))
    ReturnScreenState.AdminCodeNotSet -> stringResource(R.string.return_code_not_set)
    ReturnScreenState.ConfigUnreadable -> stringResource(R.string.config_unreadable)
}

/** 반납 처리 화면 미리보기(입력 중). */
@Preview(showBackground = true)
@Composable
private fun ReturnScreenPreview() {
    Annyeong_driveTheme {
        ReturnScreen(ReturnScreenState.WrongCode(remainingAttempts = 3), onSubmit = {}, onCancel = {})
    }
}

/** 반납 처리 화면 미리보기(코드 미설정). */
@Preview(showBackground = true)
@Composable
private fun ReturnScreenNotSetPreview() {
    Annyeong_driveTheme {
        ReturnScreen(ReturnScreenState.AdminCodeNotSet, onSubmit = {}, onCancel = {})
    }
}
