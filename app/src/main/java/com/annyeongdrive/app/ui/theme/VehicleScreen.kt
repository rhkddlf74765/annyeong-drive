package com.annyeongdrive.app.ui.theme

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

internal val VehicleTargetHeight = 64.dp

/** 화면 폭에 맞추고 짧은 화면에서도 모든 내용을 스크롤할 수 있게 한다. */
@Composable
internal fun VehicleScreen(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
            Column(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
    }
}

/** 화면 제목을 접근성 탐색에서도 제목으로 표시한다. */
@Composable
internal fun ScreenHeading(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
}

/** 관련 안내를 한 카드에 묶고, 주의가 필요한 내용에는 경고 색상을 적용한다. */
@Composable
internal fun ScreenSection(warning: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (warning) colors.errorContainer else colors.surfaceVariant,
        contentColor = if (warning) colors.onErrorContainer else colors.onSurfaceVariant,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** 한 줄에 놓인 차량용 버튼이 화면 폭과 최소 터치 높이를 확보한다. */
private val VehicleButtonModifier = Modifier.fillMaxWidth().heightIn(min = VehicleTargetHeight)

/** 주요 동작 버튼. 번역된 문구는 줄바꿈을 허용한다. */
@Composable
internal fun PrimaryAction(@StringRes label: Int, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick = onClick, modifier = VehicleButtonModifier, enabled = enabled) {
        Text(stringResource(label), textAlign = TextAlign.Center)
    }
}

/** 보조 동작 버튼. 주요 동작과 같은 터치 높이를 확보한다. */
@Composable
internal fun SecondaryAction(@StringRes label: Int, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, modifier = VehicleButtonModifier, enabled = enabled) {
        Text(stringResource(label), textAlign = TextAlign.Center)
    }
}

/** 닫기나 나중에 보기처럼 강조가 덜 필요한 동작을 보여 준다. */
@Composable
internal fun QuietAction(@StringRes label: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = VehicleButtonModifier) {
        Text(stringResource(label), textAlign = TextAlign.Center)
    }
}
