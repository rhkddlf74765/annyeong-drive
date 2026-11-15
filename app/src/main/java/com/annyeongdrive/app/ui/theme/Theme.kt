package com.annyeongdrive.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Mint,
    onPrimary = OnMint,
    background = NightBackground,
    onBackground = OnNavy,
    surface = NightSurface,
    onSurface = OnNavy,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = OnNavy,
    outline = NightOutline,
    error = Color(0xFFFFB4A9),
    onError = Color(0xFF5E120D),
    errorContainer = Color(0xFF592521),
    onErrorContainer = Color(0xFFFFDAD4),
)

private val LightColorScheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    background = DayBackground,
    onBackground = Navy,
    surface = Color.White,
    onSurface = Navy,
    surfaceVariant = DaySurfaceVariant,
    onSurfaceVariant = Navy,
    outline = DayOutline,
    error = Color(0xFF9D241D),
    onError = Color.White,
    errorContainer = Color(0xFFFFDFDA),
    onErrorContainer = Color(0xFF561410),
)

/** 차량 화면에 주야간 색상과 읽기 쉬운 글자 크기를 적용한다. */
@Composable
fun Annyeong_driveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
