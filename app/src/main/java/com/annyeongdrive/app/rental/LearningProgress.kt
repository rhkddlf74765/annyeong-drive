package com.annyeongdrive.app.rental

/** 교육·퀴즈에서 이어 볼 수 있는 화면 종류. */
enum class LearningScreen {
    /** 학습 카드. */
    CARD,

    /** 문제. */
    QUIZ,
}

/**
 * 교육·퀴즈에서 마지막으로 본 위치.
 *
 * @property screen 화면 종류
 * @property itemId 카드 또는 문항의 고정 식별자(예: quiz.lane_direction)
 */
data class LearningPosition(val screen: LearningScreen, val itemId: String)

/** 이번 회차에서 문항을 어떻게 처리했는지. 기록이 없으면 아직 보지 않은 문항이다. */
enum class ItemStatus {
    /** 정답 확인까지 마쳤다. */
    ANSWERED,

    /** 건너뛰었다. 미완료로 남는다. */
    SKIPPED,
}

/**
 * 문항 하나의 이번 회차 기록.
 * [correct]는 복습 목록을 정하는 데만 쓰고 점수로 보여 주지 않는다(결정 문서 4장).
 *
 * @property status 처리 결과
 * @property correct 정답 여부. 건너뛴 문항은 null
 */
@ConsistentCopyVisibility
data class ItemRecord private constructor(val status: ItemStatus, val correct: Boolean?) {

    companion object {
        /**
         * 정답 확인을 마친 기록을 만든다.
         *
         * @param correct 정답 여부
         * @return 응답 기록
         */
        fun answered(correct: Boolean): ItemRecord = ItemRecord(ItemStatus.ANSWERED, correct)

        /**
         * 건너뛴 기록을 만든다.
         *
         * @return 건너뜀 기록
         */
        fun skipped(): ItemRecord = ItemRecord(ItemStatus.SKIPPED, null)
    }
}

/**
 * 교육·퀴즈 한 회차. "처음부터 보기"를 하면 회차 번호가 올라가고 기록이 비워진다.
 *
 * @property number 회차 번호(1부터)
 * @property position 이번 회차에서 마지막으로 본 위치. 아직 없으면 null
 * @property items 문항 식별자별 기록. 읽기 전용
 */
@ConsistentCopyVisibility
data class LearningAttempt private constructor(
    val number: Int,
    val position: LearningPosition?,
    val items: Map<String, ItemRecord>,
) {

    /**
     * 문항에 정답 확인을 마쳤는지 알려 준다.
     *
     * @param itemId 문항 식별자
     * @return 응답했으면 true
     */
    fun isAnswered(itemId: String): Boolean = items[itemId]?.status == ItemStatus.ANSWERED

    /**
     * 위치를 옮긴 새 회차를 돌려준다.
     *
     * @param newPosition 새 위치
     * @return 위치가 바뀐 회차
     */
    internal fun movedTo(newPosition: LearningPosition): LearningAttempt = copy(position = newPosition)

    /**
     * 문항 기록을 남긴 새 회차를 돌려준다.
     *
     * @param itemId 문항 식별자
     * @param record 남길 기록
     * @return 기록이 추가된 회차
     */
    internal fun recorded(itemId: String, record: ItemRecord): LearningAttempt = copy(items = items + (itemId to record))

    /**
     * 다음 번호의 빈 회차를 돌려준다.
     *
     * @return 번호가 하나 오른 빈 회차
     */
    internal fun next(): LearningAttempt = LearningAttempt(number + 1, null, emptyMap())

    companion object {
        /** 첫 회차. */
        internal val FIRST = LearningAttempt(1, null, emptyMap())
    }
}

/**
 * 이번 대여의 교육·퀴즈 진행. [RentalSession] 안에 있고, 바깥에서는 RentalSession의 메서드로만 바꾼다.
 * 불변 객체이며 바꾸는 메서드는 새 객체를 돌려준다.
 * 근거: 기능 실행안 부록 B 동작 기준, 결정 문서 4장.
 *
 * @property contentVersion 진행을 기록한 교육 문구의 버전
 * @property begun 교육 시작 화면에서 "교육 시작"을 누른 적이 있는지
 * @property attempt 이번 회차
 * @property reviewIds 복습 목록(틀렸거나 건너뛴 문항). 회차가 바뀌어도 남는다
 * @property viewedCardIds 본 학습 카드. 회차가 바뀌어도 남는다
 */
