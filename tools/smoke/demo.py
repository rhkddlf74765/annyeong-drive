#!/usr/bin/env python3
"""시연 영상용 실행: 판정 없이 대여 → 점검 → 교육·퀴즈 → 주행(GPX 음성 안내) → 정차 경고 → 이어 보기 → 반납을 한 흐름으로 진행한다.

녹화하는 사람은 에뮬레이터 창만 찍으면 된다. 터미널에는 장면마다 [경과 시간] 장면 이름이 찍혀 편집할 때 자를 위치를 찾기 쉽다.
입력은 Pleos 가상 콘솔(기어·차속·착좌)과 GPX 재생(위치)이다. 영상 자막에 이 입력 방법을 함께 적는다.

예)
  python tools/smoke/demo.py --apk app/build/outputs/apk/release/annyeong-drive-1.0.apk   # 준비 → Enter → 시연
  python tools/smoke/demo.py --skip-install --map ai.pleos.xxx/.MainActivity           # 주행 중 지도 화면을 앞에 띄움
"""
import argparse
import sys
import threading
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from smokelib import paths  # noqa: E402
from smokelib.app import App  # noqa: E402
from smokelib.console import Console  # noqa: E402
from smokelib.device import Device  # noqa: E402
from smokelib.logs import parse_speech  # noqa: E402
from smokelib.route import Replay, load_expectation  # noqa: E402
from smokelib.screen import Strings  # noqa: E402

PAUSE_SHORT = 2.5
PAUSE = 4.0
PAUSE_LONG = 6.0


class Clock:
    """녹화 시작부터의 경과 시간을 찍는다."""

    def __init__(self):
        self.start = time.monotonic()

    def scene(self, text: str) -> None:
        """장면 이름을 경과 시간과 함께 찍는다."""
        elapsed = int(time.monotonic() - self.start)
        print(f"[{elapsed // 60:02d}:{elapsed % 60:02d}] {text}", flush=True)


def main() -> int:
    """준비를 마치고 Enter를 기다린 뒤 시연 흐름을 진행한다."""
    args = _arguments()
    device = Device()
    if args.apk:
        print("최종 APK 설치 중…", flush=True)
        device.install(args.apk)
    console = Console(device, client="demo")
    console.start()
    app = App(device, console, Strings(paths.RES), record_notifications=False)
    expected = load_expectation("route_en.json")
    replay = Replay(device, expected.route, expected.kmh)
    try:
        _prepare(app, replay)
        if not args.no_wait:
            input("\n준비 완료. 녹화를 시작한 뒤 Enter를 누르세요. ")
        _demo(app, replay, args.map, not args.app_during_drive)
        print("\n시연 끝. 녹화를 멈추세요.", flush=True)
    finally:
        console.stop()
    return 0


def _arguments() -> argparse.Namespace:
    """명령 인자."""
    parser = argparse.ArgumentParser(description="Annyeong Drive 시연 영상용 실행")
    parser.add_argument("--apk", type=Path, help="설치할 서명된 APK")
    parser.add_argument("--skip-install", action="store_true", help="설치하지 않음")
    parser.add_argument("--map", help="주행 중 앞에 띄울 화면(패키지/액티비티). 없으면 홈 화면(차속·기어 표시)")
    parser.add_argument("--app-during-drive", action="store_true", help="주행 중에도 앱 화면을 둔다(시스템이 가림)")
    parser.add_argument("--no-wait", action="store_true", help="Enter를 기다리지 않음(리허설)")
    return parser.parse_args()


def _prepare(app: App, replay: Replay) -> None:
    """녹화 전 준비: 앱 데이터를 비우고 업체 관리자 코드를 정한 뒤 반납해, 경로 시작점의 대기 화면으로 둔다."""
    print("준비 중(녹화 전): 업체 코드 설정 → 반납 → 경로 시작점 대기 화면", flush=True)
    app.rented(point=replay.start_point)
    app.return_car()
    app.console.seat(False)
    app.device.geo_fix(*replay.start_point)
    app.wait_screen("WAITING", 20)


