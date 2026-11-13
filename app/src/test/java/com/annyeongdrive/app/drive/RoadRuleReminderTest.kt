package com.annyeongdrive.app.drive

import com.annyeongdrive.app.drive.state.DrivingCruising
import com.annyeongdrive.app.gear.Gear
import com.annyeongdrive.app.guide.RoadRuleReminder
import com.annyeongdrive.app.location.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 주행 중 규칙 안내(일반 주행의 자기 전이): 기어 D/R로 달리는 중 위치가 바뀌면 찾은 규칙을 안내 할 일로 내고 상태는 그대로다.
 * 정차 중이거나 N·기어를 모를 때는 위치가 바뀌어도 규칙을 찾지 않는다.
 * 근거: 기능 보드 "주행 중 규칙 안내" 1·6번, docs/state-machine.md B11, implementation-plan 8단계.
 */
class RoadRuleReminderTest {

    private val schoolZone = RoadRuleReminder.SchoolZone("SZ1", 30)
    private val here = GeoPoint(37.63, 127.03)

    /** D로 달리는 중 위치가 바뀌어 규칙을 찾으면 안내 할 일이 나오고 일반 주행에 머문다(B11). */
    @Test
    fun `D로 달리는 중 규칙을 찾으면 안내 할 일이 나오고 주행에 머문다`() {
        // 상황
        val scenario = DriveScenario().atDriving()
        scenario.roadRules.next = listOf(schoolZone)

        // 행동
        val actions = scenario.feed(DriveEvent.LocationChanged(here))

        // 결과
        assertEquals(listOf(DriveAction.RemindRoadRule(schoolZone)), actions)
        assertSame(DrivingCruising, scenario.state)
    }

    /** 찾은 규칙이 없으면 할 일이 없다. */
    @Test
    fun `찾은 규칙이 없으면 할 일이 없다`() {
        // 상황
        val scenario = DriveScenario().atDriving()

        // 행동
        val actions = scenario.feed(DriveEvent.LocationChanged(here))

        // 결과
        assertTrue(actions.isEmpty())
        assertEquals(listOf(here), scenario.roadRules.observed)
    }

    /** N에서 차속만으로 움직일 때는 위치가 바뀌어도 규칙을 찾지 않는다(기어 D/R일 때만 말한다). */
    @Test
    fun `N으로 움직일 때는 규칙을 찾지 않는다`() {
        // 상황
        val scenario = DriveScenario().atParkedRest()
        scenario.feed(DriveEvent.GearChanged(Gear.N), DriveEvent.SpeedChanged(10f))
        scenario.roadRules.next = listOf(schoolZone)

        // 행동
        val actions = scenario.feed(DriveEvent.LocationChanged(here))

        // 결과
        assertSame(DrivingCruising, scenario.state)
        assertTrue(actions.isEmpty())
        assertTrue(scenario.roadRules.observed.isEmpty())
    }

    /** 정차 중에는 위치가 바뀌어도 규칙을 찾지 않는다. */
    @Test
    fun `정차 중에는 규칙을 찾지 않는다`() {
        // 상황
        val scenario = DriveScenario().atParkedRest()

        // 행동
        val actions = scenario.feed(DriveEvent.LocationChanged(here))

        // 결과
        assertTrue(actions.isEmpty())
        assertTrue(scenario.roadRules.observed.isEmpty())
    }
}
