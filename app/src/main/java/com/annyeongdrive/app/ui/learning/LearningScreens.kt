package com.annyeongdrive.app.ui.learning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.annyeongdrive.app.R
import com.annyeongdrive.app.learning.CORE_CATALOG
import com.annyeongdrive.app.learning.LearningPlan
import com.annyeongdrive.app.learning.LearningSituation
import com.annyeongdrive.app.learning.LearningView
import com.annyeongdrive.app.learning.PlannedQuestion
import com.annyeongdrive.app.learning.TextId
import com.annyeongdrive.app.ui.theme.Annyeong_driveTheme
import com.annyeongdrive.app.vehicle.FuelType
import com.annyeongdrive.app.vendor.HiPassAvailability

/**
 * 교육·퀴즈 화면의 사용자 동작 묶음.
 *
 * @property onPreviousCard 이전 규칙
 * @property onNextCard 다음 규칙
 * @property onStartQuiz 핵심 퀴즈 시작
 * @property onSelect 선택지 고르기
 * @property onCheck 정답 확인
 * @property onSkip 건너뛰기
 * @property onNext 해설 다음
 * @property onClose 교육 닫기
 */
data class LearningActions(
    val onPreviousCard: () -> Unit,
    val onNextCard: () -> Unit,
    val onStartQuiz: () -> Unit,
    val onSelect: (Int) -> Unit,
    val onCheck: () -> Unit,
    val onSkip: () -> Unit,
    val onNext: () -> Unit,
    val onClose: () -> Unit,
)

/**
 * 교육·퀴즈 화면. [LearningView]에 따라 카드·문제·해설·결과 중 하나를 그린다. 문구는 문구 ID로만 읽는다.
 *
 * @param view 화면 안쪽 상태
 * @param actions 사용자 동작
 * @param modifier 바깥에서 주는 배치 설정
 */
@Composable
fun LearningScreen(view: LearningView, actions: LearningActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (view) {
            is LearningView.Card -> CardContentView(view, actions)
            is LearningView.Question -> QuestionView(view, actions)
            is LearningView.Explanation -> ExplanationView(view, actions)
            is LearningView.Result -> ResultView(view)
            LearningView.Hidden -> Unit
        }
        TextButton(onClick = actions.onClose) { Text(stringResource(R.string.learn_action_close)) }
    }
}

/**
 * 학습 카드. 마지막 카드에서 핵심 퀴즈를 시작한다.
 *
 * @param view 카드 상태
 * @param actions 사용자 동작
 */
@Composable
private fun CardContentView(view: LearningView.Card, actions: LearningActions) {
    Text(stringResource(R.string.learn_card_position, view.number, view.total))
    Text(text(view.card.texts.title), style = MaterialTheme.typography.titleLarge)
    Text(text(view.card.texts.body))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = actions.onPreviousCard, enabled = view.hasPrevious) {
            Text(stringResource(R.string.learn_action_prev))
        }
        if (view.isLast) {
            Button(onClick = actions.onStartQuiz) { Text(stringResource(R.string.learn_action_start_quiz)) }
        } else {
            Button(onClick = actions.onNextCard) { Text(stringResource(R.string.learn_action_next)) }
        }
    }
}

/**
 * 문제. 답을 고르기 전에는 정답 확인을 누를 수 없다.
 *
 * @param view 문제 상태
 * @param actions 사용자 동작
 */
@Composable
private fun QuestionView(view: LearningView.Question, actions: LearningActions) {
    Text(stringResource(R.string.learn_question_position, view.number, view.total))
    Text(text(view.question.texts.question), style = MaterialTheme.typography.titleLarge)
    view.question.texts.options.forEachIndexed { index, option ->
        val chosen = view.selected == index
        Row(
            modifier = Modifier.selectable(selected = chosen, onClick = { actions.onSelect(index) }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = chosen, onClick = { actions.onSelect(index) })
            Text(text(option))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = actions.onCheck, enabled = view.canCheck) { Text(stringResource(R.string.learn_action_check)) }
        OutlinedButton(onClick = actions.onSkip) { Text(stringResource(R.string.learn_action_skip)) }
    }
}

/**
 * 해설. 정답과 해설을 보여 준다.
 *
 * @param view 해설 상태
 * @param actions 사용자 동작
 */
@Composable
private fun ExplanationView(view: LearningView.Explanation, actions: LearningActions) {
    Text(stringResource(R.string.learn_question_position, view.number, view.total))
    if (view.correct) Text(stringResource(R.string.learn_explain_correct), style = MaterialTheme.typography.titleLarge)
    val answer = text(correctOption(view.question))
    Text(stringResource(R.string.learn_explain_answer, answer), style = MaterialTheme.typography.titleMedium)
    Text(text(view.question.texts.explanation))
    Button(onClick = actions.onNext) { Text(stringResource(R.string.learn_action_next_question)) }
}

/**
 * 결과·복습. 응답한 문항 수와 다시 볼 규칙만 보여 준다(점수·합격 표현 없음).
 *
 * @param view 결과 상태
 */
@Composable
private fun ResultView(view: LearningView.Result) {
    Text(stringResource(R.string.learn_result_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.learn_result_answered, view.answered, view.total))
    Text(stringResource(R.string.learn_review_title), style = MaterialTheme.typography.titleMedium)
    if (view.reviewCards.isEmpty()) Text(stringResource(R.string.learn_review_empty))
    view.reviewCards.forEach { card ->
        Text(text(card.texts.title), style = MaterialTheme.typography.bodyLarge)
        Text(text(card.texts.body))
    }
}

/**
 * 문항의 정답 선택지 문구 ID.
 *
 * @param question 문항
 * @return 정답 선택지 문구 ID
 */
private fun correctOption(question: PlannedQuestion): TextId = question.texts.options[question.correctIndex]

/**
 * 문구 ID를 문장으로 읽는다. 언어는 Android 언어별 리소스가 고른다(10단계에서 대여 언어로 맞춘다).
 *
 * @param id 문구 ID
 * @return 문장
 */
@Composable
private fun text(id: TextId): String = stringResource(id.res)

/** 미리보기용 교육 계획(업체 미설정 상황). */
private val PREVIEW_PLAN = LearningPlan.of(
    CORE_CATALOG,
    LearningSituation(null, null, FuelType.UNKNOWN, HiPassAvailability.UNKNOWN),
)

/** 미리보기용 빈 동작. */
private val NO_ACTIONS = LearningActions({}, {}, {}, {}, {}, {}, {}, {})

/** 학습 카드 미리보기. */
@Preview(showBackground = true)
@Composable
private fun LearningCardPreview() {
    Annyeong_driveTheme {
        LearningScreen(LearningView.Card(PREVIEW_PLAN.cards[2], 3, PREVIEW_PLAN.cards.size), NO_ACTIONS)
    }
}

/** 문제 미리보기(답을 고른 상태). */
@Preview(showBackground = true, heightDp = 700)
@Composable
private fun LearningQuestionPreview() {
    Annyeong_driveTheme {
        LearningScreen(LearningView.Question(PREVIEW_PLAN.questions[1], 2, 5, selected = 1), NO_ACTIONS)
    }
}

/** 결과·복습 미리보기. */
@Preview(showBackground = true, heightDp = 700)
@Composable
private fun LearningResultPreview() {
    Annyeong_driveTheme {
        LearningScreen(LearningView.Result(3, 5, PREVIEW_PLAN.cards.take(2)), NO_ACTIONS)
    }
}
