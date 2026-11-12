package com.annyeongdrive.app.drive

import com.annyeongdrive.app.drive.state.LearningCard
import com.annyeongdrive.app.drive.state.LearningExplain
import com.annyeongdrive.app.drive.state.LearningQuiz
import com.annyeongdrive.app.drive.state.LearningResult
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 교육·퀴즈 안의 흐름: 교육 시작 → 학습 카드 → 문제 → 해설 → 결과·복습.
 * 근거: docs/state-machine.md T25~T30·B6, 기능 실행안 부록 B.
 */
class LearningFlowTest {

    /** 교육 시작을 누르면 학습 카드로 가는지 확인한다(T25). */
    @Test
    fun `교육 시작을 누르면 학습 카드로 간다`() {
        // 상황
        val scenario = DriveScenario().atParkedRest()
        scenario.feed(DriveEvent.LearningOpenRequested)
        // 행동
        scenario.feed(DriveEvent.LearningBegin)
        // 결과
        assertSame(LearningCard, scenario.state)
    }

    /** 학습 카드에서 핵심 퀴즈 시작을 누르면 문제로 가는지 확인한다(T26). */
    @Test
    fun `핵심 퀴즈 시작을 누르면 문제로 간다`() {
        // 상황
        val scenario = DriveScenario().atParkedRest()
        scenario.feed(DriveEvent.LearningOpenRequested, DriveEvent.LearningBegin)
        // 행동
        scenario.feed(DriveEvent.QuizStartRequested)
        // 결과
        assertSame(LearningQuiz, scenario.state)
    }

    /** 정답을 확인하면 해설로 가는지 확인한다(T27). */
    @Test
    fun `정답을 확인하면 해설로 간다`() {
        // 상황
        val scenario = DriveScenario().atLearningQuiz()
        // 행동
        scenario.feed(DriveEvent.AnswerChecked)
        // 결과
        assertSame(LearningExplain, scenario.state)
    }

    /** 마지막 문항이 아닌 문제를 건너뛰면 다음 문제(문제 상태)에 머무는지 확인한다(T28). */
    @Test
    fun `문제를 건너뛰면 다음 문제로 간다`() {
        // 상황
        val scenario = DriveScenario().atLearningQuiz()
        // 행동
        scenario.feed(DriveEvent.QuestionSkipped)
        // 결과
        assertSame(LearningQuiz, scenario.state)
    }

    /** 마지막 문항을 건너뛰면 결과·복습으로 가는지 확인한다(B6). */
    @Test
    fun `마지막 문항을 건너뛰면 결과·복습으로 간다`() {
        // 상황
        val scenario = DriveScenario().atLearningQuiz()
        scenario.learning.lastQuestion = true
        // 행동
        scenario.feed(DriveEvent.QuestionSkipped)
        // 결과
        assertSame(LearningResult, scenario.state)
    }

    /** 해설에서 다음을 누르면 다음 문제로 가는지 확인한다(T29). */
    @Test
    fun `해설에서 다음을 누르면 다음 문제로 간다`() {
        // 상황
        val scenario = DriveScenario().atLearningQuiz()
        scenario.feed(DriveEvent.AnswerChecked)
        // 행동
        scenario.feed(DriveEvent.NextRequested)
        // 결과
        assertSame(LearningQuiz, scenario.state)
    }

    /** 마지막 문항의 해설에서 다음을 누르면 결과·복습으로 가는지 확인한다(T30). */
    @Test
    fun `마지막 문항의 해설 다음은 결과·복습이다`() {
        // 상황
        val scenario = DriveScenario().atLearningQuiz()
        scenario.learning.lastQuestion = true
        scenario.feed(DriveEvent.AnswerChecked)
        // 행동
        scenario.feed(DriveEvent.NextRequested)
        // 결과
        assertSame(LearningResult, scenario.state)
    }
}
