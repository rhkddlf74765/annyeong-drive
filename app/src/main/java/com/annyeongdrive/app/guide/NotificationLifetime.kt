package com.annyeongdrive.app.guide

import java.time.Duration

/**
 * 안내 알림을 띄워 두는 시간. 시간이 지나면 알림을 지워, 화면 위에 남은 알림이 앱 버튼을 가리지 않게 한다.
 * 주정차 경고는 두 문장을 읽을 시간을 더 준다. 경고 내용은 앱의 주정차 경고 화면에 남는다.
 * 근거: 11/15 스모크 확인(한국어 대여에서 주정차 경고 알림이 "반납 완료" 버튼을 가림), 11/15 사용자 결정(8초·10초).
 */
object NotificationLifetime {

    private val ROAD_RULE = Duration.ofSeconds(8)
    private val PARKING_WARNING = Duration.ofSeconds(10)

    /**
     * 알림 종류별로 띄워 두는 시간을 돌려준다.
     *
     * @param channel 알림 종류
     * @return 이 시간이 지나면 알림을 지운다
     */
    fun of(channel: GuidanceChannel): Duration = when (channel) {
        GuidanceChannel.ROAD_RULE -> ROAD_RULE
        GuidanceChannel.PARKING_WARNING -> PARKING_WARNING
    }
}
