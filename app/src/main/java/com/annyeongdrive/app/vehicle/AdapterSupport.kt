package com.annyeongdrive.app.vehicle

/**
 * 어댑터가 SDK 콜백을 엔진 쪽 스레드로 옮기는 창구. 엔진은 한 스레드에서만 이벤트를 받는다.
 * release는 메인 스레드에 올리고(PleosVehicle.onMain), JVM 테스트는 바로 실행하거나 모아 두었다가 실행한다.
 */
fun interface MainThread {

    /**
     * 할 일을 엔진 쪽 스레드에서 실행한다.
     *
     * @param block 실행할 일
     */
    fun post(block: () -> Unit)
}

/**
 * 어댑터가 구독 오류·사용 불가를 남기는 창구. release는 Android 로그([AndroidSignalLog]), JVM 테스트는 기록만 한다.
 * 어댑터가 android.util.Log를 직접 부르지 않아야 JVM에서 오류 경로까지 테스트된다.
 */
fun interface SignalLog {

    /**
     * 문제 하나를 남긴다.
     *
     * @param tag 어댑터 이름
     * @param message 무엇이 잘못됐는지
     * @param error 원인 예외. 없으면 null
     */
    fun report(tag: String, message: String, error: Throwable?)
}
