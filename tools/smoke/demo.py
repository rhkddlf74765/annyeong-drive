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
import functools
import os
import http.server
import json
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
LIVE_PORT = 18090
LIVE_DIR = paths.OUT / "demo"


class Live:
    """브라우저 경로 화면(live.html)에 보여 줄 상태: 경로 위치(km), 지금 장면, 방금 나온 안내 문장. 0.5초마다 state.json으로 쓴다."""

    def __init__(self, replay):
        self.replay = replay
        self.scene = "대기 중"
        self.voice = ""

    def start(self) -> None:
        """경로 화면 파일을 만들고, 웹 서버와 상태 쓰기를 시작한다."""
        import route_map
        LIVE_DIR.mkdir(parents=True, exist_ok=True)
        (LIVE_DIR / "live.html").write_text(route_map.live_page(), encoding="utf-8")
        handler = functools.partial(_QuietHandler, directory=str(LIVE_DIR))
        server = http.server.ThreadingHTTPServer(("127.0.0.1", LIVE_PORT), handler)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        threading.Thread(target=self._write_loop, daemon=True).start()
        print(f"경로 화면: 브라우저에서 http://127.0.0.1:{LIVE_PORT}/live.html 를 여세요", flush=True)

    def _write_loop(self) -> None:
        """0.5초마다 상태를 파일로 쓴다."""
        while True:
            km = self.replay.sent[-1][1] if self.replay.sent else 0.0
            state = {"km": km, "scene": self.scene, "voice": self.voice}
            try:
                temp = LIVE_DIR / "state.tmp"
                temp.write_text(json.dumps(state, ensure_ascii=False), encoding="utf-8")
                os.replace(temp, LIVE_DIR / "state.json")
            except OSError:
                pass   # 웹 서버가 파일을 읽는 중이면(Windows) 다음 차례에 다시 쓴다. 여기서 멈추면 위치가 움직이지 않는다
            time.sleep(0.5)


class _QuietHandler(http.server.SimpleHTTPRequestHandler):
    """요청 기록을 터미널에 찍지 않는 웹 서버."""

    def log_message(self, *args) -> None:
        """기록하지 않는다."""


LIVE = None


class Clock:
    """녹화 시작부터의 경과 시간을 찍는다."""

    def __init__(self):
        self.start = time.monotonic()

    def scene(self, text: str) -> None:
        """장면 이름을 경과 시간과 함께 찍고 경로 화면에도 보낸다."""
        elapsed = int(time.monotonic() - self.start)
        print(f"[{elapsed // 60:02d}:{elapsed % 60:02d}] {text}", flush=True)
        if LIVE is not None and not text.startswith("   ♪"):
            LIVE.scene = text.strip()


def main() -> int:
    """준비를 마치고 Enter를 기다린 뒤 시연 흐름을 진행한다."""
    args = _arguments()
    device = Device()
    import run_smoke  # 에뮬레이터 켜기·준비 대기를 스모크와 같은 방법으로
    if run_smoke._ensure_emulator(device):
        print("에뮬레이터를 켰습니다. 부팅을 기다립니다(2~4분)…", flush=True)
    run_smoke._wait_ready(device)
    if args.apk:
        print("최종 APK 설치 중…", flush=True)
        device.install(args.apk)
    console = Console(device, client="demo")
    console.start()
    app = App(device, console, Strings(paths.RES), record_notifications=False)
    expected = load_expectation("route_en.json")
    replay = Replay(device, expected.route, expected.kmh)
    global LIVE
    LIVE = Live(replay)
    LIVE.start()
    try:
        if not args.no_wait:
            print("\n" + "=" * 60, flush=True)
            print(" ① 브라우저에서 위 경로 화면 주소를 열고, 에뮬레이터(왼쪽)·브라우저(오른쪽)를 나란히 놓으세요.", flush=True)
            print("    다 되면 Enter → 녹화 전 준비를 합니다(1~2분, 화면이 자동으로 움직입니다. 아직 녹화하지 마세요).", flush=True)
            print("=" * 60, flush=True)
            input()
        if not args.skip_prepare:
            _prepare(app, replay)
        if not args.no_wait:
            print("\n" + "=" * 60, flush=True)
            print(" ② 준비 완료. 이제 녹화를 시작한 뒤(Zoom: 화면 전체 공유 + 소리 공유 → 기록) Enter를 누르세요.", flush=True)
            print("=" * 60, flush=True)
        if args.manual:
            device.shell("settings put system show_touches 1", check=False)
            _manual(app, replay)
            print("\n시연 끝. 녹화를 멈추세요.", flush=True)
            return 0
        if not args.no_wait:
            input()
        _clear_terminal()
        _demo(app, replay, args.map, not args.app_during_drive)
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
    parser.add_argument("--manual", action="store_true", help="화면은 사람이 직접 누르고, 스크립트는 차량 신호만 넣는다(Enter로 단계 진행)")
    parser.add_argument("--skip-prepare", action="store_true", help="준비(업체 코드·반납)를 건너뜀(이미 대기 화면일 때)")
    return parser.parse_args()


