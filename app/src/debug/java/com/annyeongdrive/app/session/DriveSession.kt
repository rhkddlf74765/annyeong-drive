package com.annyeongdrive.app.session

import com.annyeongdrive.app.guide.AnnouncementManager
import com.annyeongdrive.app.guide.Phrasebook
import com.annyeongdrive.app.guide.Utterance
import com.annyeongdrive.app.guide.VerbosityPolicy
import com.annyeongdrive.app.speech.Speaker
import com.annyeongdrive.app.zones.ZoneData
import com.annyeongdrive.app.zones.ZoneDetector
import com.annyeongdrive.app.zones.ZoneEvent

/**
 * 주행 하나. 판단기와 안내 관리자를 한 벌씩 가진다.
 * 주행 중 기억하는 것(알린 카메라, 규칙별 만남 횟수 등)은 모두 이 객체 안에만 있다.
 * 새 주행은 새 DriveSession을 만들어 시작하므로 이전 주행의 상태가 섞이지 않는다.
 */
class DriveSession(
    val number: Int,
    val startedAtMs: Long,
    zones: ZoneData,
    phrasebook: Phrasebook,
    policy: VerbosityPolicy,
    speaker: Speaker,
) {
    private val detector = ZoneDetector(zones)
    private val announcer = AnnouncementManager(phrasebook, policy, speaker)

    /** 위치 하나를 처리하고, 일어난 사건과 그때 읽은 문장(없으면 null)을 돌려준다. */
    fun onLocation(lat: Double, lon: Double, timeMs: Long): List<Pair<ZoneEvent, Utterance?>> =
        detector.update(lat, lon, timeMs).map { it to announcer.onEvent(it) }
}
