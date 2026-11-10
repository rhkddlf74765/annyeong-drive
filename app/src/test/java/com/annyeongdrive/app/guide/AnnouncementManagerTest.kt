package com.annyeongdrive.app.guide

import com.annyeongdrive.app.speech.Speaker
import com.annyeongdrive.app.zones.ParkingCamera
import com.annyeongdrive.app.zones.SchoolZone
import com.annyeongdrive.app.zones.SpeedCamera
import com.annyeongdrive.app.zones.ZoneEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AnnouncementManagerTest {

    private class FakeSpeaker : Speaker {
        val spoken = mutableListOf<String>()
        override fun speak(text: String, locale: Locale) { spoken += text }
        override fun shutdown() {}
    }

    private val zone = SchoolZone("SZ1", "테스트초", 0.0, 0.0, 300.0, 30)
    private val cam50 = SpeedCamera("C1", 0.0, 0.0, 50, "none", "")
    private val cam30 = SpeedCamera("C2", 0.0, 0.0, 30, "school", "")
    private val park = ParkingCamera("P1", 0.0, 0.0, "")

    private fun manager(policy: VerbosityPolicy = EncounterBasedPolicy(), speaker: Speaker = FakeSpeaker()) =
        AnnouncementManager(EnglishPhrasebook, policy, speaker)

    @Test
    fun `기본 정책 - 처음은 2문장, 이후는 1문장`() {
        val m = manager()
        val first = m.onEvent(ZoneEvent.SchoolZoneEntered(zone, 290.0))!!
        val second = m.onEvent(ZoneEvent.SchoolZoneEntered(zone, 290.0))!!
        assertEquals(listOf(SentenceRole.CORE, SentenceRole.CONSEQUENCE), first.roles)
        assertEquals(listOf(SentenceRole.CORE), second.roles)
        assertEquals("School zone. Speed limit 30.", second.text)
        assertTrue(first.text.length > second.text.length)
    }

    @Test
    fun `규칙마다 따로 센다`() {
        val m = manager()
        m.onEvent(ZoneEvent.SchoolZoneEntered(zone, 290.0))
        val firstCamera = m.onEvent(ZoneEvent.SpeedCameraAhead(cam50, 200.0))!!
        assertEquals(1, firstCamera.encounter)
        assertEquals(2, firstCamera.roles.size)
        assertEquals(2, m.onEvent(ZoneEvent.SpeedCameraAhead(cam30, 200.0))!!.encounter)
    }

    @Test
    fun `스쿨존 카메라와 일반 카메라 문장이 다르다`() {
        val m = manager(policy = { _, _ -> listOf(SentenceRole.CORE) })
        assertEquals("Speed camera ahead. Limit 50.", m.onEvent(ZoneEvent.SpeedCameraAhead(cam50, 200.0))!!.text)
        assertEquals("School zone camera ahead. Limit 30.", m.onEvent(ZoneEvent.SpeedCameraAhead(cam30, 200.0))!!.text)
    }

    @Test
    fun `정책을 바꾸면 문장 길이 규칙이 바뀐다`() {
        // 처음 2번은 자세히, 주정차는 항상 자세히
        val policy = EncounterBasedPolicy(
            detailedCount = 2,
            overrides = mapOf(Rule.PARKING_CAMERA to VerbosityPolicy { _, _ ->
                listOf(SentenceRole.CORE, SentenceRole.CONSEQUENCE)
            }),
        )
        val m = manager(policy)
        assertEquals(2, m.onEvent(ZoneEvent.SchoolZoneEntered(zone, 1.0))!!.roles.size)
        assertEquals(2, m.onEvent(ZoneEvent.SchoolZoneEntered(zone, 1.0))!!.roles.size)
        assertEquals(1, m.onEvent(ZoneEvent.SchoolZoneEntered(zone, 1.0))!!.roles.size)
        repeat(3) { assertEquals(2, m.onEvent(ZoneEvent.StoppedNearParkingCamera(park, 40.0))!!.roles.size) }
    }

    @Test
    fun `이탈은 말하지 않고, 말한 문장은 Speaker로 간다`() {
        val speaker = FakeSpeaker()
        val m = manager(speaker = speaker)
        assertNull(m.onEvent(ZoneEvent.SchoolZoneExited))
        m.onEvent(ZoneEvent.SchoolZoneEntered(zone, 1.0))
        assertEquals(1, speaker.spoken.size)
    }

    @Test
    fun `reset 하면 다시 처음부터 센다`() {
        val m = manager()
        m.onEvent(ZoneEvent.SchoolZoneEntered(zone, 1.0))
        m.reset()
        assertEquals(1, m.onEvent(ZoneEvent.SchoolZoneEntered(zone, 1.0))!!.encounter)
    }
}
