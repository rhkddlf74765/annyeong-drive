package com.annyeongdrive.app.ui.learning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.annyeongdrive.app.R
import com.annyeongdrive.app.learning.CORE_CATALOG
import com.annyeongdrive.app.learning.LearningPlan
import com.annyeongdrive.app.learning.LearningSituation
import com.annyeongdrive.app.learning.LearningView
import com.annyeongdrive.app.ui.theme.Annyeong_driveTheme
import com.annyeongdrive.app.ui.theme.PrimaryAction
import com.annyeongdrive.app.ui.theme.QuietAction
import com.annyeongdrive.app.ui.theme.ScreenHeading
import com.annyeongdrive.app.ui.theme.ScreenSection
import com.annyeongdrive.app.ui.theme.SecondaryAction
import com.annyeongdrive.app.ui.theme.VehicleScreen
import com.annyeongdrive.app.ui.theme.VehicleTargetHeight
import com.annyeongdrive.app.vehicle.FuelType
import com.annyeongdrive.app.vendor.HiPassAvailability

/** 카드 탐색, 퀴즈 응답, 교육 닫기에 연결된 기존 동작. */
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

/** 교육 흐름에서 받은 표시 모델에 맞춰 카드·문제·해설·복습을 그린다. */
@Composable
internal fun LearningScreen(model: LearningScreenModel, actions: LearningActions, modifier: Modifier = Modifier) {
    val position = when (model) {
        is LearningScreenModel.Card -> model.number
        is LearningScreenModel.Question -> model.number
        is LearningScreenModel.Explanation -> model.number
        else -> null
    }
    key(model::class, position) {
        VehicleScreen(modifier) {
            when (model) {
                is LearningScreenModel.Card -> CardContentView(model, actions)
                is LearningScreenModel.Question -> QuestionView(model, actions)
                is LearningScreenModel.Explanation -> ExplanationView(model, actions)
                is LearningScreenModel.Result -> ResultView(model)
                LearningScreenModel.Hidden -> Unit
            }
            QuietAction(R.string.learn_action_close, actions.onClose)
        }
    }
}

/** 학습 카드와 기존 탐색 버튼을 그린다. */
@Composable
private fun CardContentView(model: LearningScreenModel.Card, actions: LearningActions) {
    Text(stringResource(R.string.learn_card_position, model.number, model.total))
    ScreenHeading(stringResource(model.title))
    ScreenSection { Text(stringResource(model.body)) }
    if (model.startsQuiz) {
        PrimaryAction(R.string.learn_action_start_quiz, actions.onStartQuiz)
    } else {
        PrimaryAction(R.string.learn_action_next, actions.onNextCard)
    }
    SecondaryAction(R.string.learn_action_prev, actions.onPreviousCard, enabled = model.hasPrevious)
}

/** 선택지 전체를 터치할 수 있게 하고, 기존 위치를 선택 동작에 넘긴다. */
@Composable
private fun QuestionView(model: LearningScreenModel.Question, actions: LearningActions) {
    Text(stringResource(R.string.learn_question_position, model.number, model.total))
    ScreenHeading(stringResource(model.title))
    Column(
        modifier = Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        model.options.forEachIndexed { index, option ->
            QuestionOption(option, selected = model.selected == index, onSelect = { actions.onSelect(index) })
        }
    }
    PrimaryAction(R.string.learn_action_check, actions.onCheck, enabled = model.canCheck)
    SecondaryAction(R.string.learn_action_skip, actions.onSkip)
}

/** 문구가 여러 줄인 경우에도 선택 가능한 영역을 유지한다. */
@Composable
private fun QuestionOption(label: Int, selected: Boolean, onSelect: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) colors.surfaceVariant else colors.surface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = VehicleTargetHeight)
                .selectable(selected, role = Role.RadioButton, onClick = onSelect).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Text(stringResource(label))
        }
    }
}

/** 교육 모델에서 받은 정답과 해설을 표시한다. */
@Composable
private fun ExplanationView(model: LearningScreenModel.Explanation, actions: LearningActions) {
    Text(stringResource(R.string.learn_question_position, model.number, model.total))
    if (model.correct) Text(stringResource(R.string.learn_explain_correct), style = MaterialTheme.typography.titleMedium)
    ScreenHeading(stringResource(R.string.learn_explain_answer, stringResource(model.answer)))
    ScreenSection { Text(stringResource(model.body)) }
    PrimaryAction(R.string.learn_action_next_question, actions.onNext)
}

/** 응답한 문항 수와 복습할 규칙을 보여 준다. */
@Composable
private fun ResultView(model: LearningScreenModel.Result) {
    ScreenHeading(stringResource(R.string.learn_result_title))
    Text(stringResource(R.string.learn_result_answered, model.answered, model.total))
    Text(stringResource(R.string.learn_review_title), style = MaterialTheme.typography.titleMedium)
    if (model.reviewCards.isEmpty()) Text(stringResource(R.string.learn_review_empty))
    model.reviewCards.forEach { card ->
        ScreenSection {
            Text(stringResource(card.title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(card.body))
        }
    }
}

private val PREVIEW_PLAN = LearningPlan.of(
    CORE_CATALOG,
    LearningSituation(null, null, FuelType.UNKNOWN, HiPassAvailability.UNKNOWN),
)
private val NO_ACTIONS = LearningActions({}, {}, {}, {}, {}, {}, {}, {})

/** 학습 카드 미리보기. */
@Preview(showBackground = true)
@Composable
private fun LearningCardPreview() {
    Annyeong_driveTheme {
        val view = LearningView.Card(PREVIEW_PLAN.cards[2], 3, PREVIEW_PLAN.cards.size)
        LearningScreen(LearningScreenModel.of(view), NO_ACTIONS)
    }
}

/** 선택한 답이 있는 문제 미리보기. */
@Preview(showBackground = true, heightDp = 700)
@Composable
private fun LearningQuestionPreview() {
    Annyeong_driveTheme {
        val view = LearningView.Question(PREVIEW_PLAN.questions[1], 2, 5, selected = 1)
        LearningScreen(LearningScreenModel.of(view), NO_ACTIONS)
    }
}

/** 결과·복습 미리보기. */
@Preview(showBackground = true, heightDp = 700)
@Composable
private fun LearningResultPreview() {
    Annyeong_driveTheme {
        val view = LearningView.Result(3, 5, PREVIEW_PLAN.cards.take(2))
        LearningScreen(LearningScreenModel.of(view), NO_ACTIONS)
    }
}
