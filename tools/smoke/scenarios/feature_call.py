"""① 기능별: 통화 중 안내(F9)."""
import time

from smokelib.app import PARKING_ZONE_POINT
from smokelib.logs import spoken
from smokelib.runtime import APP
from smokelib.scenario import scenario

from .common import parking_utterance, wait_spoken


@scenario("F9", "feature", "통화 중 주정차 경고는 말하지 않고 알림만 띄우고, 통화가 끝나면 다시 말한다",
          "11/14 사용자 결정 (가)(통화 중에는 음성 대신 알림만), 11/15 에뮬레이터 확인(통화가 남는 문제)")
def call_quiet(step):
    """통화 중 정차 → 통화 종료 → 다시 정차."""
    app = APP()
    step.situation("영어 대여, 구역 안 주행, 전화를 받아 통화 중")
    app.rented(point=PARKING_ZONE_POINT, vendor_code=False)
    app.drive_off(30)
    app.device.call_start()
    try:
        app.device.logcat_clear()
        step.action("통화 중 멈추고 P")
        app.stop_and_park()
        step.check(app.wait_screen("PARKING_WARNING", 15), "경고 화면")
        time.sleep(5)
        step.check(not spoken(app.speech()), "통화 중에는 말하지 않는다")
        step.check(bool(app.notifications("parking_warning")), "경고 알림은 뜬다")
        app.screen.tap("parking_warning_acknowledge")
    finally:
        app.device.call_end()
    step.action("통화 종료 → 다시 출발 → 멈추고 P")
    app.drive_off(30)
    app.device.logcat_clear()
    app.stop_and_park()
    step.result("다시 말한다(두 번째 만남이라 1문장)")
    expected = [parking_utterance("en", 2)]
    texts = [e.text for e in spoken(wait_spoken(app, 1))]
    step.equal(texts, expected, "통화가 끝나면 경고를 말한다")