@ConsistentCopyVisibility
data class LearningProgress private constructor(
    val contentVersion: String,
    val begun: Boolean,
    val attempt: LearningAttempt,
    val reviewIds: List<String>,
    val viewedCardIds: Set<String>,
) {

    /**
     * "교육 시작"을 누른 것으로 기록한다.
     *
     * @return 시작한 것으로 기록된 진행
     */
    fun begin(): LearningProgress = copy(begun = true)

    /**
     * 학습 카드를 본 것으로 기록하고 위치를 그 카드로 옮긴다.
     *
     * @param cardId 카드 식별자
     * @return 기록된 진행
     */
    fun viewCard(cardId: String): LearningProgress = copy(
        attempt = attempt.movedTo(LearningPosition(LearningScreen.CARD, cardId)),
        viewedCardIds = viewedCardIds + cardId,
    )

    /**
     * 문항을 화면에 보여 준 것으로 위치를 옮긴다.
     *
     * @param itemId 문항 식별자
     * @return 위치가 옮겨진 진행
     */
    fun showQuestion(itemId: String): LearningProgress =
        copy(attempt = attempt.movedTo(LearningPosition(LearningScreen.QUIZ, itemId)))

    /**
     * 정답 확인을 기록한다. 틀리면 복습 목록에 넣고, 맞히면 복습 목록에서 뺀다.
     * 이미 확인한 문항은 답을 바꿀 수 없으므로 그대로 둔다.
     *
     * @param itemId 문항 식별자
     * @param correct 정답 여부
     * @return 기록된 진행. 이미 확인한 문항이면 이 진행 그대로
     */
    fun answer(itemId: String, correct: Boolean): LearningProgress {
        if (attempt.isAnswered(itemId)) return this
        return copy(
            attempt = attempt.recorded(itemId, ItemRecord.answered(correct)),
            reviewIds = reviewAfterAnswer(itemId, correct),
        )
    }

    /**
     * 건너뛰기를 기록한다. 건너뛴 문항은 미완료로 남고 복습 목록에 들어간다.
     * 이미 확인한 문항은 응답 기록을 유지한다.
     *
     * @param itemId 문항 식별자
     * @return 기록된 진행. 이미 확인한 문항이면 이 진행 그대로
     */
    fun skip(itemId: String): LearningProgress {
        if (attempt.isAnswered(itemId)) return this
        return copy(
            attempt = attempt.recorded(itemId, ItemRecord.skipped()),
            reviewIds = reviewIds.withAdded(itemId),
        )
    }

    /**
     * "처음부터 보기". 이번 회차를 새로 시작하고 복습 목록과 본 카드는 남긴다.
     *
     * @return 새 회차의 진행
     */
    fun restart(): LearningProgress = copy(attempt = attempt.next())

    /**
     * 이번 회차에서 정답 확인까지 마친 문항 수. 화면의 {완료 수}이며 정답 수가 아니다.
     *
     * @return 응답한 문항 수
     */
    fun answeredCount(): Int = attempt.items.values.count { it.status == ItemStatus.ANSWERED }

    /**
     * 이번 회차에서 아직 응답하지 않은 문항 수. 화면의 {남은 수}다.
     *
     * @param order 핵심 퀴즈 문항 순서(교육 콘텐츠가 준다)
     * @return 응답하지 않은 문항 수
     */
    fun remainingCount(order: List<String>): Int = order.count { !attempt.isAnswered(it) }

    /**
     * 시작했지만 끝내지 않은 교육이 있는지 판단한다. 건너뛴 문항도 미완료다.
     *
     * @param order 핵심 퀴즈 문항 순서
     * @return 교육을 시작했고 응답하지 않은 문항이 있으면 true
     */
    fun hasUnfinished(order: List<String>): Boolean = begun && remainingCount(order) > 0

    /**
     * 지금 보고 있는 문항이 마지막 문항인지 판단한다.
     *
     * @param order 핵심 퀴즈 문항 순서
     * @return 마지막 문항을 보고 있으면 true
     */
    fun isLastQuestion(order: List<String>): Boolean {
        val position = attempt.position ?: return false
        return position.screen == LearningScreen.QUIZ && position.itemId == order.lastOrNull()
    }

    /**
     * 교육 문구 버전에 맞춘 진행을 돌려준다. 버전이 다르면 진행을 버리고 새로 시작한다(결정 문서 4장).
     *
     * @param version 지금 앱의 교육 문구 버전
     * @return 버전이 같으면 이 진행, 다르면 새 진행
     */
    fun alignedTo(version: String): LearningProgress {
        if (version == contentVersion) return this
        return fresh(version)
    }

    /**
     * 정답 확인 결과에 따라 바뀐 복습 목록을 돌려준다.
     *
     * @param itemId 문항 식별자
     * @param correct 정답 여부
     * @return 맞히면 뺀 목록, 틀리면 넣은 목록
     */
    private fun reviewAfterAnswer(itemId: String, correct: Boolean): List<String> =
        if (correct) reviewIds - itemId else reviewIds.withAdded(itemId)

    /**
     * 목록에 없을 때만 끝에 덧붙인다. 복습 목록의 순서와 중복 없음을 지킨다.
     *
     * @param itemId 넣을 식별자
     * @return 덧붙인 목록
     */
    private fun List<String>.withAdded(itemId: String): List<String> =
        if (itemId in this) this else this + itemId

    companion object {
        /**
         * 아무것도 하지 않은 새 진행을 만든다.
         *
         * @param contentVersion 교육 문구 버전
         * @return 시작 전의 진행
         */
        fun fresh(contentVersion: String): LearningProgress = LearningProgress(
            contentVersion = contentVersion,
            begun = false,
            attempt = LearningAttempt.FIRST,
            reviewIds = emptyList(),
            viewedCardIds = emptySet(),
        )
    }
}
