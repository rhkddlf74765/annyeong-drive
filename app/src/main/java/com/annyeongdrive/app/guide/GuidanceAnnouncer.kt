package com.annyeongdrive.app.guide

import com.annyeongdrive.app.rental.AppLanguage
import com.annyeongdrive.app.service.DriveNotifierPort

/** 안내를 화면에 띄울 때 쓰는 알림 종류. 종류마다 알림 채널이 다르다. */
enum class GuidanceChannel {
    /** 주행 중 규칙 안내(음성을 쓸 수 없는 언어일 때). */
    ROAD_RULE,

    /** 주정차 경고. 언어와 상관없이 항상 띄운다. */
    PARKING_WARNING,
}

/**
 * 출력 쪽에 넘기는 안내 하나. 문장을 어떤 언어로 읽고, 말할지, 어느 알림으로 띄울지가 정해져 있다.
 *
 * @property rule 규칙 종류
 * @property encounter 이번 대여에서 이 규칙을 만난 횟수(1부터)
 * @property sentences 읽을 문장(순서대로)
 * @property language 대여 언어. 대여가 없으면 null(기기 기본 문구)
 * @property speak 음성으로 말할지
 * @property channel 띄울 알림 종류. 띄우지 않으면 null
 */
data class GuidanceMessage(
    val rule: Rule,
    val encounter: Int,
    val sentences: List<GuidanceSentence>,
    val language: AppLanguage?,
    val speak: Boolean,
    val channel: GuidanceChannel?,
)

/** 안내를 실제로 말하고 띄우는 출력 쪽(Android). 문장 ID를 대여 언어의 문장으로 바꾸는 일도 여기서 한다. */
fun interface GuidanceOutput {

    /**
     * 안내 하나를 내보낸다.
     *
     * @param message 안내
     */
    fun deliver(message: GuidanceMessage)
}

/**
 * 음성을 쓸 수 있는 언어인지 알려 준다. Pleos TTS에서 확인된 언어는 영어뿐이다(11/8 기능 확인 ③).
 * 쓸 수 없으면 화면(알림)으로 계속 안내한다(부록 E "음성 미지원 → 화면으로 계속").
 *
 * @param language 대여 언어
 * @return 음성으로 말할 수 있으면 true
 */
fun canSpeak(language: AppLanguage?): Boolean = language == AppLanguage.ENGLISH

/**
 * 주행 중 규칙 안내와 주정차 경고를 문장으로 만들어 내보낸다(AnnouncementManager의 규칙을 Coordinator 흐름으로 옮김).
 * - 몇 번째 만남인가: 규칙별로 이번 대여 동안 센다. 반납(T5)하면 처음부터 다시 센다.
 * - 몇 문장인가: [VerbosityPolicy](기본: 처음 2문장, 이후 1문장)
 * - 어떤 문장인가: [GuidanceSentences]
 * - 말할지 띄울지: 규칙 안내는 음성을 쓸 수 있으면 음성, 아니면 알림. 주정차 경고는 항상 알림 + 쓸 수 있으면 음성
 * Android를 쓰지 않아 JVM에서 테스트한다.
 *
 * @param policy 문장 수 정책
 * @param output 실제 출력
 * @param language 지금 대여 언어를 읽는 함수
 */
class GuidanceAnnouncer(
    private val policy: VerbosityPolicy,
    private val output: GuidanceOutput,
    private val language: () -> AppLanguage?,
) : DriveNotifierPort {

    private val encounters = mutableMapOf<Rule, Int>()

    /**
     * 주행 중 규칙을 알려 준다.
     *
     * @param reminder 알려 줄 규칙
     */
    override fun remindRoadRule(reminder: RoadRuleReminder) {
        val encounter = meet(reminder.rule)
        val sentences = policy.roles(reminder.rule, encounter).map { GuidanceSentences.of(reminder, it) }
        val current = language()
        val speak = canSpeak(current)
        val channel = roadRuleChannel(speak)
        output.deliver(GuidanceMessage(reminder.rule, encounter, sentences, current, speak, channel))
    }

    /**
     * 검수된 주정차 구역 근처에서 확인을 요청하는 경고를 낸다. 알림은 언어와 상관없이 띄운다.
     *
     * @param zoneId 검수된 구역 식별자
     */
    override fun announceParkingWarning(zoneId: String) {
        val encounter = meet(Rule.PARKING_CAMERA)
        val sentences = policy.roles(Rule.PARKING_CAMERA, encounter).map(GuidanceSentences::parkingWarning)
        val current = language()
        val message = GuidanceMessage(
            rule = Rule.PARKING_CAMERA,
            encounter = encounter,
            sentences = sentences,
            language = current,
            speak = canSpeak(current),
            channel = GuidanceChannel.PARKING_WARNING,
        )
        output.deliver(message)
    }

    /**
     * [구현 예정] 오주유 방지 확인 안내를 낸다. implementation-plan.md P1 "오주유 방지 뼈대"에서 구현한다.
     * 오주유 기능 플래그가 꺼져 있어 지금은 불리지 않는다.
     */
    override fun announceFuelCheck(): Unit = TODO("구현 예정: P1 오주유 방지 안내")

    /** 대여가 끝나 "처음 만남" 횟수를 처음부터 다시 센다. */
    override fun forgetEncounters() = encounters.clear()

    /**
     * 규칙을 한 번 더 만난 것으로 센다.
     *
     * @param rule 규칙 종류
     * @return 이번 대여에서 만난 횟수(1부터)
     */
    private fun meet(rule: Rule): Int {
        val count = encounters.getOrElse(rule) { 0 } + 1
        encounters[rule] = count
        return count
    }

    /**
     * 규칙 안내를 띄울 알림 종류를 고른다. 음성으로 말하면 띄우지 않는다(주행 중 화면을 늘리지 않기 위해).
     *
     * @param speak 음성으로 말하는지
     * @return 알림 종류. 띄우지 않으면 null
     */
    private fun roadRuleChannel(speak: Boolean): GuidanceChannel? {
        if (speak) return null
        return GuidanceChannel.ROAD_RULE
    }
}
