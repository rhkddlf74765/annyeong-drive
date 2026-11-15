"""주행 경로: 기대 목록(expected/route_*.json)을 읽고, GPX를 실제 시간에 맞춰 위치로 재생하며 재생 위치(km)를 기록한다.

기대 목록은 JVM 신호 테스트(RouteGuidanceSignalTest)도 같은 파일을 읽어 확인한다. GPX 나누기는 tools/gpx_replay.py를 그대로 쓴다.
"""
import importlib.util
import json
import threading
import time
from bisect import bisect_right
from dataclasses import dataclass

from . import paths


def _gpx_replay():
    """tools/gpx_replay.py를 모듈로 읽는다(같은 나누기 방식을 쓰려고)."""
    spec = importlib.util.spec_from_file_location("gpx_replay", paths.REPO / "tools" / "gpx_replay.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


@dataclass(frozen=True)
class Announcement:
    """기대 안내 하나."""

    rule: str
    sentences: tuple[str, ...]
    at_km: float | None


@dataclass(frozen=True)
class RouteExpectation:
    """경로 하나의 기대 목록."""

    route: str
    kmh: float
    language: str
    spoken: bool
    announcements: tuple[Announcement, ...]
    stop_at_end: Announcement

    def joined(self) -> list[str]:
        """안내마다 문장을 이어 붙인다. 앱은 안내 하나를 한 번에 말하고(11/15 확인) 알림에도 같은 글자를 띄운다."""
        return [" ".join(a.sentences) for a in self.announcements]

    def stop_joined(self) -> str:
        """경로 끝 주정차 경고를 이어 붙인 글자."""
        return " ".join(self.stop_at_end.sentences)


def load_expectation(name: str) -> RouteExpectation:
    """기대 목록 파일을 읽는다. 형식이 틀리면 ValueError."""
    data = json.loads((paths.EXPECTED / name).read_text(encoding="utf-8"))
    for key in ("route", "kmh", "language", "spoken", "announcements", "stopAtEnd"):
        if key not in data:
            raise ValueError(f"{name}: '{key}' 항목이 없음")
    return RouteExpectation(
        route=data["route"],
        kmh=float(data["kmh"]),
        language=data["language"],
        spoken=bool(data["spoken"]),
        announcements=tuple(_announcement(a, name) for a in data["announcements"]),
        stop_at_end=_announcement(data["stopAtEnd"], name),
    )


def _announcement(item: dict, name: str) -> Announcement:
    """기대 안내 한 줄을 읽는다."""
    if not item.get("rule") or not item.get("sentences"):
        raise ValueError(f"{name}: 안내에 rule·sentences가 없음: {item}")
    at_km = item.get("atKm")
    return Announcement(item["rule"], tuple(item["sentences"]), None if at_km is None else float(at_km))


class Replay:
    """GPX를 실제 시간에 맞춰 geo fix로 재생한다. 점마다 (보낸 시각, 시작부터 km)를 기록한다."""

    def __init__(self, device, route: str, kmh: float, until_km: float | None = None):
        gpx = _gpx_replay()
        self.device = device
        self.speed_mps = kmh / 3.6
        points = gpx.densify(gpx.load_points(str(paths.REPO / route)), self.speed_mps)
        self.points = [(p, d / 1000) for p, d in points if until_km is None or d / 1000 <= until_km]
        self.sent: list[tuple[float, float]] = []
        self._thread: threading.Thread | None = None
        self._stop = False

    @property
    def start_point(self) -> tuple[float, float]:
        """경로 첫 점(위도, 경도)."""
        return self.points[0][0]

    @property
    def end_point(self) -> tuple[float, float]:
        """경로 마지막 점(위도, 경도)."""
        return self.points[-1][0]

    @property
    def duration_s(self) -> float:
        """재생 시간(초)."""
        return self.points[-1][1] * 1000 / self.speed_mps

    def start(self) -> None:
        """다른 스레드에서 재생을 시작한다."""
        self._thread = threading.Thread(target=self._run, daemon=True)
        self._thread.start()

    def _run(self) -> None:
        """점마다 정해진 시각에 위치를 보낸다."""
        t0 = time.monotonic()
        for (lat, lon), km in self.points:
            if self._stop:
                return
            target = t0 + km * 1000 / self.speed_mps
            time.sleep(max(0.0, target - time.monotonic()))
            self.device.geo_fix(lat, lon)
            self.sent.append((time.time(), km))

    def wait(self) -> None:
        """재생이 끝날 때까지 기다린다."""
        if self._thread:
            self._thread.join()

    def stop(self) -> None:
        """재생을 멈춘다."""
        self._stop = True
        self.wait()

    def km_at(self, epoch: float) -> float | None:
        """그 시각(초)에 마지막으로 보낸 위치(km). 재생 전이면 None."""
        times = [t for t, _ in self.sent]
        index = bisect_right(times, epoch) - 1
        if index < 0:
            return None
        return self.sent[index][1]
