package com.annyeongdrive.app.speech

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale

/**
 * Pleos TextToSpeech SDK로 읽는다. 에뮬레이터에 Android 기본 TTS 엔진이 없어(11/8 확인) 이것을 쓴다.
 *
 * - 문장은 직접 줄을 세워 하나씩 읽는다. 한 문장이 끝나면(onDone/onError/onStop) 다음 문장을 읽는다.
 * - 문장마다 언어가 다르면 읽기 전에 setLocale을 부른다.
 * - SDK 콜백이 어느 스레드에서 오든 메인 스레드에서 처리한다.
 * - 끝 신호(onDone 등)가 오지 않아도 멈추지 않도록, 문장 길이에 비례한 시간이 지나면 다음 문장으로 넘어간다.
 * - 주의: addEventListener 등은 initialize 성공 뒤에만 부른다. 그 전에 부르면 SDK 내부 객체가 없어 죽는다(11/8 확인).
 */
class PleosTtsSpeaker(
    context: Context,
    mode: Mode = Mode.ON_DEVICE,
    private val onReady: (success: Boolean, error: ErrorType?) -> Unit = { _, _ -> },
    private val onResult: (text: String, locale: Locale, error: ErrorType?) -> Unit = { _, _, _ -> },
) : Speaker {

    private data class Item(val text: String, val locale: Locale)

    private val main = Handler(Looper.getMainLooper())
    private val tts = TextToSpeech(context.applicationContext, mode)
    private val queue = ArrayDeque<Item>()
    private var current: Item? = null
    private var currentLocale: Locale? = null
    private var ready = false
    private var failed = false
    private var released = false
    /** stopAll로 멈춘 문장의 늦은 끝 신호(onStop 등)를 한 번 무시한다. */
    private var swallowNextFinish = false

    /** 끝 신호가 안 올 때 다음 문장으로 넘기는 장치. */
    private val watchdog = Runnable {
        current?.let { Log.w(TAG, "no finish event, moving on: ${it.text}") }
        finish(null)
    }

    private val events = object : EventListener {
        override fun onStart() {
            Log.d(TAG, "event onStart")
        }

        override fun onReady() {
            Log.d(TAG, "event onReady")
        }

        override fun onUpdatedRms(rms: Double) = Unit

        override fun onDone() {
            Log.d(TAG, "event onDone")
            main.post { finish(null) }
        }

        override fun onStop() {
            Log.d(TAG, "event onStop")
            main.post { finish(null) }
        }

        override fun onError(errorType: ErrorType) {
            Log.d(TAG, "event onError $errorType")
            main.post { finish(errorType) }
        }
    }

    init {
        try {
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    main.post { onInitSuccess(mode) }
                }

                override fun onFailure(errorType: ErrorType) {
                    main.post { onInitFailure(errorType.name, errorType) }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Pleos TTS initialize threw", e)
            main.post { onInitFailure(e.javaClass.simpleName, null) }
        }
    }

    private fun onInitSuccess(mode: Mode) {
        if (released) {
            runCatching { tts.release() }   // 정리된 뒤에 초기화가 끝난 경우
            return
        }
        try {
            tts.addEventListener(events)
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Pleos TTS setup after init threw", e)
            onInitFailure(e.javaClass.simpleName, null)
            return
        }
        ready = true
        Log.d(TAG, "Pleos TTS ready (mode=$mode)")
        onReady(true, null)
        next()
    }

    private fun onInitFailure(reason: String, error: ErrorType?) {
        failed = true
        Log.w(TAG, "Pleos TTS init failed: $reason, ${queue.size} sentence(s) dropped")
        queue.clear()
        onReady(false, error)
    }

    override fun speak(text: String, locale: Locale) {
        main.post {
            if (failed) {
                Log.w(TAG, "TTS unavailable, skip: $text")
                return@post
            }
            queue.addLast(Item(text, locale))
            next()
        }
    }

    private fun next() {
        if (!ready || current != null) return
        val item = queue.removeFirstOrNull() ?: return
        current = item
        if (item.locale != currentLocale) {
            tts.setLocale(item.locale)
            currentLocale = item.locale
        }
        Log.i(TAG, "speak[${item.locale}] (queue=${queue.size}): ${item.text}")
        main.postDelayed(watchdog, timeoutMs(item.text))
        try {
            tts.speak(item.text, true)
        } catch (e: Exception) {
            Log.e(TAG, "speak threw", e)
            finish(null)
        }
    }

    private fun finish(error: ErrorType?) {
        if (swallowNextFinish) {
            swallowNextFinish = false
            Log.d(TAG, "late finish event of stopped sentence ignored")
            return
        }
        main.removeCallbacks(watchdog)
        val item = current ?: return
        current = null
        if (error != null) Log.w(TAG, "speak failed[${item.locale}] $error: ${item.text}")
        onResult(item.text, item.locale, error)
        next()
    }

    override fun stopAll() {
        main.post {
            val dropped = queue.size
            queue.clear()
            main.removeCallbacks(watchdog)
            val wasSpeaking = current != null
            current = null
            if (wasSpeaking && ready) {
                swallowNextFinish = true
                // 늦은 신호가 끝내 안 오면 다음 문장을 막지 않도록 잠시 뒤 해제한다
                main.postDelayed({ swallowNextFinish = false }, 1_000L)
                runCatching { tts.stop() }
            }
            Log.i(TAG, "stopAll: speaking=$wasSpeaking dropped=$dropped")
        }
    }

    override fun shutdown() {
        released = true
        main.removeCallbacksAndMessages(null)
        queue.clear()
        current = null
        if (!ready) return   // 초기화 전이면 SDK 내부 객체가 없어 정리할 것도 없다
        ready = false
        runCatching { tts.removeEventListener(events) }
        runCatching { tts.stop() }
        runCatching { tts.release() }
    }

    /** 영어 기준 초당 약 12자 + 여유 3초, 최대 20초. */
    private fun timeoutMs(text: String): Long = (3_000L + text.length * 85L).coerceAtMost(20_000L)

    companion object {
        const val TAG = "AnnyeongTts"
    }
}
