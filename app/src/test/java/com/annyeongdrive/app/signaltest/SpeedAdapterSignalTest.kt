package com.annyeongdrive.app.signaltest

import com.annyeongdrive.app.vehicle.car.CarSpeedSource
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 차속 어댑터: 가짜 차량 연결이 보낸 차속 원값(m/s)이 km/h로 바뀌어 나간다. 모르는 값은 알리지 않는다.
 * 근거: 11/14 SDK 확인(Pleos SDK에 차속 API 없음 → android.car PERF_VEHICLE_SPEED, m/s), 11/14 사용자 결정(실제 차속 연결),
 * 11/15 사용자 결정(SDK 값을 임의로 넣어 확인하는 신호 테스트).
 */
class SpeedAdapterSignalTest {

    private val feed = FakeSpeedFeed()
    private val log = RecordingSignalLog()
    private val received = mutableListOf<Float>()

    /**
     * 어댑터를 만들고 구독을 시작한다.
     *
     * @return 구독 중인 어댑터
     */
    private fun started(): CarSpeedSource {
        val source = CarSpeedSource(feed, log)
        source.start { received += it }
        return source
    }

    /** 시작하면 현재 차속을 알리고, 이후 원값을 km/h로 바꾸며 후진 음수는 크기만 쓰는지 확인한다. */
    @Test
    fun `차속 원값을 km_h로 바꾸고 후진 음수는 크기만 쓴다`() {
        // 상황
        started()
        // 행동
        feed.send(10f)
        feed.send(-2.5f)
        // 결과
        assertArrayEquals(floatArrayOf(0f, 36f, 9f), received.toFloatArray(), 0.001f)
    }

    /** 사용할 수 없는 상태의 값은 정차(0)로 바꾸지 않고 알리지 않는지 확인한다. */
    @Test
    fun `사용할 수 없는 차속 값은 정차로 보지 않고 알리지 않는다`() {
        // 상황
        started()
        received.clear()
        // 행동
        feed.send(null)
        // 결과
        assertTrue(received.isEmpty())
    }

    /** 차속 권한이 없으면 구독하지 않고 아무 값도 알리지 않으며 남기는지 확인한다(권한 거부 시 정차·주행을 알 수 없음). */
    @Test
    fun `차속 권한이 없으면 구독하지 않고 아무 값도 알리지 않는다`() {
        // 상황
        feed.readable = false
        // 행동
        started()
        // 결과
        assertFalse(feed.subscribed)
        assertTrue(received.isEmpty())
        assertEquals(1, log.messages.size)
    }

    /** 멈추면 구독을 놓고, 그 뒤 값은 알리지 않는지 확인한다. */
    @Test
    fun `멈추면 구독을 놓고 이후 값은 알리지 않는다`() {
        // 상황
        val source = started()
        received.clear()
        // 행동
        source.stop()
        feed.send(10f)
        // 결과
        assertFalse(feed.subscribed)
        assertTrue(received.isEmpty())
    }
}
