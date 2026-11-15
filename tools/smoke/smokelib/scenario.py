"""시나리오 틀. 시나리오마다 번호·단계·한국어 이름·근거를 붙여 등록하고, 상황 → 행동 → 결과로 나눠 기록·판정한다."""
import time
import traceback
from dataclasses import dataclass, field
from typing import Callable

LEVELS = ("feature", "linked", "full")
LEVEL_NAMES = {"feature": "① 기능별", "linked": "② 부분 연결", "full": "③ 전체 흐름"}


class ScenarioFailed(AssertionError):
    """시나리오 판정 실패."""


@dataclass(frozen=True)
class Scenario:
    """등록된 시나리오."""

    sid: str
    level: str
    name: str
    basis: str
    run: Callable


@dataclass
class Result:
    """시나리오 하나의 결과."""

    scenario: Scenario
    passed: bool
    seconds: float
    log: list[str] = field(default_factory=list)
    error: str = ""


REGISTRY: list[Scenario] = []


def scenario(sid: str, level: str, name: str, basis: str):
    """시나리오 함수를 등록한다. 함수는 Step을 하나 받는다."""
    if level not in LEVELS:
        raise ValueError(f"모르는 단계: {level}")

    def register(func: Callable) -> Callable:
        if any(s.sid == sid for s in REGISTRY):
            raise ValueError(f"같은 번호의 시나리오가 둘: {sid}")
        REGISTRY.append(Scenario(sid, level, name, basis, func))
        return func

    return register


class Step:
    """시나리오 진행 기록. 상황·행동·결과 단계를 적고 판정한다."""

    def __init__(self, app):
        self.app = app
        self.log: list[str] = []

    def situation(self, text: str) -> None:
        """상황(준비) 단계를 적는다."""
        self._write("상황", text)

    def action(self, text: str) -> None:
        """행동 단계를 적는다."""
        self._write("행동", text)

    def result(self, text: str) -> None:
        """결과 판정 단계를 적는다."""
        self._write("결과", text)

    def check(self, condition: bool, message: str) -> None:
        """조건이 맞는지 판정한다. 틀리면 시나리오 실패."""
        mark = "✓" if condition else "✗"
        self._write("판정", f"{mark} {message}")
        if not condition:
            raise ScenarioFailed(message)

    def equal(self, actual, expected, message: str) -> None:
        """두 값이 같은지 판정한다. 틀리면 두 값을 함께 남긴다."""
        if actual != expected:
            self._write("판정", f"✗ {message}\n      기대: {expected}\n      실제: {actual}")
            raise ScenarioFailed(message)
        self._write("판정", f"✓ {message}")

    def _write(self, kind: str, text: str) -> None:
        """기록 한 줄을 남기고 화면에도 바로 보인다."""
        line = f"  [{time.strftime('%H:%M:%S')}] {kind}: {text}"
        self.log.append(line)
        print(line, flush=True)


def run_one(item: Scenario, app, on_failure: Callable[[Scenario], None]) -> Result:
    """시나리오 하나를 실행한다. 실패하면 화면·로그를 남기는 함수를 부른다."""
    print(f"\n▶ {item.sid} {item.name}  (근거: {item.basis})", flush=True)
    step = Step(app)
    started = time.monotonic()
    try:
        item.run(step)
        return Result(item, True, time.monotonic() - started, step.log)
    except Exception as error:  # noqa: BLE001 - 한 시나리오가 실패해도 다음을 이어 간다
        detail = "".join(traceback.format_exception_only(type(error), error)).strip()
        step.log.append(f"  실패: {detail}")
        print(f"  실패: {detail}", flush=True)
        on_failure(item)
        return Result(item, False, time.monotonic() - started, step.log, detail)


def select(levels: list[str], only: list[str]) -> list[Scenario]:
    """실행할 시나리오를 고른다. 번호를 주면 그 번호만, 아니면 단계로. 등록 순서를 지킨다."""
    if only:
        wanted = {s.upper() for s in only}
        return [s for s in REGISTRY if s.sid.upper() in wanted]
    return [s for s in REGISTRY if s.level in levels]
