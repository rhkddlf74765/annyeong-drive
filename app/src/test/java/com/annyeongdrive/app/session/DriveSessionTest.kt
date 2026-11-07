package com.annyeongdrive.app.session

import com.annyeongdrive.app.guide.EncounterBasedPolicy
import com.annyeongdrive.app.guide.EnglishPhrasebook
import com.annyeongdrive.app.speech.Speaker
import com.annyeongdrive.app.zones.SpeedCamera
import com.annyeongdrive.app.zones.ZoneData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** 주행 단위로 상태가 나뉘는지 검증한다. 북쪽 직선 도로, 1초마다 11.1m. */
class DriveSessionTest {

    private val baseLat = 37.6000
    private val lon = 127.0300
    private fun latAt(m: Double) = baseLat + m / 111_195.0

    private val zones = ZoneData(
        schoolZones = emptyList(),
        speedCameras = listOf(
            SpeedCamera("C1", latAt(150.0), lon, 50, "none", "출발 직후 카메라"),
            SpeedCamera("C2", latAt(900.0), lon, 50, "none", "두 번째 카메라"),
        ),
        parkingCameras = emptyList(),
    )

    private class FakeSpeaker : Speaker {
        val spoken = mutableListOf<String>()
        var stopCount = 0
        override fun speak(text: String, locale: Locale) { spoken += text }
        override fun stopAll() { stopCount += 1 }
        override fun shutdown() {}
    }

    /** LocationService와 같은 방식으로 주행을 나눠 돌린다. */
    private class Runner(private val zones: ZoneData, val speaker: FakeSpeaker = FakeSpeaker()) {
        private val boundary = JumpTripBoundary()
        private var session: DriveSession? = null
        var sessions = 0
        fun feed(lat: Double, lon: Double, t: Long) {
            if (boundary.isNewTrip(lat, lon, t) || session == null) {
                if (session != null) speaker.stopAll()
                sessions += 1
                session = DriveSession(sessions, t, zones, EnglishPhrasebook, EncounterBasedPolicy(), speaker)
            }
            session!!.onLocation(lat, lon, t)
        }
    }

    private fun drive(r: Runner, untilM: Double, startT: Long): Long {
        var t = startT
        var m = 0.0
        while (m <= untilM) { r.feed(latAt(m), lon, t); m += 11.1; t += 1000 }
        return t
    }

    @Test
    fun `한 주행 안에서는 두 번째 카메라가 1문장`() {
        val r = Runner(zones)
        drive(r, 1000.0, 0)
        assertEquals(1, r.sessions)
        assertEquals(2, r.speaker.spoken.size)
        assertTrue(r.speaker.spoken[0].contains("Tickets"))      // 처음: 2문장
        assertEquals("Speed camera ahead. Limit 50.", r.speaker.spoken[1]) // 반복: 1문장
    }

    @Test
    fun `출발 직후 멈추고 처음부터 다시 달리면 새 주행으로 똑같이 안내`() {
        val r = Runner(zones)
        var t = drive(r, 120.0, 0)                 // 약 11초 달리다 멈춤 (첫 카메라 안내됨)
        repeat(5) { r.feed(latAt(120.0), lon, t); t += 1000 }
        drive(r, 120.0, t)                         // 출발점으로 돌아가 다시 시작
        assertEquals(2, r.sessions)
        assertEquals(2, r.speaker.spoken.size)
        assertEquals(r.speaker.spoken[0], r.speaker.spoken[1])   // 두 번 모두 처음처럼 2문장
    }

    @Test
    fun `출발하자마자 3초 만에 멈추고 다시 시작해도 새 주행이고 이전 음성은 끊는다`() {
        val r = Runner(zones)
        var t = drive(r, 33.0, 0)                  // 3초, 약 33m (첫 카메라 안내됨)
        repeat(3) { r.feed(latAt(33.3), lon, t); t += 1000 }
        drive(r, 120.0, t)                         // 뒤로 33m 튀어 다시 시작
        assertEquals(2, r.sessions)
        assertEquals(1, r.speaker.stopCount)
        assertEquals(2, r.speaker.spoken.size)
        assertEquals(r.speaker.spoken[0], r.speaker.spoken[1])
    }

    @Test
    fun `정상 주행과 작은 GPS 흔들림은 새 주행으로 보지 않는다`() {
        val b = JumpTripBoundary()
        assertTrue(b.isNewTrip(latAt(0.0), lon, 0))               // 첫 위치
        assertFalse(b.isNewTrip(latAt(11.1), lon, 1000))          // 40km/h
        assertFalse(b.isNewTrip(latAt(50.0), lon, 2000))          // 140km/h
        assertFalse(b.isNewTrip(latAt(40.0), lon, 3000))          // 뒤로 10m 흔들림: 20m 이하라 무시
        assertFalse(b.isNewTrip(latAt(52.0), lon, 4000))          // 다시 앞으로
        assertTrue(b.isNewTrip(latAt(20.0), lon, 5000))           // 1초에 뒤로 32m: 새 주행
        assertTrue(b.isNewTrip(latAt(320.0), lon, 6000))          // 1초에 앞으로 300m: 새 주행
    }

}
