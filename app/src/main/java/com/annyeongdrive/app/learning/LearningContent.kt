package com.annyeongdrive.app.learning

/**
 * 지금 앱의 교육 문구 버전. 저장된 진행의 버전과 다르면 교육 진행만 새로 시작한다(결정 문서 4장).
 * 기준 문구는 기능 실행안(11/10)이다.
 */
const val CURRENT_CONTENT_VERSION = "2026-11-10"

/**
 * 교육 콘텐츠(6단계)가 생기기 전의 문항 순서. 문항이 없으므로 미완료 교육도, 마지막 문항도 없다.
 * 6단계에서 실제 핵심 퀴즈 5문항 순서로 바꾼다.
 */
val NO_QUIZ_YET: QuizOrder = QuizOrder { emptyList() }
