"""② 부분 연결: 두세 기능을 이어서 조작하고 중간마다 판정한다(L1~L9)."""
import time

from smokelib.app import PARKING_ZONE_POINT
from smokelib.logs import spoken
from smokelib.route import Replay, load_expectation
from smokelib.runtime import APP
from smokelib.scenario import scenario

from .common import ROUTE_EN, parking_utterance, wait_spoken


def _quiz_question_two(app) -> None:
    """대여 중 교육을 열어 퀴즈 문제 2까지 간다."""
    app.rented(vendor_code=False)
    app.open_learning()
    app.go_to_quiz()
    app.answer_current()


@scenario("L1", "linked", "퀴즈 중 출발하면 교육이 닫히고 학습 음성이 멈추며, 정차 뒤 이어 보기로 같은 문제로 돌아간다",
          "CLAUDE.md 아키텍처(주행 진입 시 교육 닫기·학습 음성 중단을 한 번에), 상태 머신 T9·T19·B2, 7단계")
def quiz_drive_resume(step):
    """퀴즈 2번 → 출발 → 정차 → 이어 보기."""
    app = APP()
    step.situation("퀴즈 문제 2")
    _quiz_question_two(app)
    step.equal(app.question_number(), 2, "문제 2")
    app.device.logcat_clear()
    step.action("D·30km/h")
    app.drive_off(30)
    step.check(any(e.kind == "stop" for e in app.speech()), "학습 음성 멈춤 지시가 나간다")
    step.action("멈추고 P")
    app.stop_and_park()
    step.check(app.wait_screen("RESUME_OFFER", 15), f"이어서 확인할까요(지금: {app.current()})")
    step.action("이어 보기")
    app.screen.tap("action_resume")
    step.result("같은 문제(2)로 돌아간다")
    step.equal(app.question_number(), 2, "멈춘 문제로 돌아간다")


@scenario("L2", "linked", "퀴즈 중 출발 뒤 정차해 나중에 보기를 고르면 다음 정차에 다시 제안한다",
          "상태 머신 T20·B1, 7단계 결정(나중에 보기)")
def quiz_drive_later(step):
    """퀴즈 → 출발 → 정차 → 나중에 → 출발 → 정차."""
    app = APP()
    step.situation("퀴즈 문제 2")
    _quiz_question_two(app)
    step.action("출발 → 정차 → 나중에 보기")
    app.drive_off(30)
    app.stop_and_park()
    step.check(app.wait_screen("RESUME_OFFER", 15), "이어서 확인할까요")
    app.screen.tap("action_later")
    step.check(app.wait_screen("PARKED_HOME", 15), "나중에 보기 → 정차 대기")
    step.action("다시 출발 → 정차")
    app.drive_off(30)
    app.stop_and_park()
    step.result("다시 제안한다")
    found = app.wait_parked()
    step.check(found in ("RESUME_OFFER", "LEARNING_START"), f"다음 정차에 다시 제안(지금: {found})")


@scenario("L3", "linked", "이어서 확인할까요에서 처음부터 보기를 고르면 첫 카드부터 다시 본다",
          "7단계 결정(처음부터 보기: 새 회차 저장 후 이어 보기)")
def restart_from_offer(step):
    """퀴즈 → 출발 → 정차 → 처음부터."""
    app = APP()
    step.situation("퀴즈 중 출발 → 정차 → 이어서 확인할까요")
    _quiz_question_two(app)
    app.drive_off(30)
    app.stop_and_park()
    step.check(app.wait_screen("RESUME_OFFER", 15), "이어서 확인할까요")
    step.action("처음부터 보기")
    app.screen.tap("action_restart")
    step.result("첫 카드")
    step.check(app.wait_screen("LEARNING", 15), "교육 화면")
    step.equal(app.card_position(), 1, "첫 카드부터")


@scenario("L4", "linked", "P인데 차속이 생기면 교육을 열 수 없고, 차속이 0이 되면 다시 열 수 있다",
          "도메인 원칙(교육은 P + 차속 0 + 제한 없음일 때만), 11/15 신호 테스트 기대값(사용자 확인)")
def moving_in_park(step):
    """P에서 차속 5 → 0 → 교육 열기."""
    app = APP()
    step.situation("대여 중 정차 대기")
    app.rented(vendor_code=False)
    step.action("기어 P 그대로 차속 5km/h")
    app.console.cruise(5)
    step.check(app.wait_screen("DRIVING", 15), "주행으로 보고 교육 열기 버튼이 없다")
    step.check(not app.screen.has("action_learning_open"), "교육 열기를 누를 수 없다")
    step.action("차속 0 → 교육 열기")
    app.console.stop_vehicle()
    app.to_parked_home()
    app.open_learning()
    step.result("교육이 열린다")
    step.check(app.current() == "LEARNING", "교육 화면")


@scenario("L5", "linked", "출발 전 점검 중 출발하면 시스템이 가리고, 정차하면 점검을 이어서 마칠 수 있다",
          "9단계 확인 6, 7단계 결정(대여 설정 화면 주행 중은 AAOS 가림에 맡김)")
