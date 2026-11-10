package com.annyeongdrive.app.speech

import ai.pleos.playground.tts.constant.ErrorType
import android.content.Context
import android.util.Log
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 앱 전체에서 Pleos TTS 객체를 하나만 쓰게 한다.
 * Pleos TTS SDK는 객체가 둘이면 끝 신호(onDone)를 한쪽에만 보낸다(11/8 확인).
 * 서비스와 화면(TTS 확인 버튼)이 모두 여기서 같은 Speaker를 받는다.
 */
object SpeechHub {

    fun interface ResultListener {
        fun onResult(text: String, locale: Locale, error: ErrorType?)
    }

    fun interface ReadyListener {
        fun onReady(success: Boolean, error: ErrorType?)
    }

    private var speaker: PleosTtsSpeaker? = null
    private var readyState: Pair<Boolean, ErrorType?>? = null
    private val resultListeners = CopyOnWriteArrayList<ResultListener>()
    private val readyListeners = CopyOnWriteArrayList<ReadyListener>()

    /** 없으면 만든다. 메인 스레드에서 부른다. */
    @Synchronized
    fun obtain(context: Context): Speaker {
        speaker?.let { return it }
        Log.d(PleosTtsSpeaker.TAG, "SpeechHub: create Pleos TTS")
        return PleosTtsSpeaker(
            context.applicationContext,
            onReady = { ok, err ->
                readyState = ok to err
                readyListeners.forEach { it.onReady(ok, err) }
            },
            onResult = { text, locale, err -> resultListeners.forEach { it.onResult(text, locale, err) } },
        ).also { speaker = it }
    }

    /** 이미 준비가 끝났으면 바로 알려 준다. */
    fun addReadyListener(listener: ReadyListener) {
        readyListeners += listener
        readyState?.let { (ok, err) -> listener.onReady(ok, err) }
    }

    fun removeReadyListener(listener: ReadyListener) {
        readyListeners -= listener
    }

    fun addResultListener(listener: ResultListener) {
        resultListeners += listener
    }

    fun removeResultListener(listener: ResultListener) {
        resultListeners -= listener
    }

    /** 서비스가 끝날 때 부른다. */
    @Synchronized
    fun release() {
        speaker?.shutdown()
        speaker = null
        readyState = null
    }
}
