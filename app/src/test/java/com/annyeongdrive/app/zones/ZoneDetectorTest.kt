package com.annyeongdrive.app.zones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 북쪽으로 똑바로 달리는 가상 도로에서 판단기를 검증한다.
 * 위도 0.0001도 ≈ 11.1m, 1초마다 한 점(약 40km/h).
 */
class ZoneDetectorTest {

    private val baseLat = 37.6000
    private val lon = 127.0300
    private fun latAt(meters: Double) = baseLat + meters / 111_195.0

    private val data = ZoneData(
        schoolZones = listOf(
            SchoolZone("SZ1", "테스트초", latAt(1000.0), lon + 0.002, 300.0, 30), // 도로에서 동쪽 약 176m
        ),
        speedCameras = listOf(
            SpeedCamera("C1", latAt(500.0), lon, 50, "none", "앞 카메라"),
            SpeedCamera("C2", latAt(520.0), lon + 0.0002, 50, "none", "건너편 카메라"), // 같은 지점 중복
            SpeedCamera("C3", latAt(600.0), lon + 0.003, 30, "school", "옆길 카메라"),   // 동쪽 약 264m
        ),
        parkingCameras = listOf(
            ParkingCamera("P1", latAt(1600.0), lon + 0.0004, "끝 지점 카메라"),
        ),
    )

    private fun drive(): List<ZoneEvent> {
        val detector = ZoneDetector(data)
        val events = mutableListOf<ZoneEvent>()
        var t = 0L
        var m = 0.0
        while (m <= 1600.0) {
            events += detector.update(latAt(m), lon, t)
            m += 11.1; t += 1000
        }
        repeat(8) { events += detector.update(latAt(1600.0), lon, t); t += 1000 } // 도착 후 정차
        return events
    }

    @Test
    fun `앞 카메라는 한 번만, 건너편 중복과 옆길 카메라는 제외`() {
        val cams = drive().filterIsInstance<ZoneEvent.SpeedCameraAhead>()
        assertEquals(listOf("C1"), cams.map { it.camera.id })
        assertTrue(cams.first().distanceM <= 250.0)
    }

    @Test
    fun `보호구역은 진입과 이탈이 한 번씩`() {
        val ev = drive()
        assertEquals(1, ev.count { it is ZoneEvent.SchoolZoneEntered })
        assertEquals(1, ev.count { it is ZoneEvent.SchoolZoneExited })
        val enterIdx = ev.indexOfFirst { it is ZoneEvent.SchoolZoneEntered }
        val exitIdx = ev.indexOfFirst { it is ZoneEvent.SchoolZoneExited }
        assertTrue(enterIdx < exitIdx)
    }

    @Test
    fun `도착 후 정차하면 가까운 주정차 카메라를 한 번 알린다`() {
        val stops = drive().filterIsInstance<ZoneEvent.StoppedNearParkingCamera>()
        assertEquals(listOf("P1"), stops.map { it.camera.id })
    }
}
