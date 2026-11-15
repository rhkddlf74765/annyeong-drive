"""① 기능별: 반납(F10), 업체 설정 첫 코드(F11)."""
from smokelib.app import ADMIN_CODE
from smokelib.runtime import APP
from smokelib.scenario import scenario


@scenario("F10", "feature", "틀린 관리자 코드로는 반납되지 않고 맞는 코드로 반납하면 대기 화면으로 간다",
          "4단계 반납 처리, VendorConfig 관리자 자격(실패 횟수), 11/13 관리자 코드 규칙")
def return_with_code(step):
    """틀린 코드 → 맞는 코드."""
    app = APP()
    step.situation("대여 중 정차 대기, 업체 코드 Demo2026")
    app.rented()
    step.action("반납 완료 → 틀린 코드")
    app.screen.tap("action_return")
    step.check(app.wait_screen("RETURN", 15), "반납 화면")
    app.enter_code("Wrong123")
    step.check(app.screen.wait_any(["code_wrong"], 10) is not None, "코드가 맞지 않는다는 안내")
    step.check(app.current() == "RETURN", "반납 화면에 남는다")
    step.action("맞는 코드")
    app.enter_code(ADMIN_CODE)
    step.result("대기 화면")
    step.check(app.wait_screen("WAITING", 20), f"대기 화면(지금: {app.current()})")


@scenario("F11", "feature", "처음 쓰는 기기에서 업체 설정으로 관리자 코드를 정하면 반납 화면이 코드 입력을 받는다",
          "4단계 업체 설정 화면, 11/13 관리자 코드 규칙(영문·숫자 8자리)")
def first_vendor_code(step):
    """첫 코드 설정 → 반납 화면 확인 → 취소."""
    app = APP()
    step.situation("대여 중 정차 대기, 코드 미설정")
    app.rented(vendor_code=False)
    step.action("반납 완료(코드 미설정)")
    app.screen.tap("action_return")
    step.check(app.screen.wait_any(["return_code_not_set"], 10) is not None, "코드를 먼저 정하라는 안내")
    app.screen.tap("action_cancel")
    step.check(app.wait_screen("PARKED_HOME", 15), "취소하면 정차 대기")
    step.action("업체 설정에서 첫 코드 Demo2026")
    app.set_vendor_code()
    step.action("반납 완료")
    app.screen.tap("action_return")
    step.result("코드 입력 안내, 취소하면 정차 대기")
    step.check(app.screen.wait_any(["return_entry"], 10) is not None, "관리자 코드 입력 안내")
    app.screen.tap("action_cancel")
    step.check(app.wait_screen("PARKED_HOME", 15), "취소하면 정차 대기")
