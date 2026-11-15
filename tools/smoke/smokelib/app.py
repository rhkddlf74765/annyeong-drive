"""앱을 시나리오가 원하는 상태로 가장 짧은 길로 데려가는 조작 모음과, 지금 어떤 화면인지 읽는 일."""
import re
import time

from .console import Console
from .device import Device
from .logs import Notification, SpeechEvent, parse_speech
from .screen import Screen, Strings

ADMIN_CODE = "Demo2026"
PARKING_ZONE_POINT = (37.6251787, 127.0330457)   # PC5 주정차 단속 카메라(11/14·11/15 경고 확인 지점)
OUTSIDE_ZONE_POINT = (37.6420, 127.0480)         # 주정차 카메라에서 1km 넘게 떨어진 곳

# 화면마다 그 화면에만 보이는 문구 ID(AppScreen과 같은 이름). 위에서부터 먼저 찾는다.
SCREEN_MARKERS = (
    ("PARKING_WARNING", "parking_warning_acknowledge"),
    ("RESUME_OFFER", "resume_offer_title"),
    ("LEARNING_START", "learning_start_title"),
    ("LEARNING", "learn_action_close"),
    ("RETURN", "return_title"),
    ("PARKED_HOME", "parked_home_title"),       # 정차 대기의 "업체 설정" 버튼이 업체 설정 제목과 같은 글자라 먼저 본다
    ("VENDOR_SETTINGS", "vendor_title"),
    ("PRECHECK", "precheck_title"),
    ("LANGUAGE", "setup_language_title"),
    ("DRIVING", "driving_home_title"),
    ("WAITING", "home_idle"),
    ("LOCATION_NEEDED", "home_location_needed"),
    ("PREPARING", "home_preparing"),
)
PARKED_SCREENS = ("PARKED_HOME", "LEARNING_START", "RESUME_OFFER")
LANGUAGE_BUTTONS = {"en": "language_english", "ko": "language_korean"}


