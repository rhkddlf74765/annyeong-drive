package com.annyeongdrive.app.time

import java.time.Instant

/**
 * 현재 시각을 알려 준다. 도메인은 시스템 시계를 직접 부르지 않고 이것을 주입받는다.
 * 테스트에서는 시각을 직접 정하는 가짜 구현을 넣는다.
 */
fun interface Clock {

    /**
     * 현재 시각을 돌려준다.
     *
     * @return 현재 시각
     */
    fun now(): Instant
}

/** 기기의 시스템 시계를 쓰는 [Clock]. 앱 실행 시에만 쓴다. */
class DeviceClock : Clock {

    /**
     * 기기의 현재 시각을 돌려준다.
     *
     * @return 현재 시각
     */
    override fun now(): Instant = Instant.now()
}