def precheck_drive(step):
    """점검 화면 → 출발 → 정차 → 점검 마침."""
    app = APP()
    step.situation("출발 전 점검 화면")
    app.reset()
    app.sit_and_choose("en")
    step.action("D·30km/h")
    app.console.drive(30)
    time.sleep(3)
    labels = app.screen.labels()
    step.result(f"주행 중 화면 글자: {labels[:4]}")
    step.action("멈추고 P → 안전띠 확인 → 점검 마침")
    app.stop_and_park()
    step.check(app.wait_screen("PRECHECK", 15), f"점검 화면 그대로(지금: {app.current()})")
    app.complete_precheck()
    step.check(app.current() == "LEARNING_START", "점검을 마칠 수 있다")


def _warned_in_zone(app) -> None:
    """구역 안에서 달리다 정차해 첫 경고를 받고 확인한다."""
    app.rented(point=PARKING_ZONE_POINT)
    app.drive_off(30)
    app.stop_and_park()
    if not app.wait_screen("PARKING_WARNING", 15):
        raise AssertionError(f"첫 경고 화면이 아님: {app.current()}")


@scenario("L6", "linked", "같은 자리에서 P → D → P로 다시 정차하면 다시 경고하고 두 번째는 한 문장이다",
          "상태 머신 T13 가드 해석 보완(P로 바꾼 순간에만), 8단계 결정(처음 2문장·이후 1문장)")
def warn_again(step):
    """경고 → 확인 → D → P."""
    app = APP()
    step.situation("구역 안 첫 경고 → 확인")
    _warned_in_zone(app)
    app.screen.tap("parking_warning_acknowledge")
    app.device.logcat_clear()
    step.action("D → 멈추고 P")
    app.drive_off(30)
    app.stop_and_park()
    step.result("다시 경고, 한 문장")
    step.check(app.wait_screen("PARKING_WARNING", 15), "경고 화면")
    texts = [e.text for e in spoken(wait_spoken(app, 1))]
    step.equal(texts, [parking_utterance("en", 2)], "두 번째 경고는 한 문장")


@scenario("L7", "linked", "주정차 경고 화면에서 출발하면 경고 화면이 닫히고 주행 화면이 된다",
          "CLAUDE.md 아키텍처(주행 진입 시 화면 닫기), 상태 머신 T9")
def warning_then_drive(step):
    """경고 화면 → 출발."""
    app = APP()
    step.situation("구역 안 경고 화면")
    _warned_in_zone(app)
    step.action("D·30km/h")
    app.drive_off(30)
    step.result("주행 화면")
    step.check(app.current() == "DRIVING", "경고 화면이 닫힌다")


@scenario("L8", "linked", "경고 구역에서 반납한 뒤 같은 자리에서 다음 손님이 점검을 마쳐도 경고하지 않는다",
          "11/14 주정차 경고 반복 수정(a48807d) 회귀 확인, 상태 머신 가드 해석 보완")
def next_customer_same_spot(step):
    """경고 → 반납 → 다음 손님 점검 마침."""
    app = APP()
    step.situation("구역 안 경고 → 확인 → 반납")
    _warned_in_zone(app)
    app.screen.tap("parking_warning_acknowledge")
    app.return_car()
    before = len(app.notifications("parking_warning"))
    step.action("비움 → 앉음 → English → 점검 마침")
    app.console.seat(False)
    app.sit_and_choose("en")
    app.device.logcat_clear()
    app.complete_precheck()
    time.sleep(4)
    step.result("경고 없음")
    step.check(app.current() == "LEARNING_START", f"교육 시작 제안(지금: {app.current()})")
    step.equal(len(app.notifications("parking_warning")), before, "새 경고 알림이 없다")
    step.check(not spoken(app.speech()), "경고를 말하지 않는다")


@scenario("L9", "linked", "통화 중 주행하면 규칙 안내를 알림으로만 띄우고, 통화가 끝난 뒤 안내는 다시 말한다",
          "11/14 사용자 결정 (가), 11/15 에뮬레이터 확인(통화가 남은 채 주행하면 안내가 알림으로만), 기대 목록 route_en.json")
def call_while_driving(step):
    """경로 앞부분: 카메라 두 곳은 통화 중, 보호구역은 통화 뒤."""
    app = APP()
    expected = load_expectation(ROUTE_EN)
    replay = Replay(app.device, expected.route, expected.kmh, until_km=1.75)
    step.situation("영어 대여, 경로 시작점, 통화 중")
    app.rented(point=replay.start_point, vendor_code=False)
    app.device.call_start()
    app.device.logcat_clear()
    try:
        step.action("출발 → 경로 앞부분 재생(1.0km까지 통화 중)")
        app.drive_off(expected.kmh)
        replay.start()
        replay.wait_until_km(1.0, timeout=replay.duration_s + 60)
        during_call = spoken(app.speech())
        notes = [n.text for n in app.notifications("road_rule")]
    except Exception:
        replay.stop()   # 실패하면 위치 재생을 멈춰 다음 시나리오에 위치가 섞이지 않게 한다
        raise
    finally:
        app.device.call_end()
    step.action("통화 종료 → 1.75km까지 재생")
    replay.wait()
    step.result("통화 중엔 알림만, 통화 뒤엔 말한다")
    step.check(not during_call, f"통화 중에는 말하지 않는다: {[e.text for e in during_call]}")
    step.equal(notes[:2], expected.joined()[:2], "통화 중 카메라 두 곳은 주행 안내 알림")
    after = [e.text for e in spoken(app.speech())]
    step.check(expected.joined()[2] in after, f"통화 뒤 보호구역 안내를 말한다: {after}")
    app.stop_and_park()
