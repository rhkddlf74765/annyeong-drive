package com.annyeongdrive.app.drive.state

import com.annyeongdrive.app.drive.DriveContext
import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.drive.Transition

/**
 * 정차 — 교육·퀴즈(복합 상태). 시작 상태는 [LearningStart](T24)이고, 이어 보기는 저장된 위치로 들어간다(B2).
 * 교육·퀴즈 안 어디서든 완료·닫기면 정차 대기로 간다(T23). UX 제한이 생기면 화면·음성을 닫고 정차 대기로 간다(B7).
 */
sealed interface Learning : Parked

/** 교육·퀴즈 — 교육 시작. */
data object LearningStart : Learning {

    /**
     * [구현 예정] 교육 시작 이벤트를 처리한다.
     * implementation-plan.md 1단계 1-b에서 T25를 구현한다.
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? =
        TODO("구현 예정: 1-b T25")
}

/** 교육·퀴즈 — 학습 카드. 카드 사이 이동(이전·다음 규칙)은 화면 안의 일이라 전이가 아니다. */
data object LearningCard : Learning {

    /**
     * [구현 예정] 핵심 퀴즈 시작 이벤트를 처리한다.
     * implementation-plan.md 1단계 1-b에서 T26을 구현한다.
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? =
        TODO("구현 예정: 1-b T26")
}

/** 교육·퀴즈 — 문제. */
data object LearningQuiz : Learning {

    /**
     * [구현 예정] 정답 확인·건너뛰기 이벤트를 처리한다.
     * implementation-plan.md 1단계 1-b에서 T27·T28·B6을 구현한다.
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? =
        TODO("구현 예정: 1-b T27·T28·B6")
}

/** 교육·퀴즈 — 해설. */
data object LearningExplain : Learning {

    /**
     * [구현 예정] 다음 문제·결과 보기 이벤트를 처리한다.
     * implementation-plan.md 1단계 1-b에서 T29·T30을 구현한다.
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? =
        TODO("구현 예정: 1-b T29·T30")
}

/** 교육·퀴즈 — 결과·복습. 나가는 길은 부모의 완료·닫기(T23)뿐이다. */
data object LearningResult : Learning {

    /**
     * [구현 예정] 결과·복습 화면의 이벤트를 처리한다. 자기 전이는 없고 닫기는 부모 규칙에 맡긴다.
     * implementation-plan.md 1단계 1-b에서 구현한다.
     *
     * @param event 받은 이벤트
     * @param context 판단 정보
     * @return 전이. 처리하지 않는 이벤트면 null
     */
    override fun handle(event: DriveEvent, context: DriveContext): Transition? =
        TODO("구현 예정: 1-b 부모 규칙 위임")
}
