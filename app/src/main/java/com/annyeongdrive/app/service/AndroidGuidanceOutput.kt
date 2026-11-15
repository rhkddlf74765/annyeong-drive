package com.annyeongdrive.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.annyeongdrive.app.MainActivity
import com.annyeongdrive.app.R
import com.annyeongdrive.app.guide.GuidanceChannel
import com.annyeongdrive.app.guide.GuidanceMessage
import com.annyeongdrive.app.guide.GuidanceOutput
import com.annyeongdrive.app.guide.GuidanceSentence
import com.annyeongdrive.app.guide.NotificationLifetime
import com.annyeongdrive.app.guide.voiceLocaleOf
import com.annyeongdrive.app.speech.SpeechHub
import java.util.Locale

/**
 * 안내를 실제로 말하고 heads-up 알림으로 띄운다. 문장은 대여 언어의 strings.xml에서 읽는다(기기 언어가 아니라).
 * 음성은 앱 전체에서 하나인 Pleos TTS(SpeechHub), 알림은 채널별 중요도 높음(heads-up)이다. 알림을 누르면 앱이 열린다.
 * 알림 권한이 없으면 알림은 건너뛰고 음성만 낸다.
 *
 * @param context 앱 Context
 */
class AndroidGuidanceOutput(context: Context) : GuidanceOutput {

    private val appContext = context.applicationContext
    private val notifications = appContext.getSystemService(NotificationManager::class.java)
    private var nextId = FIRST_NOTIFICATION_ID

    /**
     * 안내 하나를 말하고 띄운다.
     *
     * @param message 안내
     */
    override fun deliver(message: GuidanceMessage) {
        val locale = localeOf(message)
        val text = textOf(message.sentences, locale)
        if (message.speak) SpeechHub.obtain(appContext).speak(text, locale)
        val channel = message.channel ?: return
        notify(channel, text)
    }

    /**
     * 안내 언어의 Locale을 고른다. 나라까지 붙인 값이다([voiceLocaleOf]). 대여가 없으면 기기 기본 Locale이다.
     *
     * @param message 안내
     * @return 문장을 읽고 말할 Locale
     */
    private fun localeOf(message: GuidanceMessage): Locale {
        val language = message.language ?: return Locale.getDefault()
        return voiceLocaleOf(language)
    }

    /**
     * 문장 ID들을 그 언어의 문장으로 바꿔 한 덩어리로 잇는다.
     *
     * @param sentences 문장 ID 목록
     * @param locale 언어
     * @return 읽을 문장
     */
    private fun textOf(sentences: List<GuidanceSentence>, locale: Locale): String {
        val configuration = Configuration(appContext.resources.configuration)
        configuration.setLocale(locale)
        val localized = appContext.createConfigurationContext(configuration)
        return sentences.joinToString(" ") { sentenceText(localized, it) }
    }

    /**
     * 문장 하나를 읽는다. 제한속도가 있으면 넣는다.
     *
     * @param localized 대여 언어의 Context
     * @param sentence 문장 ID와 제한속도
     * @return 문장
     */
    private fun sentenceText(localized: Context, sentence: GuidanceSentence): String {
        val limit = sentence.limitKmh ?: return localized.getString(sentence.textId)
        return localized.getString(sentence.textId, limit)
    }

    /**
     * 채널을 만들고 heads-up 알림을 띄운다. 알림 권한이 없으면 띄우지 않는다. 정해진 시간([NotificationLifetime])이 지나면 알림을 지운다.
     *
     * @param channel 알림 종류
     * @param text 알림 문장
     */
    private fun notify(channel: GuidanceChannel, text: String) {
        if (!canNotify()) return
        val channelId = ensureChannel(channel)
        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(appContext.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setAutoCancel(true)
            .setTimeoutAfter(NotificationLifetime.of(channel).toMillis())
            .setContentIntent(openAppIntent())
            .build()
        notifications.notify(nextId, notification)
        nextId += 1
    }

    /**
     * 알림을 띄울 수 있는지 알려 준다.
     *
     * @return 알림 권한이 있으면 true
     */
    private fun canNotify(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * 알림 종류의 채널을 만든다(이미 있으면 그대로).
     *
     * @param channel 알림 종류
     * @return 채널 ID
     */
    private fun ensureChannel(channel: GuidanceChannel): String {
        val (id, nameId) = when (channel) {
            GuidanceChannel.PARKING_WARNING -> PARKING_CHANNEL to R.string.guide_channel_parking
            GuidanceChannel.ROAD_RULE -> ROAD_RULE_CHANNEL to R.string.guide_channel_road_rule
        }
        val created = NotificationChannel(id, appContext.getString(nameId), NotificationManager.IMPORTANCE_HIGH)
        notifications.createNotificationChannel(created)
        return id
    }

    /**
     * 알림을 누르면 앱 첫 화면을 여는 PendingIntent를 만든다.
     *
     * @return 앱을 여는 PendingIntent
     */
    private fun openAppIntent(): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(appContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private companion object {
        /** 주정차 경고 채널(기능 보드 "주정차 경고" 2번). */
        const val PARKING_CHANNEL = "parking_warning"

        /** 주행 중 규칙 안내 채널(음성을 쓸 수 없는 언어일 때). */
        const val ROAD_RULE_CHANNEL = "road_rule"

        /** 상주 알림(서비스)과 겹치지 않는 첫 알림 번호. */
        const val FIRST_NOTIFICATION_ID = 2000
    }
}
