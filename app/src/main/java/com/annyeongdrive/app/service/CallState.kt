package com.annyeongdrive.app.service

import android.content.Context
import android.media.AudioManager

/**
 * 지금 통화 중인지 오디오 모드로 알려 준다. 벨이 울리는 중, 일반 통화, 인터넷 통화를 통화 중으로 본다.
 * 전화 상태 권한 없이 알 수 있는 오디오 모드를 쓴다. 판단 없이 값만 읽는다.
 *
 * @param context 앱 Context
 */
class CallState(context: Context) {

    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)

    /**
     * 지금 통화 중인지 알려 준다.
     *
     * @return 벨이 울리거나 통화 중이면 true
     */
    fun isInCall(): Boolean = audio.mode in CALL_MODES

    private companion object {
        /** 통화로 보는 오디오 모드. */
        val CALL_MODES = setOf(
            AudioManager.MODE_RINGTONE,
            AudioManager.MODE_IN_CALL,
            AudioManager.MODE_IN_COMMUNICATION,
            AudioManager.MODE_CALL_SCREENING,
        )
    }
}
