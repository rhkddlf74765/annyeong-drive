package com.annyeongdrive.app.ui.admin

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import com.annyeongdrive.app.R
import com.annyeongdrive.app.admin.ReturnScreenState
import com.annyeongdrive.app.ui.theme.Annyeong_driveTheme
import com.annyeongdrive.app.ui.theme.PrimaryAction
import com.annyeongdrive.app.ui.theme.ScreenHeading
import com.annyeongdrive.app.ui.theme.ScreenSection
import com.annyeongdrive.app.ui.theme.SecondaryAction
import com.annyeongdrive.app.ui.theme.VehicleScreen
import java.time.ZoneId

/** 반납 안내와 입력을 그린다. 제출·취소 동작은 반납 흐름으로 전달한다. */
@Composable
internal fun ReturnScreen(
    model: ReturnScreenModel,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var code by remember { mutableStateOf("") }
    VehicleScreen(modifier.imePadding()) {
        ScreenHeading(stringResource(R.string.return_title))
        ScreenSection {
            Text(stringResource(model.message, *model.messageArgs.toTypedArray()))
        }
        if (model.showCodeEntry) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.admin_code_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            PrimaryAction(R.string.action_confirm, onClick = { onSubmit(code) })
        }
        SecondaryAction(R.string.action_cancel, onCancel)
    }
}

/** 코드 입력 화면 미리보기. */
@Preview(showBackground = true)
@Composable
private fun ReturnScreenPreview() {
    Annyeong_driveTheme {
        ReturnScreen(
            ReturnScreenModel.of(ReturnScreenState.WrongCode(remainingAttempts = 3), ZoneId.systemDefault()),
            onSubmit = {},
            onCancel = {},
        )
    }
}

/** 반납 코드 미설정 화면 미리보기. */
@Preview(showBackground = true)
@Composable
private fun ReturnScreenNotSetPreview() {
    Annyeong_driveTheme {
        ReturnScreen(
            ReturnScreenModel.of(ReturnScreenState.AdminCodeNotSet, ZoneId.systemDefault()),
            onSubmit = {},
            onCancel = {},
        )
    }
}
