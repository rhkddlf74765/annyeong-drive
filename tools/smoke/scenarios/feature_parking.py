"""① 기능별: 주정차 경고(F7 구역 안, F8 구역 밖)."""
import time

from smokelib.app import OUTSIDE_ZONE_POINT, PARKING_ZONE_POINT
from smokelib.logs import errors, spoken
from smokelib.runtime import APP
from smokelib.scenario import scenario

from .common import PARKED_SCREENS, parking_utterance, wait_spoken


@scenario("F7", "feature", "주정차 단속 구역에서 멈추고 P를 넣으면 경고 화면·영어 음성 2문장·경고 알림이 나오고 확인하면 정차 대기로 간다",
          "기능 보드 주정차 경고, 상태 머신 T13·T17, 8단계 결정(처음 2문장), 11/15 콘솔 차속 특성(0 확인 뒤 P)")
def parking_warning(step):
    """구역 안 정차."""
    app = APP()
    step.situation("영어 대여, 주정차 카메라(PC5) 앞에서 30km/h 주행")
    app.rented(point=PARKING_ZONE_POINT, vendor_code=False)
    app.drive_off(30)
    app.device.logcat_clear()
    step.action("멈춘 것을 확인하고 P")
    app.stop_and_park()
    step.result("경고 화면, 음성, 알림")
    step.check(app.wait_screen("PARKING_WARNING", 15), f"경고 화면(지금: {app.current()})")
    expected = parking_utterance("en", 1)
    events = wait_spoken(app, 1)
    texts = [e.text for e in spoken(events)]
    step.check(expected in texts, f"경고 두 문장을 한 번에 말한다: {texts}")
    step.check(all(e.locale == "en_US" for e in spoken(events)), "영어(en_US)로 말한다")
    step.check(not errors(events), "재생 오류가 없다")
    warning = app.notifications("parking_warning")
    step.check(bool(warning) and warning[-1].text == expected, "경고 알림에 같은 문장")
    step.action("확인했어요")
    app.screen.tap("parking_warning_acknowledge")
    found = app.wait_parked()
    step.check(found in PARKED_SCREENS, f"확인하면 정차 화면(지금: {found})")


@scenario("F8", "feature", "주정차 단속 구역 밖에서 정차하면 경고하지 않는다",
          "기능 보드 주정차 경고(검수된 구역 데이터가 있을 때만), 도메인 원칙(GPS 반경으로 금지를 확정하지 않는다)")
def no_warning_outside(step):
    """구역 밖 정차."""
    app = APP()
    step.situation("영어 대여, 구역 밖에서 30km/h 주행")
    app.rented(point=OUTSIDE_ZONE_POINT, vendor_code=False)
    app.drive_off(30)
    step.action("멈춘 것을 확인하고 P")
    app.stop_and_park()
    time.sleep(4)
    step.result("경고 없음")
    step.check(app.current() != "PARKING_WARNING", f"경고 화면이 아님(지금: {app.current()})")
    step.check(not app.notifications("parking_warning"), "경고 알림이 없다")