def _prepare(app: App, replay: Replay) -> None:
    """녹화 전 준비: 앱 데이터를 비우고 업체 관리자 코드를 정한 뒤 반납해, 경로 시작점의 대기 화면으로 둔다."""
    print("준비 중: 업체 관리자 코드 설정 → 대기 화면", flush=True)
    app.rented(point=replay.start_point)
    app.return_car()
    app.console.seat(False)
    app.device.geo_fix(*replay.start_point)
    app.wait_screen("WAITING", 20)


def _demo(app: App, replay: Replay, map_activity: str | None, leave_app: bool) -> None:
    """녹화할 시연 흐름."""
    clock = Clock()
    clock.scene("1. 대기 화면 — 영어·한국어 함께 표시")
    time.sleep(PAUSE)

    clock.scene("2. 운전석 착좌(가상 콘솔) → 언어 선택")
    app.console.seat(True)
    app.wait_screen("LANGUAGE", 20)
    time.sleep(PAUSE_SHORT)
    clock.scene("3. English 선택 → 출발 전 점검(안전띠 직접 확인 → 점검 마침)")
    app.screen.tap("language_english")
    app.wait_screen("PRECHECK", 20)
    time.sleep(PAUSE)
    app.screen.tap("precheck_seatbelt_confirm")
    time.sleep(PAUSE_SHORT)
    app.screen.tap("precheck_done")
    app.wait_screen("LEARNING_START", 20)
    time.sleep(PAUSE_SHORT)

    clock.scene("4. 교육 시작 → 규칙 카드")
    app.screen.tap("action_learning_begin")
    app.wait_screen("LEARNING", 15)
    time.sleep(PAUSE)
    app.screen.tap("learn_action_next")
    time.sleep(PAUSE_SHORT)
    clock.scene("5. 핵심 퀴즈 → 답 선택 → 정답 확인 → 해설")
    app.go_to_quiz()
    time.sleep(PAUSE_SHORT)
    app.choose_first_option()
    time.sleep(PAUSE_SHORT)
    app.screen.tap("learn_action_check")
    time.sleep(PAUSE_LONG)
    app.screen.tap("learn_action_next_question")
    time.sleep(PAUSE_SHORT)

    clock.scene("6. 출발: 기어 D·50km/h(가상 콘솔) → 교육 화면 자동 닫힘")
    app.device.logcat_clear()
    app.console.drive(replay.speed_mps * 3.6)
    app.wait_screen("DRIVING", 20)
    time.sleep(PAUSE)
    if map_activity:
        clock.scene("   지도 화면")
        app.device.shell(f"am start --user {app.device.user} -n {map_activity}", check=False)
        time.sleep(PAUSE_SHORT)
    elif leave_app:
        clock.scene("   주행 중: 차속·기어 표시, 앱 화면은 시스템이 가리고 음성으로 안내")
        app.device.shell("input keyevent KEYCODE_HOME", check=False)
        time.sleep(PAUSE_SHORT)

    clock.scene("7. 시연 경로 주행(GPX 재생, 3.8km)")
    watcher = _SpeechWatcher(app, clock)
    watcher.start()
    replay.start()
    replay.wait()

    clock.scene("8. 경로 끝 정차: 0km/h → P(가상 콘솔) → 주정차 확인 안내 → 확인했어요")
    app.stop_and_park()
    if map_activity or leave_app:
        app.device.start_app()
    app.wait_screen("PARKING_WARNING", 20)
    time.sleep(PAUSE_LONG + 2)
    watcher.stop()
    app.screen.tap("parking_warning_acknowledge")
    time.sleep(PAUSE_SHORT)

    clock.scene("9. 이어서 확인할까요 → 이어 보기(멈춘 문제로 복귀)")
    if app.wait_screen("RESUME_OFFER", 10):
        time.sleep(PAUSE_SHORT)
        app.screen.tap("action_resume")
        time.sleep(PAUSE)
        app.screen.tap("learn_action_close")
        time.sleep(PAUSE_SHORT)

    clock.scene("10. 반납: 관리자 코드 입력 → 운전자 기록 삭제")
    app.return_car()
    time.sleep(PAUSE)
    clock.scene("11. 운전석 비움(가상 콘솔) → 다음 손님 대기")
    app.console.seat(False)
    time.sleep(PAUSE)
    clock.scene("시연 종료")


