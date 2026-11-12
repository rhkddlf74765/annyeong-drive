package com.annyeongdrive.app.learning

/**
 * 핵심 퀴즈 문항의 순서를 알려 주는 창구. 교육 콘텐츠(6단계)가 구현한다.
 * 교육 진행(LearningProgress)은 순서를 저장하지 않고 판단할 때 이것으로 받는다.
 */
fun interface QuizOrder {

    /**
     * 핵심 퀴즈 문항 식별자를 화면에 나오는 순서대로 돌려준다.
     *
     * @return 문항 식별자 목록(예: quiz.lane_direction)
     */
    fun questionIds(): List<String>
}
