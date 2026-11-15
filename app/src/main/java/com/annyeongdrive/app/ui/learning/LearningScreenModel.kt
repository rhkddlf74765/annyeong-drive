package com.annyeongdrive.app.ui.learning

import com.annyeongdrive.app.learning.LearningView

/** 교육 흐름이 만든 상태를 카드·문제·해설·복습의 표시 값으로 바꾼다. */
internal sealed interface LearningScreenModel {
    /** 표시할 교육 단계가 없다. */
    data object Hidden : LearningScreenModel

    /** 카드의 문구, 위치와 탐색 버튼 상태. */
    data class Card(
        val title: Int,
        val body: Int,
        val number: Int,
        val total: Int,
        val hasPrevious: Boolean,
        val startsQuiz: Boolean,
    ) : LearningScreenModel

    /** 문항의 문구, 원래 순서의 선택지와 정답 확인 버튼 상태. */
    data class Question(
        val title: Int,
        val options: List<Int>,
        val number: Int,
        val total: Int,
        val selected: Int?,
        val canCheck: Boolean,
    ) : LearningScreenModel

    /** 문항의 위치와 흐름에서 넘긴 정답 및 해설. */
    data class Explanation(
        val answer: Int,
        val body: Int,
        val number: Int,
        val total: Int,
        val correct: Boolean,
    ) : LearningScreenModel

    /** 복습 항목의 제목과 본문. */
    data class ReviewCard(val title: Int, val body: Int)

    /** 응답한 문항 수와 복습할 규칙. */
    data class Result(val answered: Int, val total: Int, val reviewCards: List<ReviewCard>) : LearningScreenModel

    companion object {
        /** 카드와 퀴즈의 문구 ID 및 기존 버튼 상태만 복사한다. */
        fun of(view: LearningView): LearningScreenModel = when (view) {
            LearningView.Hidden -> Hidden
            is LearningView.Card -> Card(
                view.card.texts.title.res, view.card.texts.body.res,
                view.number, view.total, view.hasPrevious, view.isLast,
            )
            is LearningView.Question -> Question(
                view.question.texts.question.res, view.question.texts.options.map { it.res },
                view.number, view.total, view.selected, view.canCheck,
            )
            is LearningView.Explanation -> Explanation(
                view.question.texts.options[view.question.correctIndex].res,
                view.question.texts.explanation.res, view.number, view.total, view.correct,
            )
            is LearningView.Result -> Result(
                view.answered, view.total,
                view.reviewCards.map { ReviewCard(it.texts.title.res, it.texts.body.res) },
            )
        }
    }
}
