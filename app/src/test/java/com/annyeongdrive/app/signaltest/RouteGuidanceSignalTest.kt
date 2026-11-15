package com.annyeongdrive.app.signaltest

import ai.pleos.playground.vehicle.constant.control.VehicleGear
import com.annyeongdrive.app.admin.MutableClock
import com.annyeongdrive.app.guide.GuidanceChannel
import com.annyeongdrive.app.rental.AppLanguage
import com.annyeongdrive.app.zones.ZoneRoadRuleWatcher
import com.annyeongdrive.app.zones.ZoneStopPlaces
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * 시연 경로 주행 안내: 실제 구역 데이터(zones.json)와 실제 구역 판단으로 시연 경로(tools/route.gpx)를 1초 간격으로 달리면
 * 기대 목록 파일(tools/smoke/expected/route_*.json)과 같은 안내가 같은 순서로 나온다. 같은 파일로 스모크(에뮬레이터 E1·E2)가 실제 앱을 확인한다.
 * 근거: 기능 실행안 주행 중 규칙 상기(B11)·주정차 경고, 8단계 결정(영어 대여는 음성, 한국어 대여는 알림, 처음 2문장·이후 1문장),
 * 11/15 에뮬레이터 확인(경로 안내 6건 + 경로 끝 주정차 경고), 11/15 사용자 검수(스모크 시나리오 설계의 기대 목록).
 */
class RouteGuidanceSignalTest {

    /**
     * 기대 목록의 경로를 그 언어 대여로 끝까지 달리고 경로 끝에서 0 → P로 정차한다.
     *
     * @param expectation 기대 목록
     * @param language 대여 언어
     * @return 안내를 내보낸 장치와, 주행 안내마다 나온 경로 위치(km)
     */
    private fun driveRoute(expectation: RouteExpectation, language: AppLanguage): Pair<SignalRig, List<Double>> {
        val zones = RouteFixtures.zones()
        val clock = MutableClock(Instant.parse("2026-11-15T03:00:00Z"))
        val rig = SignalRig(ZoneRoadRuleWatcher(zones, clock), ZoneStopPlaces(zones))
        rig.language = language
        rig.startRented()
        rig.drive(expectation.kmh.toFloat())
        val spokenAtKm = mutableListOf<Double>()
        RouteFixtures.replayPoints(expectation.route, expectation.kmh).forEach { replay ->
            clock.now = clock.now.plusSeconds(1)
            val before = rig.delivered.size
            rig.location.moveTo(replay.point)
            repeat(rig.delivered.size - before) { spokenAtKm += replay.km }
        }
        rig.speed.sendKmh(0f)
        rig.gear.select(VehicleGear.GEAR_PARK)
        return rig to spokenAtKm
    }

    /** 영어 대여로 경로를 달리면 기대 목록의 안내를 순서대로 모두 음성으로 내보내는지 확인한다. */
    @Test
    fun `영어 대여로 시연 경로를 달리면 기대 목록의 안내를 차례로 말한다`() {
        // 상황
        val expected = RouteFixtures.expectation("route_en.json")

        // 행동
        val (rig, _) = driveRoute(expected, AppLanguage.ENGLISH)

        // 결과
        val driving = rig.delivered.filter { it.channel != GuidanceChannel.PARKING_WARNING }
        assertEquals(expected.announcements, driving.map { RouteFixtures.lineOf(it, "values-en") })
        assertEquals(List(driving.size) { expected.spoken }, driving.map { it.speak })
    }

    /** 영어 대여로 경로 끝에서 정차하면 주정차 경고를 한 번, 음성으로 내보내는지 확인한다. */
    @Test
    fun `영어 대여로 시연 경로 끝에서 정차하면 주정차 경고를 말한다`() {
        // 상황
        val expected = RouteFixtures.expectation("route_en.json")

        // 행동
        val (rig, _) = driveRoute(expected, AppLanguage.ENGLISH)

        // 결과
        val warning = rig.parkingWarnings.single()
        assertEquals(expected.stopAtEnd, RouteFixtures.lineOf(warning, "values-en"))
        assertEquals(expected.spoken, warning.speak)
    }

    /** 한국어 대여로 경로를 달리면 같은 순서의 한국어 안내를 말하지 않고 알림으로만 띄우는지 확인한다. */
    @Test
    fun `한국어 대여로 시연 경로를 달리면 같은 순서의 안내를 알림으로만 띄운다`() {
        // 상황
        val expected = RouteFixtures.expectation("route_ko.json")

        // 행동
        val (rig, _) = driveRoute(expected, AppLanguage.KOREAN)

        // 결과
        val driving = rig.delivered.filter { it.channel != GuidanceChannel.PARKING_WARNING }
        assertEquals(expected.announcements, driving.map { RouteFixtures.lineOf(it, "values") })
        assertEquals(List(driving.size) { expected.spoken }, driving.map { it.speak })
    }

    /** 한국어 대여로 경로 끝에서 정차하면 주정차 경고를 알림으로만 띄우는지 확인한다. */
    @Test
    fun `한국어 대여로 시연 경로 끝에서 정차하면 주정차 경고를 알림으로 띄운다`() {
        // 상황
        val expected = RouteFixtures.expectation("route_ko.json")

        // 행동
        val (rig, _) = driveRoute(expected, AppLanguage.KOREAN)

        // 결과
        val warning = rig.parkingWarnings.single()
        assertEquals(expected.stopAtEnd, RouteFixtures.lineOf(warning, "values"))
        assertEquals(expected.spoken, warning.speak)
    }

    /** 경로의 기대 위치(기대 목록의 atKm, 10m 단위)에서 안내가 나오는지 확인한다. 스모크는 같은 값으로 실제 앱의 위치를 본다. */
    @Test
    fun `시연 경로의 안내는 기대 목록의 위치에서 나온다`() {
        // 상황
        val expected = RouteFixtures.expectation("route_en.json")

        // 행동
        val (_, spokenAtKm) = driveRoute(expected, AppLanguage.ENGLISH)

        // 결과
        assertEquals(expected.announcementKm, spokenAtKm.map(::roundedKm))
    }

    /**
     * 경로 위치를 소수 둘째 자리(10m)로 반올림한다. JVM 재생은 정해진 점을 그대로 지나가므로 이 단위에서 같아야 한다.
     *
     * @param km 위치(km)
     * @return 반올림한 위치
     */
    private fun roundedKm(km: Double): Double = Math.round(km * KM_SCALE) / KM_SCALE

    private companion object {
        /** 반올림 단위(0.01km). */
        const val KM_SCALE = 100.0
    }
}