def _clear_terminal() -> None:
    """준비 안내를 지우고 시연 제목만 남긴다(터미널도 화면 공유에 들어간다)."""
    os.system("cls" if os.name == "nt" else "clear")
    print("Annyeong Drive 시연", flush=True)
    print("입력: 착좌·기어·차속 = Pleos 가상 콘솔 · 위치 = GPX 재생 · 음성 = Pleos TTS\n", flush=True)


def _manual(app: App, replay: Replay) -> None:
    """사람이 화면을 누르는 시연. 스크립트는 Enter를 받을 때마다 차량 신호(착좌·기어·차속·GPX)만 넣는다."""
    print("\n[직접 조작 모드] 화면은 마우스로 직접 누르세요. 차량 신호는 Enter를 누르면 넣습니다.", flush=True)
    input("\n① 녹화를 시작한 뒤 Enter → 운전석에 앉습니다(가상 콘솔). ")
    clock = Clock()
    clock.scene("운전석 착좌 [입력: 가상 콘솔]")
    app.console.seat(True)
    print("\n   지금 직접 하세요: English → 안전띠 확인 체크 → 점검 마침 → 교육 시작 → 카드 넘기기 → 핵심 퀴즈 → 답 고르기 → 정답 확인 → 다음", flush=True)
    input("\n② 퀴즈 문제 화면에서 Enter → 출발합니다(D·50km/h) + 시연 경로 주행(약 4분 30초, 자동). ")
    clock.scene("출발 [입력: 가상 콘솔 D·50km/h]")
    app.device.logcat_clear()
    app.console.drive(replay.speed_mps * 3.6)
    time.sleep(PAUSE)
    clock.scene(f"시연 경로 주행 [입력: GPX 재생, 약 {replay.duration_s / 60:.0f}분] — 손대지 마세요")
    watcher = _SpeechWatcher(app, clock)
    watcher.start()
    replay.start()
    replay.wait()
    clock.scene("경로 끝 정차 [입력: 가상 콘솔 0km/h → P] → 주정차 확인 안내")
    app.stop_and_park()
    time.sleep(PAUSE_LONG)
    watcher.stop()
    print("\n   지금 직접 하세요: 확인했어요 → (이어서 확인할까요) 이어 보기 → 교육 닫기 → 반납 완료 → 관리자 코드 Demo2026 → 확인", flush=True)
    input("\n③ 대기 화면이 되면 Enter → 운전석을 비웁니다(다음 손님 대기). ")
    clock.scene("운전석 비움 [입력: 가상 콘솔]")
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
                    if LIVE is not None:
                        LIVE.voice = event.text
            time.sleep(2)


if __name__ == "__main__":
    sys.exit(main())
