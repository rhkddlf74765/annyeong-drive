package com.annyeongdrive.app.location

import java.time.Duration
import java.time.Instant

/** 위치 값이 안내에 쓸 만큼 새것인지 보는 기준. 오래된 위치로 안내하지 않는다(기능 보드 "주행 중 규칙 안내" 5번). */
object LocationFreshness {

    /** 이보다 오래된 위치는 버린다. */
    val MAX_AGE: Duration = Duration.ofSeconds(10)

    /**
     * 위치를 잰 시각이 지금에서 [MAX_AGE] 안인지 판단한다. 기기 시계 차이로 미래 시각이 오면 새것으로 본다.
     *
     * @param measuredAt 위치를 잰 시각
     * @param now 지금 시각
     * @return 새것이면 true
     */
    fun isFresh(measuredAt: Instant, now: Instant): Boolean = Duration.between(measuredAt, now) <= MAX_AGE
}
