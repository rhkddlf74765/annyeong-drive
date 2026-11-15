"""저장소·Android SDK 도구의 위치를 찾는다."""
import os
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[3]
SMOKE = REPO / "tools" / "smoke"
RES = REPO / "app" / "src" / "main" / "res"
EXPECTED = SMOKE / "expected"
OUT = SMOKE / "out"
PACKAGE = "com.annyeongdrive.app"
AVD = "Pleos_Connect_v2.1.0"

_EXE = ".exe" if sys.platform.startswith("win") else ""
_BAT = ".bat" if sys.platform.startswith("win") else ""


def sdk_root() -> Path:
    """Android SDK 폴더를 찾는다. ANDROID_HOME → ANDROID_SDK_ROOT → 기본 설치 위치 순서다."""
    for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        value = os.environ.get(key)
        if value:
            return Path(value)
    local = os.environ.get("LOCALAPPDATA")
    if local:
        return Path(local) / "Android" / "Sdk"
    return Path.home() / "Android" / "Sdk"


def adb() -> str:
    """adb 실행 파일 경로."""
    return str(sdk_root() / "platform-tools" / f"adb{_EXE}")


def emulator() -> str:
    """에뮬레이터 실행 파일 경로."""
    return str(sdk_root() / "emulator" / f"emulator{_EXE}")


def apksigner() -> str:
    """가장 최신 build-tools의 apksigner 경로."""
    tools = sorted((sdk_root() / "build-tools").iterdir(), key=lambda p: [int(x) for x in p.name.split(".") if x.isdigit()])
    return str(tools[-1] / f"apksigner{_BAT}")


def gradlew() -> str:
    """저장소의 Gradle 래퍼 경로."""
    return str(REPO / f"gradlew{_BAT}")
