package com.annyeongdrive.app.zones

import com.annyeongdrive.app.admin.MutableClock
import com.annyeongdrive.app.guide.RoadRuleReminder
import com.annyeongdrive.app.location.GeoPoint
import com.annyeongdrive.app.storage.StorageFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/**
 * 구역 데이터로 주행 중 규칙 찾기: 검증된 판단기(ZoneDetector)의 사건을 규칙 안내 재료로 옮긴다.
 * 제한속도는 유효한 값만 담고, 같은 카메라는 지나는 동안 한 번만 찾는다.
 * 근거: 기능 보드 "주행 중 규칙 안내" 1·3·5번, CLAUDE.md 도메인 원칙(30 고정 금지), implementation-plan 8단계.
 */
class ZoneRoadRuleWatcherTest {

    private val clock = MutableClock(StorageFixtures.NOW)

    /**
     * 구역 데이터로 규칙 판단을 만든다.
     *
     * @param schoolZones 보호구역
     * @param cameras 과속 카메라
     * @return 규칙 판단
     */
    private fun watcher(schoolZones: List<SchoolZone> = emptyList(), cameras: List<SpeedCamera> = emptyList()) =
        ZoneRoadRuleWatcher(ZoneData(schoolZones, cameras, emptyList()), clock)

    /** 보호구역에 들어가면 그 구역의 제한속도와 함께 찾는다. */
    @Test
    fun `보호구역에 들어가면 제한속도와 함께 찾는다`() {
        // 상황
        val rules = watcher(schoolZones = listOf(SchoolZone("SZ1", "테스트초", 0.0, 0.0, 300.0, 30)))

        // 행동
        val found = rules.observe(GeoPoint(0.0, 0.0))

        // 결과
        assertEquals(listOf(RoadRuleReminder.SchoolZone("SZ1", 30)), found)
    }

    /** 제한속도를 모르는 보호구역은 숫자 없이 찾는다(30으로 채우지 않는다). */
    @Test
    fun `제한속도를 모르는 보호구역은 숫자 없이 찾는다`() {
        // 상황
        val rules = watcher(schoolZones = listOf(SchoolZone("SZ1", "테스트초", 0.0, 0.0, 300.0, null)))

        // 행동
        val found = rules.observe(GeoPoint(0.0, 0.0))

        // 결과
        assertEquals(listOf(RoadRuleReminder.SchoolZone("SZ1", null)), found)
    }

    /** 진행 방향 앞의 카메라는 다가가는 동안 한 번만 찾는다. */
    @Test
    fun `같은 카메라는 지나는 동안 한 번만 찾는다`() {
        // 상황
        val camera = SpeedCamera("C1", 0.0020, 0.0, 30, "school", "")
        val rules = watcher(cameras = listOf(camera))
        val path = listOf(0.0, 0.0005, 0.0008, 0.0011).map { GeoPoint(it, 0.0) }

        // 행동
        val found = path.flatMap { point ->
            clock.advance(Duration.ofSeconds(3))
            rules.observe(point)
        }

        // 결과
        assertEquals(listOf(RoadRuleReminder.SpeedCamera("C1", 30, inSchoolZone = true)), found)
    }

    /** 아무 구역도 가깝지 않으면 찾지 않는다. */
    @Test
    fun `가까운 구역이 없으면 찾지 않는다`() {
        // 상황
        val rules = watcher(schoolZones = listOf(SchoolZone("SZ1", "테스트초", 0.0, 0.0, 300.0, 30)))

        // 행동
        val found = rules.observe(GeoPoint(0.01, 0.01))

        // 결과
        assertTrue(found.isEmpty())
    }
}
