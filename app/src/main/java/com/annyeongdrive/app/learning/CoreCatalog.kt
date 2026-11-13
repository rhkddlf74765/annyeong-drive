package com.annyeongdrive.app.learning

import com.annyeongdrive.app.R
import com.annyeongdrive.app.learning.SituationCondition.Companion.fuelIs
import com.annyeongdrive.app.vehicle.FuelType

/**
 * 1차 제출 교육 콘텐츠: 학습 카드 A-1~A-6과 핵심 퀴즈 Q1~Q5(기능 실행안 부록 A·B).
 * 문구는 임시 문구다. 부록 원문을 받으면 strings.xml만 바꾸고 [CURRENT_CONTENT_VERSION]을 올린다.
 * 카드·문항을 더하거나 변형을 붙일 때는 이 파일 하나만 고친다(11/13 결정).
 */
val CORE_CATALOG = LearningCatalog(
    version = CURRENT_CONTENT_VERSION,
    cards = listOf(
        CardContent(
            id = "card.precheck",
            texts = CardTexts(TextId(R.string.learn_card_precheck_title), TextId(R.string.learn_card_precheck_body)),
            variants = listOf(
                Variant(
                    fuelIs(FuelType.ELECTRIC),
                    CardTexts(TextId(R.string.learn_card_precheck_title), TextId(R.string.learn_card_precheck_body_ev)),
                ),
            ),
        ),
        CardContent(
            id = "card.lane_direction",
            texts = CardTexts(TextId(R.string.learn_card_lane_title), TextId(R.string.learn_card_lane_body)),
        ),
        CardContent(
            id = "card.protected_zone",
            texts = CardTexts(TextId(R.string.learn_card_zone_title), TextId(R.string.learn_card_zone_body)),
        ),
        CardContent(
            id = "card.intersection",
            texts = CardTexts(TextId(R.string.learn_card_turn_title), TextId(R.string.learn_card_turn_body)),
        ),
        CardContent(
            id = "card.speed_units",
            texts = CardTexts(TextId(R.string.learn_card_speed_title), TextId(R.string.learn_card_speed_body)),
        ),
        CardContent(
            id = "card.korean_signs",
            texts = CardTexts(TextId(R.string.learn_card_signs_title), TextId(R.string.learn_card_signs_body)),
        ),
    ),
    questions = listOf(
        QuestionContent(
            id = "quiz.lane_direction",
            texts = question(
                R.string.learn_q1_question,
                listOf(R.string.learn_q1_o1, R.string.learn_q1_o2, R.string.learn_q1_o3, R.string.learn_q1_o4),
                R.string.learn_q1_explain,
            ),
            correctIndex = 0,
            relatedCardId = "card.lane_direction",
        ),
        QuestionContent(
            id = "quiz.school_zone_stop",
            texts = question(
                R.string.learn_q2_question,
                listOf(R.string.learn_q2_o1, R.string.learn_q2_o2, R.string.learn_q2_o3, R.string.learn_q2_o4),
                R.string.learn_q2_explain,
            ),
            correctIndex = 1,
            relatedCardId = "card.protected_zone",
        ),
        QuestionContent(
            id = "quiz.unprotected_left",
            texts = question(
                R.string.learn_q3_question,
                listOf(R.string.learn_q3_o1, R.string.learn_q3_o2, R.string.learn_q3_o3, R.string.learn_q3_o4),
                R.string.learn_q3_explain,
            ),
            correctIndex = 2,
            relatedCardId = "card.intersection",
        ),
        QuestionContent(
            id = "quiz.right_turn_pedestrian",
            texts = question(
                R.string.learn_q4_question,
                listOf(R.string.learn_q4_o1, R.string.learn_q4_o2, R.string.learn_q4_o3, R.string.learn_q4_o4),
                R.string.learn_q4_explain,
            ),
            correctIndex = 0,
            relatedCardId = "card.intersection",
        ),
        QuestionContent(
            id = "quiz.roundabout_yield",
            texts = question(
                R.string.learn_q5_question,
                listOf(R.string.learn_q5_o1, R.string.learn_q5_o2, R.string.learn_q5_o3, R.string.learn_q5_o4),
                R.string.learn_q5_explain,
            ),
            correctIndex = 3,
            relatedCardId = "card.intersection",
        ),
    ),
)

/**
 * 문구 ID 묶음으로 문항 문구를 만든다(이 파일의 반복을 줄이는 용도).
 *
 * @param question 문제 문구 ID
 * @param options 선택지 문구 ID 4개
 * @param explanation 해설 문구 ID
 * @return 문항 문구
 */
private fun question(question: Int, options: List<Int>, explanation: Int): QuestionTexts =
    QuestionTexts(TextId(question), options.map { TextId(it) }, TextId(explanation))
