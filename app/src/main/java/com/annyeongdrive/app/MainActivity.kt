package com.annyeongdrive.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.annyeongdrive.app.ui.theme.Annyeong_driveTheme

/**
 * 앱의 첫 화면. 지금은 임시 홈이다.
 * 3-b에서 Coordinator의 현재 상태를 받아 그리도록 바꾸고, 5단계부터 실제 화면(언어 선택·정차 등)을 붙인다.
 * 기능 확인용 화면은 debug 빌드의 CheckActivity로 옮겼다.
 */
class MainActivity : ComponentActivity() {

    /**
     * 임시 홈 화면을 그린다.
     *
     * @param savedInstanceState 저장된 화면 상태
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Annyeong_driveTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PreparingHome(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

/**
 * 임시 홈 화면. 화면 문구는 strings.xml의 문구 ID로 읽는다.
 *
 * @param modifier 바깥에서 주는 배치 설정
 */
@Composable
fun PreparingHome(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.home_preparing),
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(24.dp),
    )
}

/** 임시 홈 화면 미리보기. */
@Preview(showBackground = true)
@Composable
private fun PreparingHomePreview() {
    Annyeong_driveTheme {
        PreparingHome()
    }
}
