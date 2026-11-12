package com.annyeongdrive.app.speech

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Android 기본 TTS로 읽는다. 엔진 준비 전에 들어온 문장은 모아 뒀다가 준비되면 읽는다.
 * 문장은 순서대로 이어서 읽는다(QUEUE_ADD).
 *
 * 주의: 엔진을 찾지 못하면 초기화 콜백이 TextToSpeech 생성자 안에서 바로 불린다(11/8 에뮬레이터에서 확인).
 * 그래서 콜백에서 tts를 바로 쓰지 않고, 생성이 끝난 뒤 처리한다.
 */
class AndroidTtsSpeaker(
    context: Context,
    private val onReady: (speaker: AndroidTtsSpeaker, success: Boolean) -> Unit = { _, _ -> },
) : Speaker {

    private var tts: TextToSpeech? = null
    private var earlyStatus: Int? = null
    private var ready = false
    private val pending = ArrayDeque<Pair<String, Locale>>()
    private val ids = AtomicInteger()

    /** 설치된 TTS 엔진 이름들(확인용). */
    var engines: List<String> = emptyList()
        private set

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (tts == null) earlyStatus = status   // 생성자 안에서 바로 불린 경우
            else handleInit(status)
        }
        earlyStatus?.let(::handleInit)
    }

    private fun handleInit(status: Int) {
        val engine = tts ?: return
        engines = engine.engines.map { it.name }
        ready = status == TextToSpeech.SUCCESS
        Log.d(TAG, "TTS init status=$status default=${engine.defaultEngine} engines=$engines")
        if (ready) {
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            while (pending.isNotEmpty()) {
                val (text, locale) = pending.removeFirst()
                speakNow(text, locale)
            }
        } else {
            Log.w(TAG, "TTS unavailable. ${pending.size} pending sentence(s) dropped")
            pending.clear()
        }
        onReady(this, ready)
    }

    override fun speak(text: String, locale: Locale) {
        if (!ready) {
            if (earlyStatus != null && earlyStatus != TextToSpeech.SUCCESS) {
                Log.w(TAG, "TTS unavailable, skip: $text")
                return
            }
            pending.addLast(text to locale)
            return
        }
        speakNow(text, locale)
    }

    private fun speakNow(text: String, locale: Locale) {
        val engine = tts ?: return
        val lang = engine.setLanguage(locale)
        if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "language not available: $locale (code=$lang)")
        }
        val result = engine.speak(text, TextToSpeech.QUEUE_ADD, null, "u${ids.incrementAndGet()}")
        Log.i(TAG, "speak[$locale] result=$result: $text")
    }

    override fun stopAll() {
        pending.clear()
        tts?.stop()
    }

    /** 언어 지원 여부를 글로 돌려준다(기능 확인 ③용). 엔진 준비 뒤에 부른다. */
    fun languageStatus(locale: Locale): String {
        if (!ready) return "TTS 엔진 사용 불가"
        return when (tts?.isLanguageAvailable(locale)) {
            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_AVAILABLE,
            TextToSpeech.LANG_AVAILABLE -> "지원"
            TextToSpeech.LANG_MISSING_DATA -> "음성 데이터 없음"
            TextToSpeech.LANG_NOT_SUPPORTED -> "미지원"
            else -> "알 수 없음"
        }
    }

    override fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    companion object {
        const val TAG = "AnnyeongTts"
    }
}