def _demo(app: App, replay: Replay, map_activity: str | None, leave_app: bool) -> None:
    """녹화할 시연 흐름."""
    clock = Clock()
    clock.scene("1. 대기 화면(영어·한국어 함께)")
    time.sleep(PAUSE)

    clock.scene("2. 운전석 착좌 [입력: 가상 콘솔] → 언어 선택")
    app.console.seat(True)
    app.wait_screen("LANGUAGE", 20)
    time.sleep(PAUSE_SHORT)
    clock.scene("3. English 선택 → 출발 전 점검")
    app.screen.tap("language_english")
    app.wait_screen("PRECHECK", 20)
    time.sleep(PAUSE)
    app.screen.tap("precheck_seatbelt_confirm")
    time.sleep(PAUSE_SHORT)
    app.screen.tap("precheck_done")
    app.wait_screen("LEARNING_START", 20)
    time.sleep(PAUSE_SHORT)

    clock.scene("4. 교육 시작 → 카드")
    app.screen.tap("action_learning_begin")
    app.wait_screen("LEARNING", 15)
    time.sleep(PAUSE)
    app.screen.tap("learn_action_next")
    time.sleep(PAUSE_SHORT)
    clock.scene("5. 핵심 퀴즈 → 답 고르기 → 정답 확인 → 해설")
    app.go_to_quiz()
    time.sleep(PAUSE_SHORT)
    app.choose_first_option()
    time.sleep(PAUSE_SHORT)
    app.screen.tap("learn_action_check")
    time.sleep(PAUSE_LONG)
    app.screen.tap("learn_action_next_question")
    time.sleep(PAUSE_SHORT)

    clock.scene("6. 출발 [입력: 가상 콘솔 D·50km/h] → 교육 화면 닫힘")
    app.device.logcat_clear()
    app.console.drive(replay.speed_mps * 3.6)
    app.wait_screen("DRIVING", 20)
    time.sleep(PAUSE)
    if map_activity:
        clock.scene("   지도 화면 띄움")
        app.device.shell(f"am start --user {app.device.user} -n {map_activity}", check=False)
        time.sleep(PAUSE_SHORT)
    elif leave_app:
        clock.scene("   홈 화면(차속·기어 표시) — 앱은 뒤에서 안내")
        app.device.shell("input keyevent KEYCODE_HOME", check=False)
        time.sleep(PAUSE_SHORT)

    clock.scene(f"7. 시연 경로 주행 [입력: GPX 재생, 약 {replay.duration_s / 60:.0f}분] — 안내가 나오면 아래에 찍힌다")
    watcher = _SpeechWatcher(app, clock)
    watcher.start()
    replay.start()
    replay.wait()

    clock.scene("8. 경로 끝 정차 [입력: 가상 콘솔 0km/h → P] → 주정차 확인 안내")
    app.stop_and_park()
    if map_activity or leave_app:
        app.device.start_app()
    app.wait_screen("PARKING_WARNING", 20)
    time.sleep(PAUSE_LONG + 2)
    watcher.stop()
    app.screen.tap("parking_warning_acknowledge")
    time.sleep(PAUSE_SHORT)

    clock.scene("9. 이어서 확인할까요 → 이어 보기(멈춘 문제로)")
    if app.wait_screen("RESUME_OFFER", 10):
        time.sleep(PAUSE_SHORT)
        app.screen.tap("action_resume")
        time.sleep(PAUSE)
        app.screen.tap("learn_action_close")
        time.sleep(PAUSE_SHORT)

    clock.scene("10. 반납(관리자 코드) → 운전자 기록 삭제 → 대기 화면")
    app.return_car()
    time.sleep(PAUSE)
    clock.scene("11. 운전석 비움 [입력: 가상 콘솔] → 다음 손님 대기")
    app.console.seat(False)
    time.sleep(PAUSE)


class _SpeechWatcher:
    """주행 중 안내 음성이 나오면 경과 시간과 문장을 찍는다(편집할 때 원래 속도로 둘 구간)."""

    def __init__(self, app: App, clock: Clock):
        self.app = app
        self.clock = clock
        self._seen: set[tuple[float, str]] = set()
        self._running = False

    def start(self) -> None:
        """다른 스레드에서 지켜보기 시작한다."""
        self._running = True
        threading.Thread(target=self._run, daemon=True).start()

    def stop(self) -> None:
        """지켜보기를 멈춘다."""
        self._running = False

    def _run(self) -> None:
        """5초마다 새 음성 로그를 찍는다(같은 로그는 한 번만)."""
        while self._running:
            try:
                speaks = [e for e in parse_speech(self.app.device.logcat("AnnyeongTts")) if e.kind == "speak"]
            except Exception:  # noqa: BLE001 - 로그를 못 읽어도 시연은 이어 간다
                speaks = []
            for event in speaks:
                key = (event.time, event.text)
                if key not in self._seen:
                    self._seen.add(key)
                    self.clock.scene(f"   ♪ 안내 음성: {event.text}")
            time.sleep(5)


if __name__ == "__main__":
    sys.exit(main())