class App:
    """에뮬레이터의 release 앱 하나를 조작한다."""

    def __init__(self, device: Device, console: Console, strings: Strings):
        self.device = device
        self.console = console
        self.strings = strings
        self.screen = Screen(device, strings)

    # ── 읽기 ─────────────────────────────────────────────

    def current(self) -> str:
        """지금 화면 이름(AppScreen과 같은 이름). 모르면 UNKNOWN."""
        nodes = self.screen.nodes()
        for name, string_id in SCREEN_MARKERS:
            if self.screen.find(string_id, nodes):
                return name
        return "UNKNOWN"

    def wait_screen(self, name: str, timeout: float = 20) -> bool:
        """그 화면이 될 때까지 기다린다."""
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if self.current() == name:
                return True
            time.sleep(0.8)
        return False

    def wait_parked(self, timeout: float = 15) -> str:
        """정차 화면(정차 대기·교육 시작 제안·이어서 확인할까요) 중 하나가 될 때까지 기다린다. 그 화면 이름(아니면 지금 화면)."""
        deadline = time.monotonic() + timeout
        screen = self.current()
        while time.monotonic() < deadline:
            screen = self.current()
            if screen in PARKED_SCREENS:
                return screen
            time.sleep(0.8)
        return screen

    def speech(self) -> list[SpeechEvent]:
        """지금까지의 음성 로그."""
        return parse_speech(self.device.logcat("AnnyeongTts"))

    def notifications(self, channel: str | None = None) -> list[Notification]:
        """앱 알림. 채널을 주면 그 채널만."""
        found = self.device.notifications()
        return [n for n in found if channel is None or n.channel == channel]

    def question_number(self) -> int | None:
        """교육 문제 화면의 문제 번호(문제 N / M의 N). 문제 화면이 아니면 None."""
        node = self.screen.find("learn_question_position")
        if node is None:
            return None
        numbers = re.findall(r"\d+", node.label)
        return int(numbers[0]) if numbers else None

    def card_position(self) -> int | None:
        """교육 카드 화면의 카드 번호. 카드 화면이 아니면 None."""
        node = self.screen.find("learn_card_position")
        if node is None:
            return None
        numbers = re.findall(r"\d+", node.label)
        return int(numbers[0]) if numbers else None

    # ── 준비 ─────────────────────────────────────────────

    def reset(self, point: tuple[float, float] = OUTSIDE_ZONE_POINT) -> None:
        """앱 데이터를 비우고 시동 직후(P·0·운전석 비움)로 앱을 연다. 대기 화면까지 기다린다."""
        self.console.seat(False)
        if self.console.speed_kph() > 0:
            self.console.stop_vehicle()
        self.console.gear("P")
        self.device.force_stop()
        self.device.clear_data()
        self.device.grant_permissions()
        self.device.geo_fix(*point)
        self.device.logcat_clear()
        self.device.start_app()
        if not self.wait_screen("WAITING", 40):
            raise AssertionError(f"앱을 연 뒤 대기 화면이 아님: {self.current()}")

    def sit_and_choose(self, language: str = "en") -> None:
        """운전석에 앉아 언어를 고르고 출발 전 점검 화면까지 간다."""
        self.console.seat(True)
        if not self.wait_screen("LANGUAGE", 20):
            raise AssertionError(f"앉은 뒤 언어 선택이 아님: {self.current()}")
        self.screen.tap(LANGUAGE_BUTTONS[language])
        if not self.wait_screen("PRECHECK", 20):
            raise AssertionError(f"언어를 고른 뒤 점검 화면이 아님: {self.current()}")

    def complete_precheck(self) -> None:
        """안전띠를 확인하고 점검을 마쳐 교육 시작 제안까지 간다."""
        self.screen.tap("precheck_seatbelt_confirm")
        self.screen.tap("precheck_done")
        if not self.wait_screen("LEARNING_START", 20):
            raise AssertionError(f"점검을 마친 뒤 교육 시작 제안이 아님: {self.current()}")

    def later(self) -> None:
        """교육을 나중에 보기로 하고 정차 대기로 간다."""
        self.screen.tap("action_later")
        if not self.wait_screen("PARKED_HOME", 20):
            raise AssertionError(f"나중에 보기 뒤 정차 대기가 아님: {self.current()}")

    def set_vendor_code(self, code: str = ADMIN_CODE) -> None:
        """정차 대기에서 업체 설정을 열어 첫 관리자 코드를 정하고 돌아온다."""
        self.screen.tap("action_vendor_settings")
        if self.screen.wait_any(["vendor_first_code"], 15) is None:
            raise AssertionError(f"업체 설정 첫 코드 화면이 아님: {self.screen.labels()[:10]}")
        self.screen.type_into("new_code_label", code)
        self.screen.type_into("confirm_code_label", code)
        self.screen.tap("action_confirm")
        if self.screen.wait_any(["action_save"], 15):
            self.screen.tap("action_save")
        self.screen.tap("action_close")
        if not self.wait_screen("PARKED_HOME", 20):
            raise AssertionError(f"업체 설정을 닫은 뒤 정차 대기가 아님: {self.current()}")

    def rented(self, language: str = "en", vendor_code: bool = True,
               point: tuple[float, float] = OUTSIDE_ZONE_POINT) -> None:
        """대여를 시작해 정차 대기까지 간다(앱 비움 → 앉기 → 언어 → 점검 → 나중에 → 업체 코드)."""
        self.reset(point)
        self.sit_and_choose(language)
        self.complete_precheck()
        self.later()
        if vendor_code:
            self.set_vendor_code()

    # ── 주행 ─────────────────────────────────────────────

    def drive_off(self, kmh: float = 30) -> None:
        """D로 출발한다. 주행 화면이 될 때까지 기다린다."""
        self.console.drive(kmh)
        if not self.wait_screen("DRIVING", 20):
            raise AssertionError(f"출발한 뒤 주행 화면이 아님: {self.current()}")

    def stop_and_park(self) -> None:
        """멈춘 것을 확인하고 P를 넣는다."""
        self.console.park()
        time.sleep(2.5)

    # ── 교육 ─────────────────────────────────────────────

    def open_learning(self) -> None:
        """정차 대기에서 교육을 열고 교육 시작 제안이면 시작해 카드 화면까지 간다."""
        self.screen.tap("action_learning_open")
        if self.wait_screen("LEARNING_START", 10):
            self.screen.tap("action_learning_begin")
        if not self.wait_screen("LEARNING", 15):
            raise AssertionError(f"교육을 연 뒤 교육 화면이 아님: {self.current()}")

    def go_to_quiz(self) -> None:
        """카드를 끝까지 넘기고 핵심 퀴즈를 시작한다."""
        for _ in range(12):
            if self.screen.has("learn_action_start_quiz"):
                self.screen.tap("learn_action_start_quiz")
                break
            self.screen.tap("learn_action_next")
        if self.question_number() is None:
            raise AssertionError(f"퀴즈를 시작한 뒤 문제 화면이 아님: {self.screen.labels()[:10]}")

    def choose_first_option(self) -> None:
        """지금 문제의 보기 중 하나를 고른다(정답 여부와 상관없이)."""
        option_ids = [k for k in self.strings.by_language["values"] if re.fullmatch(r"learn_q\d+_o\d+", k)]
        for node in self.screen.nodes():
            if any(self.strings.matches(option_id, node.label) for option_id in option_ids if node.label):
                self.device.tap(*node.center)
                time.sleep(1.0)
                return
        raise AssertionError("문제 보기를 찾지 못함")

    def answer_current(self) -> None:
        """보기를 고르고 정답을 확인해 해설로 간 뒤 다음 문제로 넘어간다."""
        self.choose_first_option()
        self.screen.tap("learn_action_check")
        if self.screen.wait_any(["learn_explain_answer"], 10) is None:
            raise AssertionError("정답 확인 뒤 해설이 보이지 않음")
        self.screen.tap("learn_action_next_question")

    # ── 반납 ─────────────────────────────────────────────

    def to_parked_home(self) -> None:
        """정차 중 제안 화면(이어서 확인할까요·교육 시작 제안)이면 나중에 보기로 정차 대기까지 간다."""
        screen = self.current()
        if screen in ("RESUME_OFFER", "LEARNING_START"):
            self.screen.tap("action_later")
        if not self.wait_screen("PARKED_HOME", 15):
            raise AssertionError(f"정차 대기로 가지 못함: {self.current()}")

    def enter_code(self, code: str) -> None:
        """관리자 코드 칸을 비우고 코드를 넣은 뒤 확인을 누른다."""
        self.screen.tap("admin_code_label")
        for _ in range(12):
            self.device.shell("input keyevent KEYCODE_DEL")
        self.device.type_text(code)
        self.screen.tap("action_confirm")

    def return_car(self, code: str = ADMIN_CODE) -> None:
        """정차 대기에서 반납을 열고 관리자 코드로 반납을 마친다. 대기 화면까지 기다린다."""
        self.to_parked_home()
        self.screen.tap("action_return")
        if not self.wait_screen("RETURN", 15):
            raise AssertionError(f"반납 화면이 아님: {self.current()}")
        self.enter_code(code)
        if not self.wait_screen("WAITING", 20):
            raise AssertionError(f"반납 뒤 대기 화면이 아님: {self.current()}")
