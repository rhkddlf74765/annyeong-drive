package com.annyeongdrive.app.guide

/**
 * 통화 중에는 안내를 말하지 않고 알림으로만 띄우게 하는 출력 감싸기.
 * 안내 음성이 통화 소리와 겹치지 않게 한다(11/14 에뮬레이터 확인: 통화 중에도 끝까지 재생됐다, 사용자 결정 (가)).
 * 말하려던 안내는 같은 문장을 알림으로 띄워 정보가 빠지지 않게 한다. 원래 알림만 내던 안내는 그대로 둔다.
 * 통화 중인지는 바깥(Android)에서 받아 온다. 이 클래스는 Android API를 쓰지 않는다.
 *
 * @param inner 실제로 말하고 띄우는 출력
 * @param isInCall 지금 통화 중(벨이 울리는 중 포함)이면 true를 돌려주는 함수
 */
class CallAwareGuidanceOutput(
    private val inner: GuidanceOutput,
    private val isInCall: () -> Boolean,
) : GuidanceOutput {

    /**
     * 통화 중이면 말하기를 알림으로 바꿔 내보내고, 아니면 그대로 내보낸다.
     *
     * @param message 안내
     */
    override fun deliver(message: GuidanceMessage) {
        if (!message.speak) {
            inner.deliver(message)
            return
        }
        if (!isInCall()) {
            inner.deliver(message)
            return
        }
        inner.deliver(notificationOnly(message))
    }

    /**
     * 말하려던 안내를 알림으로만 띄우는 안내로 바꾼다. 알림 종류가 없으면 주행 중 규칙 안내 알림으로 띄운다.
     *
     * @param message 말하려던 안내
     * @return 말하지 않고 알림만 띄우는 안내
     */
    private fun notificationOnly(message: GuidanceMessage): GuidanceMessage {
        val channel = message.channel ?: GuidanceChannel.ROAD_RULE
        return message.copy(speak = false, channel = channel)
    }
}
