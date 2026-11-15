#!/usr/bin/env python3
"""Annyeong Drive 스모크: 에뮬레이터의 release 앱을 실제 신호(가상 콘솔)·GPX·통화로 조작해 세 단계 시나리오를 돌린다.

  ① feature  기능별 독립(F1~F11)
  ② linked   부분 연결(L1~L9)
  ③ full     전체 흐름(E1~E3, 시연 경로 GPX 음성 확인)

표준 라이브러리만 쓴다. GitHub Actions에서는 돌리지 않는다(Pleos 에뮬레이터 이미지가 필요). 기기 없이 도는 부분은 tests/가 확인한다.

예)
  # release를 빌드·서명·설치하고 전부 실행. 에뮬레이터가 꺼져 있으면 켜고, 끝나면 끈다
  python tools/smoke/run_smoke.py --build

  # 이미 켜진 에뮬레이터·설치된 앱으로 ③만
  python tools/smoke/run_smoke.py --level full --skip-install --keep-emulator

  # 번호로 골라 실행
  python tools/smoke/run_smoke.py --only F7 L1 E1 --skip-install
"""
import argparse
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from smokelib import paths  # noqa: E402
from smokelib.app import App  # noqa: E402
from smokelib.console import Console  # noqa: E402
from smokelib.device import Device  # noqa: E402
from smokelib.runtime import set_app  # noqa: E402
from smokelib.scenario import LEVEL_NAMES, LEVELS, Result, run_one, select  # noqa: E402
from smokelib.screen import Strings  # noqa: E402

import scenarios  # noqa: E402,F401  - 읽으면 시나리오가 등록된다


def main() -> int:
    """인자를 읽어 스모크를 돌리고 실패가 있으면 1을 돌려준다."""
    args = _arguments()
    out = paths.OUT / time.strftime("%Y%m%d-%H%M%S")
    out.mkdir(parents=True, exist_ok=True)
    chosen = select(args.level, args.only)
    if not chosen:
        print("고른 시나리오가 없음")
        return 1
    apk = None if args.skip_install else _apk(args, out)
    device = Device(args.serial)
    started_here = _ensure_emulator(device)
    console = Console(device)
    results: list[Result] = []
    try:
        _wait_ready(device)
        if apk:
            device.install(apk)
        console.start()
        app = App(device, console, Strings(paths.RES))
        if args.screenshots:
            app.shots_dir = out / "screens"
            app.shots_dir.mkdir(exist_ok=True)
        set_app(app)
        for item in chosen:
            results.append(run_one(item, app, lambda failed: _save_failure(device, out, failed.sid)))
    finally:
        console.stop()
        _report(results, out)
        if started_here and not args.keep_emulator:
            _shutdown(device)
    return 0 if results and all(r.passed for r in results) else 1


def _arguments() -> argparse.Namespace:
    """명령 인자."""
    parser = argparse.ArgumentParser(description="Annyeong Drive 스모크 시나리오")
    parser.add_argument("--level", nargs="+", choices=LEVELS, default=list(LEVELS), help="돌릴 단계(기본: 전부)")
    parser.add_argument("--only", nargs="+", default=[], help="시나리오 번호(예: F7 L1 E1)")
    parser.add_argument("--build", action="store_true", help="release를 빌드하고 디버그 키로 서명해 설치")
    parser.add_argument("--apk", type=Path, help="설치할 서명된 APK")
    parser.add_argument("--skip-install", action="store_true", help="설치하지 않고 이미 깔린 앱으로")
    parser.add_argument("--keep-emulator", action="store_true", help="끝나도 에뮬레이터를 끄지 않음")
    parser.add_argument("--serial", help="기기 번호(예: emulator-5554)")
    parser.add_argument("--screenshots", action="store_true", help="화면마다 언어별로 스크린샷을 남김(out/…/screens)")
    return parser.parse_args()


