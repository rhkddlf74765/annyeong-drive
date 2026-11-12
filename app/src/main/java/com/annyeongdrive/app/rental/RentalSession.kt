package com.annyeongdrive.app.rental

import java.time.Instant

/**
 * 대여를 시작할 때 필요한 값 묶음.
 *
 * @property rentalId 이번 대여의 식별자(생성한 UUID)
 * @property startedAt 대여 시작 시각
 * @property language 고른 언어
 * @property contentVersion 지금 앱의 교육 문구 버전
 */
data class RentalStartRequest(
    val rentalId: String,
    val startedAt: Instant,
    val language: AppLanguage,
    val contentVersion: String,
)

/**
 * 운전자 데이터의 루트(Aggregate). 언어, 운전 국가, 출발 전 점검 상태, 교육 진행을 묶는다.
 * 반납 처리나 예비 초기화 때 통째로 지운다. 위치·개인정보는 넣지 않는다.
 * 불변 객체이며 바꾸는 메서드는 새 객체를 돌려준다. 교육 진행도 이 객체의 메서드로만 바꾼다.
 * 근거: 결정 문서 3·4·6장, CLAUDE.md Aggregate 규칙.
 *
 * @property rentalId 이번 대여의 식별자
 * @property startedAt 대여 시작 시각
 * @property language 고른 언어
 * @property drivingCountry 익숙한 운전 국가. 국가별 차이 안내는 확장 기능이라 지금은 항상 null
 * @property precheckCompleted 출발 전 점검을 마쳤는지
 * @property learning 교육·퀴즈 진행(읽기 전용)
 */
@ConsistentCopyVisibility
data class RentalSession private constructor(
    val rentalId: String,
    val startedAt: Instant,
    val language: AppLanguage,
    val drivingCountry: String?,
    val precheckCompleted: Boolean,
    val learning: LearningProgress,
) {

    /**
     * 대여 설정(언어 선택 + 출발 전 점검)을 마쳤는지 알려 준다.
     * 상태 머신: T2, T3 가드 (2-d에서 RentalStatusReader가 읽는다)
     *
     * @return 출발 전 점검을 마쳤으면 true
     */
    fun hasRentalSetup(): Boolean = precheckCompleted

    /**
     * 언어를 바꾼다. 교육 진행은 그대로 둔다.
     *
     * @param newLanguage 새 언어
     * @return 언어가 바뀐 대여
     */
    fun changeLanguage(newLanguage: AppLanguage): RentalSession = copy(language = newLanguage)

    /**
     * 출발 전 점검을 마친 것으로 기록한다.
     *
     * @return 점검을 마친 대여
     */
    fun completePrecheck(): RentalSession = copy(precheckCompleted = true)

    /**
     * "교육 시작"을 누른 것으로 기록한다.
     *
     * @return 기록된 대여
     */
    fun beginLearning(): RentalSession = copy(learning = learning.begin())

    /**
     * 학습 카드를 본 것으로 기록한다.
     *
     * @param cardId 카드 식별자
     * @return 기록된 대여
     */
    fun viewCard(cardId: String): RentalSession = copy(learning = learning.viewCard(cardId))

    /**
     * 문항을 화면에 보여 준 것으로 위치를 옮긴다.
     *
     * @param itemId 문항 식별자
     * @return 기록된 대여
     */
    fun showQuestion(itemId: String): RentalSession = copy(learning = learning.showQuestion(itemId))

    /**
     * 정답 확인을 기록한다. 이미 확인한 문항은 바꾸지 않는다.
     *
     * @param itemId 문항 식별자
     * @param correct 정답 여부
     * @return 기록된 대여
     */
    fun answerQuestion(itemId: String, correct: Boolean): RentalSession =
        copy(learning = learning.answer(itemId, correct))

    /**
     * 건너뛰기를 기록한다.
     *
     * @param itemId 문항 식별자
     * @return 기록된 대여
     */
    fun skipQuestion(itemId: String): RentalSession = copy(learning = learning.skip(itemId))

    /**
     * "처음부터 보기". 복습 목록은 남긴다.
     *
     * @return 새 회차를 시작한 대여
     */
    fun restartLearning(): RentalSession = copy(learning = learning.restart())

    /**
     * 앱의 교육 문구 버전에 맞춘다. 버전이 다르면 교육 진행만 새로 시작하고 대여는 유지한다.
     *
     * @param contentVersion 지금 앱의 교육 문구 버전
     * @return 버전에 맞춘 대여
     */
    fun alignContentVersion(contentVersion: String): RentalSession =
        copy(learning = learning.alignedTo(contentVersion))

    companion object {
        /**
         * 언어를 고른 순간 대여를 시작한다. 출발 전 점검과 교육은 아직 하지 않은 상태다.
         *
         * @param request 대여 시작 값
         * @return 새 대여
         */
        fun start(request: RentalStartRequest): RentalSession = RentalSession(
            rentalId = request.rentalId,
            startedAt = request.startedAt,
            language = request.language,
            drivingCountry = null,
            precheckCompleted = false,
            learning = LearningProgress.fresh(request.contentVersion),
        )
    }
}
