"""① 기능별: 교육·퀴즈 전 단계(F6)."""
from smokelib.runtime import APP
from smokelib.scenario import scenario

FORBIDDEN = ("점수", "합격", "준비 완료", "score", "you passed", "ready to go")


@scenario("F6", "feature", "교육은 카드를 차례로 넘기고 퀴즈에서 답·정답 확인·해설·건너뛰기를 거쳐 결과까지 가며 결과에 평가 표현이 없다",
          "기능 실행안 교육·퀴즈, 도메인 원칙(점수·합격 표현 금지), 6단계 교육 흐름")
def learning_all_steps(step):
    """카드 → 퀴즈(답하기·건너뛰기) → 결과."""
    app = APP()
    step.situation("대여 중 정차 대기")
    app.rented(vendor_code=False)
    step.action("교육 열기 → 교육 시작")
    app.open_learning()
    step.equal(app.card_position(), 1, "첫 카드")
    step.action("다음 카드")
    app.screen.tap("learn_action_next")
    step.equal(app.card_position(), 2, "두 번째 카드")
    step.action("카드를 끝까지 넘겨 핵심 퀴즈 시작")
    app.go_to_quiz()
    step.equal(app.question_number(), 1, "문제 1")
    step.action("보기 고르기 → 정답 확인 → 해설 → 다음")
    app.answer_current()
    step.equal(app.question_number(), 2, "문제 2")
    step.action("건너뛰기")
    app.screen.tap("learn_action_skip")
    step.equal(app.question_number(), 3, "건너뛰면 문제 3")
    step.action("남은 문제에 모두 답하기")
    for _ in range(10):
        if app.screen.has("learn_result_title"):
            break
        app.answer_current()
    step.result("결과 화면, 평가 표현 없음")
    step.check(app.screen.has("learn_result_title"), "학습 결과 화면")
    shown = " ".join(app.screen.labels()).lower()
    step.check(not any(word in shown for word in FORBIDDEN), "결과에 점수·합격 같은 표현이 없다")
