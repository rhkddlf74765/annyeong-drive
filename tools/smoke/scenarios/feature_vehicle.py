"""① 기능별: 기어(F1), 차속(F2)."""
import time

from smokelib.runtime import APP
from smokelib.scenario import scenario

from .common import PARKED_SCREENS


@scenario("F1", "feature", "기어를 D·R로 바꾸면 주행이고, N은 주행으로 보지 않으며, P·0이어야 정차로 돌아온다",
          "CLAUDE.md 아키텍처(주행 진입 D/R·차속), 상태 머신 T10·T11")
def gear(step):
    """정차 대기에서 기어만 바꿔 주행 화면과 정차 화면이 바뀌는지 본다."""
    app = APP()
    step.situation("대여 중 정차 대기(P·0), 차속은 0으로 둔다")
    app.rented(vendor_code=False)
    step.action("N")
    app.console.gear("N")
    step.check(app.current() in PARKED_SCREENS, f"정차 중 N은 주행이 아니다(화면: {app.current()})")
    step.action("D")
    app.console.gear("D")
    step.check(app.wait_screen("DRIVING", 15), "D는 주행 화면")
    step.action("R")
    app.console.gear("R")
    time.sleep(2)
    step.check(app.current() not in PARKED_SCREENS, f"R도 주행(후방 카메라가 화면을 덮을 수 있어 정차 화면이 아닌지로 본다, 지금: {app.current()})")
    step.action("N (차속 0)")
    app.console.gear("N")
    step.check(app.wait_screen("DRIVING", 10), "주행 중 N은 아직 정차가 아니다(정차는 P·0일 때만)")
    step.action("P")
    app.console.gear("P")
    step.result("정차 화면으로 돌아온다")
    found = app.wait_parked()
    step.check(found in PARKED_SCREENS, f"P·0에서 정차 화면(지금: {found})")


@scenario("F2", "feature", "기어가 P여도 차속이 생기면 주행으로 보고, 차속이 0이 되면 정차로 돌아온다",
          "CLAUDE.md 아키텍처(차속 발생도 주행 진입), 도메인 원칙(교육은 P + 차속 0일 때만)")
def speed(step):
    """P에서 차속만 올렸다 내린다."""
    app = APP()
    step.situation("대여 중 정차 대기(P·0)")
    app.rented(vendor_code=False)
    step.action("기어 P 그대로 차속 5km/h")
    app.console.cruise(5)
    step.check(app.wait_screen("DRIVING", 15), "차속이 생기면 주행 화면")
    step.action("차속 0")
    app.console.stop_vehicle()
    step.result("정차 화면으로 돌아온다")
    found = app.wait_parked()
    step.check(found in PARKED_SCREENS, f"P·0에서 정차 화면(지금: {found})")
