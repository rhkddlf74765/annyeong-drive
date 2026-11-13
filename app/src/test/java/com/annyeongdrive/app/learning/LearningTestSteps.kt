package com.annyeongdrive.app.learning

import com.annyeongdrive.app.admin.AdminTestHarness
import com.annyeongdrive.app.gear.Gear
import com.annyeongdrive.app.rental.AppLanguage

/**
 * 교육 흐름 테스트들이 함께 쓰는 사용자 동작 묶음. 1차 콘텐츠(CORE_CATALOG)를 쓰는 앱을 전제로 한다.
 */
object LearningTestSteps {

    /** 1차 콘텐츠의 첫 학습 카드. */
    const val FIRST_CARD = "card.precheck"

    /** 1차 콘텐츠의 첫 문항. */
    const val FIRST_QUESTION = "quiz.lane_direction"

    /** 1차 콘텐츠의 둘째 문항. */
    const val SECOND_QUESTION = "quiz.school_zone_stop"

    /** 1차 콘텐츠 문항별 정답 위치(순서대로). */
    val CORRECT_CHOICES = listOf(0, 1, 2, 0, 3)

    /**
     * 처음 설치한 앱에서 대여 설정을 마치고 교육 시작을 눌러 첫 학습 카드까지 간다.
     *
     * @param app 1차 콘텐츠를 쓰는 앱
     */
    suspend fun toFirstCard(app: AdminTestHarness) {
        app.startAndSit()
        app.setupFlow.selectLanguage(AppLanguage.ENGLISH)
        app.setupFlow.confirmSeatBelts(true)
        app.setupFlow.completePrecheck()
        app.learningEntry.begin()
        app.learningFlow.sync()
    }

    /**
     * 첫 문제까지 간다.
     *
     * @param app 1차 콘텐츠를 쓰는 앱
     */
    suspend fun toFirstQuestion(app: AdminTestHarness) {
        toFirstCard(app)
        app.learningFlow.startQuiz()
    }

    /**
     * 지금 문제에 답을 고르고 정답을 확인한 뒤 다음으로 넘어간다.
     *
     * @param app 앱
     * @param choice 고를 선택지
     */
    suspend fun answerAndNext(app: AdminTestHarness, choice: Int) {
        app.learningFlow.selectAnswer(choice)
        app.learningFlow.checkAnswer()
        app.learningFlow.next()
    }

    /**
     * 지금 보이는 문제의 식별자.
     *
     * @param app 앱
     * @return 문항 식별자
     */
    fun shownQuestionId(app: AdminTestHarness): String =
        (app.learningFlow.view.value as LearningView.Question).question.id

    /**
     * 출발했다가 다시 선다(기어 D → P). 교육이 닫히고 정차 맥락 판단을 다시 한다.
     *
     * @param app 앱
     */
    suspend fun driveAndStop(app: AdminTestHarness) {
        app.signals.gear.shift(Gear.D)
        app.signals.gear.shift(Gear.P)
        app.learningFlow.sync()
    }
}
