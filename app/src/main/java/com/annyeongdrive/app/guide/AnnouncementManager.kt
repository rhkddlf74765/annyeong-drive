package com.annyeongdrive.app.guide

import com.annyeongdrive.app.speech.Speaker
import com.annyeongdrive.app.zones.ZoneEvent

/**
 * 판단기 사건을 받아 문장을 만들고 Speaker로 읽는다.
 * - 사건 → 안내 재료: toAnnouncement
 * - 몇 번째 만남인가: 규칙별로 센다
 * - 어떤 문장을 넣을까: VerbosityPolicy
 * - 문장 내용: Phrasebook
 * 안드로이드에 의존하지 않아 단위 테스트가 된다.
 *
 * 만남 횟수는 주행 하나 동안만 센다. 새 주행은 DriveSession이 새 관리자를 만들어 시작한다.
 */
class AnnouncementManager(
    private var phrasebook: Phrasebook,
    private val policy: VerbosityPolicy,
    private val speaker: Speaker,
) {
    private val encounters = mutableMapOf<Rule, Int>()

    /** 사건 하나를 처리한다. 읽을 것이 없으면 null. */
    fun onEvent(event: ZoneEvent): Utterance? {
        val announcement = toAnnouncement(event) ?: return null
        val encounter = (encounters[announcement.rule] ?: 0) + 1
        encounters[announcement.rule] = encounter

        val roles = policy.roles(announcement.rule, encounter)
        val text = roles
            .mapNotNull { phrasebook.sentence(announcement.rule, it, announcement.params) }
            .joinToString(" ")
        if (text.isBlank()) return null

        val utterance = Utterance(announcement.rule, encounter, roles, text, phrasebook.locale)
        speaker.speak(utterance.text, utterance.locale)
        return utterance
    }

    /** 언어를 바꾼다. 만남 횟수는 유지한다. */
    fun setPhrasebook(newPhrasebook: Phrasebook) {
        phrasebook = newPhrasebook
    }

    /** 대여가 끝나면 처음부터 다시 센다. */
    fun reset() = encounters.clear()

    private fun toAnnouncement(event: ZoneEvent): Announcement? = when (event) {
        is ZoneEvent.SchoolZoneEntered -> Announcement(
            Rule.SCHOOL_ZONE,
            mapOf(Params.LIMIT to event.zone.speedLimitKmh.toString()),
        )
        is ZoneEvent.SpeedCameraAhead -> Announcement(
            Rule.SPEED_CAMERA,
            mapOf(
                Params.LIMIT to event.camera.speedLimitKmh.toString(),
                Params.SCHOOL to (event.camera.protectedZone == "school").toString(),
            ),
        )
        is ZoneEvent.StoppedNearParkingCamera -> Announcement(Rule.PARKING_CAMERA)
        ZoneEvent.SchoolZoneExited -> null   // 이탈은 지금은 말하지 않는다
    }
}
