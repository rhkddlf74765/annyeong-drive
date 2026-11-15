package com.annyeongdrive.app.vehicle

import android.util.Log

/** 어댑터 문제를 Android 로그(오류 수준)에 남긴다. release 어댑터가 쓴다. */
object AndroidSignalLog : SignalLog {

    /**
     * 문제 하나를 Android 로그에 남긴다.
     *
     * @param tag 어댑터 이름
     * @param message 무엇이 잘못됐는지
     * @param error 원인 예외. 없으면 null
     */
    override fun report(tag: String, message: String, error: Throwable?) {
        Log.e(tag, message, error)
    }
}
