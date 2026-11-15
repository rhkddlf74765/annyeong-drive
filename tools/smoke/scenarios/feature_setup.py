"""① 기능별: 언어 선택·출발 전 점검(F5)."""
import time

from smokelib.runtime import APP
from smokelib.scenario import scenario


@scenario("F5", "feature", "영어를 고르면 점검이 영어로 나오고, 안전띠를 직접 확인하지 않으면 점검을 마칠 수 없다",
          "기능 실행안 출발 전 점검, 도메인 원칙(경고 없음은 착용이 아니다), 11/14 대여 언어 화면 결정")
def language_and_precheck(step):
    """언어 선택 → 점검(체크 없이 마침 시도 → 체크 → 마침)."""
    app = APP()
    step.situation("앱 비움 → 운전석 앉음 → 언어 선택")
    app.reset()
    app.console.seat(True)
    step.check(app.wait_screen("LANGUAGE", 20), "앉으면 언어 선택")
    labels = app.screen.labels()
    both = [app.strings.text("setup_language_title", lang) for lang in ("values-en", "values")]
    step.check(all(text in labels for text in both), f"언어를 고르기 전 제목은 영어·한국어를 함께 보여 준다: {labels[:4]}")
    step.action("English")
    app.screen.tap("language_english")
    step.check(app.wait_screen("PRECHECK", 20), "점검 화면")
    english_title = app.strings.text("precheck_title", "values-en")
    step.check(english_title in app.screen.labels(), f"점검 제목이 영어('{english_title}')")
    step.action("안전띠 체크 없이 점검 마침")
    app.screen.tap("precheck_done")
    time.sleep(2)
    step.check(app.current() == "PRECHECK", "체크 없이는 점검 화면에 남는다")
    step.action("안전띠 확인 체크 → 점검 마침")
    app.screen.tap("precheck_seatbelt_confirm")
    app.screen.tap("precheck_done")
    step.result("교육 시작 제안")
    step.check(app.wait_screen("LEARNING_START", 20), f"교육 시작 제안(지금: {app.current()})")
