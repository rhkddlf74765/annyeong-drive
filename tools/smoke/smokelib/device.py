"""adb로 에뮬레이터를 다룬다: 명령, 앱 설치·초기화·실행, 로그, 화면 저장, 통화·위치, 오디오 모드, 알림 목록."""
import subprocess
import time
from pathlib import Path

from . import paths
from .logs import Notification, parse_notifications

PERMISSIONS = (
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.POST_NOTIFICATIONS",
    "pleos.car.permission.FUSED_LOCATION",
    "pleos.car.permission.TTS_SERVICE",
    "android.car.permission.CAR_SPEED",
)
CALL_NUMBER = "01012345678"
ADB_RETRIES = 4
_SERVER_RESTART = ("daemon not running", "cannot connect to daemon", "failed to start daemon", "device offline",
                   "could not connect to TCP port")  # 마지막은 에뮬레이터 콘솔 포트가 잠깐 막힌 경우(geo·gsm, 11/15 확인)


class Device:
    """에뮬레이터 한 대. adb 명령을 감싼다."""

    def __init__(self, serial: str | None = None):
        self.serial = serial
        self._user: int | None = None

    def adb(self, *args: str, timeout: float = 60, check: bool = True) -> str:
        """adb 명령을 실행하고 표준 출력을 돌려준다. adb 서버가 다시 뜨는 중이면(Android Studio 등) 잠시 뒤 다시 한다."""
        command = [paths.adb()]
        if self.serial:
            command += ["-s", self.serial]
        command += list(args)
        for attempt in range(ADB_RETRIES):
            done = subprocess.run(command, capture_output=True, timeout=timeout)
            out = done.stdout.decode("utf-8", errors="replace")
            err = done.stderr.decode("utf-8", errors="replace")
            if not any(sign in err + out for sign in _SERVER_RESTART) or attempt == ADB_RETRIES - 1:
                break
            time.sleep(3)
            subprocess.run([paths.adb(), "start-server"], capture_output=True, timeout=30)
            self.on_server_restart()
        if check and done.returncode != 0:
            raise RuntimeError(f"adb {' '.join(args)} 실패: {err.strip() or out.strip()}")
        return out

    def on_server_restart(self) -> None:
        """adb 서버가 다시 뜬 뒤 할 일(포트 연결 다시 하기). 가상 콘솔이 바꿔 끼운다."""

    def shell(self, command: str, timeout: float = 60, check: bool = True) -> str:
        """기기 셸 명령을 실행한다."""
        return self.adb("shell", command, timeout=timeout, check=check)

    def emu(self, *args: str) -> str:
        """에뮬레이터 콘솔 명령(geo·gsm 등)을 실행한다."""
        return self.adb("emu", *args, timeout=20)

    @property
    def user(self) -> int:
        """지금 사용자 번호(Pleos는 10)."""
        if self._user is None:
            self._user = int(self.shell("am get-current-user").strip())
        return self._user

    def install(self, apk: Path) -> None:
        """APK를 설치(덮어쓰기)한다. 서명 키가 다른 앱이 깔려 있으면(디버그 키 ↔ 정식 키) 지우고 다시 설치한다."""
        out = self._install_output("-r", str(apk))
        if "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in out or "signatures do not match" in out:
            self.adb("uninstall", paths.PACKAGE, check=False)
            out = self._install_output(str(apk))
        if "Success" not in out:
            raise RuntimeError(f"설치 실패: {out.strip()[-300:]}")

    def _install_output(self, *args: str) -> str:
        """adb install을 실행하고 표준 출력과 오류 출력을 합쳐 돌려준다(실패 이유는 오류 출력에 나온다)."""
        command = [paths.adb()] + (["-s", self.serial] if self.serial else []) + ["install", *args]
        done = subprocess.run(command, capture_output=True, timeout=300)
        return (done.stdout + done.stderr).decode("utf-8", errors="replace")

    def clear_data(self) -> None:
        """앱 데이터를 모두 지운다(대여·업체 설정 포함)."""
        self.shell(f"pm clear --user {self.user} {paths.PACKAGE}")

    def grant_permissions(self) -> None:
        """권한 대화상자 없이 시작하도록 필요한 권한을 미리 준다. 줄 수 없는 권한(설치 시 자동)은 넘어간다."""
        for permission in PERMISSIONS:
            self.shell(f"pm grant --user {self.user} {paths.PACKAGE} {permission}", check=False)

    def start_app(self) -> None:
        """앱 첫 화면을 연다."""
        self.shell(f"am start --user {self.user} -n {paths.PACKAGE}/.MainActivity")

    def force_stop(self) -> None:
        """앱을 강제로 끈다."""
        self.shell(f"am force-stop --user {self.user} {paths.PACKAGE}")

    def logcat_clear(self) -> None:
        """로그를 비운다."""
        self.adb("logcat", "-c", check=False)

    def logcat(self, *tags: str) -> str:
        """지금까지의 로그를 기기 시각(초)과 함께 읽는다. 태그를 주면 그 태그만."""
        args = ["logcat", "-d", "-v", "epoch"]
        if tags:
            args += ["-s", *tags]
        return self.adb(*args, timeout=60, check=False)

    def screenshot(self, path: Path) -> None:
        """지금 화면을 PNG로 저장한다."""
        command = [paths.adb()] + (["-s", self.serial] if self.serial else []) + ["exec-out", "screencap", "-p"]
        path.write_bytes(subprocess.run(command, capture_output=True, timeout=30).stdout)

    def ui_dump(self) -> str:
        """지금 화면 구조(uiautomator XML)를 읽는다."""
        self.shell("uiautomator dump /sdcard/smoke_ui.xml", timeout=30, check=False)
        return self.adb("exec-out", "cat", "/sdcard/smoke_ui.xml", timeout=30, check=False)

    def tap(self, x: int, y: int) -> None:
        """화면 한 점을 누른다."""
        self.shell(f"input tap {x} {y}")

    def type_text(self, text: str) -> None:
        """글자를 입력한다(영문·숫자)."""
        self.shell(f"input text {text}")

    def geo_fix(self, lat: float, lon: float) -> None:
        """GPS 위치를 정한다(geo fix는 경도, 위도 순서)."""
        self.emu("geo", "fix", f"{lon:.7f}", f"{lat:.7f}")

    def call_start(self) -> None:
        """전화가 걸려 오게 하고 받는다."""
        self.emu("gsm", "call", CALL_NUMBER)
        time.sleep(2)
        self.emu("gsm", "accept", CALL_NUMBER)
        self.wait_audio_mode("MODE_IN_CALL", 15)

    def call_end(self) -> None:
        """통화를 끊고 오디오 모드가 보통으로 돌아올 때까지 기다린다. 남아 있으면 다시 끊는다(11/15 확인)."""
        for _ in range(3):
            self.emu("gsm", "cancel", CALL_NUMBER)
            if self.wait_audio_mode("MODE_NORMAL", 10):
                return
        raise RuntimeError("통화가 끊기지 않음(오디오 모드가 MODE_NORMAL로 돌아오지 않음)")

    def audio_mode(self) -> str:
        """지금 오디오 모드(MODE_NORMAL, MODE_IN_CALL 등)."""
        for line in self.shell("dumpsys audio", check=False).splitlines():
            if "Actual mode =" in line:
                return line.split("=", 1)[1].strip()
        return "UNKNOWN"

    def wait_audio_mode(self, mode: str, timeout: float) -> bool:
        """오디오 모드가 그 값이 될 때까지 기다린다."""
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if self.audio_mode() == mode:
                return True
            time.sleep(1)
        return False

    def notifications(self) -> list[Notification]:
        """앱이 띄운 알림 목록."""
        return parse_notifications(self.shell("dumpsys notification --noredact", check=False), paths.PACKAGE)

    def clear_notifications(self) -> None:
        """알림을 모두 지운다(앱을 강제 종료하면 앱 알림도 사라진다)."""
        self.shell("service call notification 1", check=False)

    def boot_completed(self) -> bool:
        """부팅이 끝났는지."""
        return self.shell("getprop sys.boot_completed", check=False).strip() == "1"
