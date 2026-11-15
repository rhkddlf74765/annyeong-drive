"""③ 전체 흐름: 대여부터 주행(GPX 경로 음성)·정차·반납·다음 손님까지(E1~E3)."""
import time

from smokelib.logs import errors, finished_count, spoken
from smokelib.route import Replay, load_expectation
from smokelib.runtime import APP
from smokelib.scenario import scenario

from .common import ROUTE_EN, ROUTE_KO, wait_spoken

POSITION_TOLERANCE_KM = 0.3


def _drive_route(app, expected) -> Replay:
    """출발해 경로 전체를 재생하고 끝에서 멈춰 P를 넣는다."""
    replay = Replay(app.device, expected.route, expected.kmh)
    app.drive_off(expected.kmh)
    replay.start()
    replay.wait()
    app.stop_and_park()
    return replay


@scenario("E1", "full", "영어 대여로 대여·점검·교육 뒤 시연 경로를 달리면 기대한 안내를 기대한 곳에서 끝까지 말하고, 끝에서 경고 후 반납하면 다음 손님을 맞는다",
          "기능 실행안 전체 흐름, 8단계 주행 중 규칙 안내·주정차 경고, 기대 목록 route_en.json(JVM RouteGuidanceSignalTest와 같은 파일)")
def english_rental(step):
    """대여 → 점검 → 교육 카드 2장 → 경로 주행 → 경고 → 반납 → 다음 손님."""
    app = APP()
    expected = load_expectation(ROUTE_EN)
    start = Replay(app.device, expected.route, expected.kmh).start_point
    step.situation("앱 비움, 경로 시작점, 운전석 앉음 → English → 점검")
    app.reset(start)
    app.sit_and_choose("en")
    app.complete_precheck()
    step.action("교육 시작 → 카드 2장 → 교육 닫기 → 업체 코드")
    app.screen.tap("action_learning_begin")
    app.screen.tap("learn_action_next")
    app.screen.tap("learn_action_close")
    app.to_parked_home()
    app.set_vendor_code()
    app.device.logcat_clear()
    step.action(f"출발 → 경로 전체 재생({expected.kmh:g}km/h) → 끝에서 멈추고 P")
    replay = _drive_route(app, expected)
    step.result("주행 안내 음성")
    want = expected.joined()
    events = wait_spoken(app, len(want) + 1, timeout=40)
    speaks = spoken(events)
    driving = speaks[:len(want)]
    step.equal([e.text for e in driving], want, "주행 안내 문장·순서·횟수가 기대 목록과 같다")
    step.check(all(e.locale == "en_US" for e in speaks), "모두 영어(en_US)")
    step.check(not errors(events), "재생 오류가 없다")
    step.check(finished_count(events) >= len(speaks), f"모든 문장을 끝까지 재생(완료 {finished_count(events)} / 요청 {len(speaks)})")
    _check_positions(step, expected, driving, replay)
    step.result("경로 끝 주정차 경고")
    step.check(app.current() == "PARKING_WARNING", f"경고 화면(지금: {app.current()})")
    step.equal([e.text for e in speaks[len(want):]], [expected.stop_joined()], "경고 문장을 말한다")
    step.action("확인 → 반납 → 비움 → 앉음")
    app.screen.tap("parking_warning_acknowledge")
    app.return_car()
    app.console.seat(False)
    app.console.seat(True)
    step.check(app.wait_screen("LANGUAGE", 20), "다음 손님의 언어 선택")


def _check_positions(step, expected, driving, replay) -> None:
    """안내마다 말한 순간의 경로 위치가 기대 위치(atKm)와 0.3km 안인지 본다."""
    for announcement, said in zip(expected.announcements, driving):
        km = replay.km_at(said.time)
        if announcement.at_km is None or km is None:
            step.check(False, f"'{announcement.sentences[0]}' 위치를 알 수 없음")
            continue
        step.check(abs(km - announcement.at_km) <= POSITION_TOLERANCE_KM,
                   f"'{announcement.sentences[0][:28]}…' {km:.2f}km에서(기대 {announcement.at_km:.2f}km)")


@scenario("E2", "full", "한국어 대여로 시연 경로를 달리면 말하지 않고 같은 순서의 한국어 안내를 알림으로 띄우고, 끝에서 경고 알림 후 반납된다",
          "8단계 결정(한국어 대여는 음성 대신 알림), 기대 목록 route_ko.json")
def korean_rental(step):
    """한국어 대여 → 경로 주행 → 경고 → 반납."""
    app = APP()
    expected = load_expectation(ROUTE_KO)
    start = Replay(app.device, expected.route, expected.kmh).start_point
    step.situation("앱 비움, 경로 시작점, 한국어 대여, 업체 코드")
    app.rented("ko", point=start)
    app.device.logcat_clear()
    step.action("출발 → 경로 전체 재생 → 끝에서 멈추고 P")
    _drive_route(app, expected)
    time.sleep(3)
    step.result("음성 없이 알림")
    step.check(not spoken(app.speech()), "한국어 대여는 말하지 않는다")
    notes = [n.text for n in app.notifications("road_rule")]
    step.equal(notes, expected.joined(), "주행 안내 알림이 기대 목록과 같은 순서·문장")
    warning = app.notifications("parking_warning")
    step.check(bool(warning) and warning[-1].text == expected.stop_joined(), "경로 끝 경고 알림")
    step.action("확인 → 12초 기다림 → 반납")
    app.screen.tap("parking_warning_acknowledge")
    time.sleep(12)
    step.check(not app.notifications("parking_warning"), "주정차 경고 알림이 10초 뒤 사라진다")
    app.covered_taps.clear()
    app.return_car()
    step.check(app.current() == "WAITING", "반납 뒤 대기 화면")
    step.check(not app.covered_taps, f"알림이 반납 버튼을 가리지 않는다(가린 버튼: {app.covered_taps})")


@scenario("E3", "full", "교육 중 출발했다 정차한 뒤 앱을 껐다 켜도 대여와 교육 진행이 남아 이어 볼 수 있다",
          "결정 문서 4장(진행 저장 DataStore), 9단계 확인 3")
def restart_app(step):
    """교육 카드 2 → 출발 → 정차 → 앱 재시작 → 이어 보기."""
    app = APP()
    step.situation("대여 중 교육 카드 2")
    app.rented(vendor_code=False)
    app.open_learning()
    app.screen.tap("learn_action_next")
    step.equal(app.card_position(), 2, "카드 2")
    step.action("출발 → 정차 → 앱 강제 종료 → 다시 실행")
    app.drive_off(30)
    app.stop_and_park()
    app.wait_parked()
    app.device.force_stop()
    app.device.start_app()
    step.result("이어서 확인할까요 → 이어 보기 → 카드 2")
    step.check(app.wait_screen("RESUME_OFFER", 30), f"이어서 확인할까요(지금: {app.current()})")
    app.screen.tap("action_resume")
    step.equal(app.card_position(), 2, "멈춘 카드로 돌아간다")
