package com.annyeongdrive.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.annyeongdrive.app.guide.voiceLocaleOf
import com.annyeongdrive.app.rental.AppLanguage

/**
 * 안쪽 화면의 문구를 대여 언어로 읽게 한다. 기기 언어가 아니라 운전자가 고른 언어를 따른다.
 * 대여가 없으면(언어 선택 전·반납 뒤) 기기 언어 그대로다. 음성 안내와 같은 Locale([voiceLocaleOf])을 쓴다.
 * Activity Context는 그대로 두고 리소스만 바꾼다. 입력 창 등 Activity가 필요한 부품이 그대로 동작하게 하기 위해서다.
 * 언어가 없을 때도 같은 모양으로 감싼다. 모양이 바뀌면 안쪽 화면이 새로 만들어져, 언어를 고른 직후 진행 중이던
 * 화면 작업(다음 단계로 넘기기)이 취소된다(11/14 에뮬레이터 확인).
 *
 * @param language 대여 언어. 대여가 없으면 null
 * @param content 안쪽 화면
 */
@Composable
fun RentalLanguage(language: AppLanguage?, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val localized = remember(base, language) { localizedContext(base, language) }
    val configuration = localized?.resources?.configuration ?: baseConfiguration
    CompositionLocalProvider(
        LocalContext provides (localized ?: base),
        LocalConfiguration provides configuration,
        content = content,
    )
}

/**
 * 대여 언어로 리소스를 읽는 Context를 만든다.
 *
 * @param base 원래 Context
 * @param language 대여 언어. 대여가 없으면 null
 * @return 대여 언어 Context. 언어가 없으면 null(원래 Context를 그대로 쓴다)
 */
private fun localizedContext(base: Context, language: AppLanguage?): Context? {
    if (language == null) return null
    return LocalizedContext(base, voiceLocaleConfiguration(base, language))
}

/**
 * 기기 설정에 대여 언어 Locale만 바꾼 Configuration을 만든다.
 *
 * @param base 원래 Context
 * @param language 대여 언어
 * @return 대여 언어 Configuration
 */
private fun voiceLocaleConfiguration(base: Context, language: AppLanguage): Configuration {
    val configuration = Configuration(base.resources.configuration)
    configuration.setLocale(voiceLocaleOf(language))
    return configuration
}

/**
 * 리소스만 대여 언어로 바꾼 Context. 나머지는 원래 Context(Activity)에 맡긴다.
 *
 * @param base 원래 Context
 * @param configuration 대여 언어 Configuration
 */
private class LocalizedContext(base: Context, configuration: Configuration) : ContextWrapper(base) {

    private val localizedResources = base.createConfigurationContext(configuration).resources

    /**
     * 대여 언어 리소스를 돌려준다.
     *
     * @return 대여 언어 리소스
     */
    override fun getResources(): Resources = localizedResources
}
