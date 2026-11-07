package com.annyeongdrive.app.speech

import java.util.Locale

/** 문장을 소리로 내는 쪽. Android TTS, Pleos TTS, 녹음 파일 재생 등으로 바꿔 끼운다. */
interface Speaker {
    fun speak(text: String, locale: Locale)

    /** 대기 중인 문장을 버리고 읽는 중인 문장을 멈춘다. 새 주행이 시작될 때 쓴다. */
    fun stopAll() {}

    fun shutdown()
}