def _ensure_emulator(device: Device) -> bool:
    """연결된 기기가 없으면 에뮬레이터를 켠다. 여기서 켰으면 True."""
    listed = device.adb("devices", check=False)
    if any(line.strip().endswith("device") for line in listed.splitlines()[1:]):
        return False
    print(f"에뮬레이터 켜는 중: {paths.AVD}", flush=True)
    subprocess.Popen([paths.emulator(), "-avd", paths.AVD, "-no-snapshot-save"],
                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    device.adb("wait-for-device", timeout=300)
    return True


def _wait_ready(device: Device) -> None:
    """부팅이 끝나고 Pleos 사용자(10)가 앞에 올 때까지 기다린다."""
    deadline = time.monotonic() + 420
    while time.monotonic() < deadline:
        if device.boot_completed() and device.shell("am get-current-user", check=False).strip() not in ("", "0"):
            time.sleep(15)
            print(f"기기 준비됨(사용자 {device.user})", flush=True)
            return
        time.sleep(5)
    raise RuntimeError("에뮬레이터가 준비되지 않음")


def _apk(args: argparse.Namespace, out: Path) -> Path:
    """설치할 APK. --build면 에뮬레이터를 켜기 전에 빌드·서명한다(메모리 부족 방지). 아니면 --apk."""
    if args.apk:
        return args.apk
    if not args.build:
        raise SystemExit("--build, --apk, --skip-install 중 하나를 주세요")
    print("release 빌드 중…", flush=True)
    subprocess.run([paths.gradlew(), "-p", str(paths.REPO), "assembleRelease", "--console=plain", "-q"], check=True)
    unsigned = paths.REPO / "app" / "build" / "outputs" / "apk" / "release" / "app-release-unsigned.apk"
    signed = out / "app-release-smoke.apk"
    keystore = Path.home() / ".android" / "debug.keystore"
    subprocess.run([paths.apksigner(), "sign", "--ks", str(keystore), "--ks-pass", "pass:android",
                    "--out", str(signed), str(unsigned)], check=True)
    return signed


def _save_failure(device: Device, out: Path, sid: str) -> None:
    """실패한 시나리오의 화면과 로그를 남긴다."""
    try:
        device.screenshot(out / f"{sid}.png")
        (out / f"{sid}.logcat.txt").write_text(device.logcat(), encoding="utf-8")
        (out / f"{sid}.ui.xml").write_text(device.ui_dump(), encoding="utf-8")
    except Exception as error:  # noqa: BLE001 - 기록 실패가 실행을 멈추지 않게
        print(f"  실패 기록을 남기지 못함: {error}")


def _report(results: list[Result], out: Path) -> None:
    """결과표를 화면과 report.md로 남긴다."""
    lines = ["# 스모크 결과", "", f"폴더: {out}", ""]
    for level in LEVELS:
        rows = [r for r in results if r.scenario.level == level]
        if not rows:
            continue
        lines += [f"## {LEVEL_NAMES[level]}", "", "| 번호 | 결과 | 시간 | 시나리오 |", "| --- | --- | --- | --- |"]
        for r in rows:
            mark = "통과" if r.passed else f"실패: {r.error[:80]}"
            lines.append(f"| {r.scenario.sid} | {mark} | {r.seconds:.0f}s | {r.scenario.name} |")
        lines.append("")
    passed = sum(r.passed for r in results)
    lines.append(f"합계: {passed} / {len(results)} 통과")
    lines += ["", "## 기록", ""] + [line for r in results for line in [f"### {r.scenario.sid}", *r.log, ""]]
    text = "\n".join(lines)
    (out / "report.md").write_text(text, encoding="utf-8")
    print("\n" + "\n".join(lines[: lines.index("## 기록")]))


def _shutdown(device: Device) -> None:
    """에뮬레이터를 끄고 꺼질 때까지 기다린다."""
    print("에뮬레이터 끄는 중", flush=True)
    device.adb("emu", "kill", check=False)
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        if "emulator" not in device.adb("devices", check=False):
            return
        time.sleep(2)


if __name__ == "__main__":
    sys.exit(main())
