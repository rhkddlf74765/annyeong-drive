"""화면 읽기·누르기. uiautomator 화면 구조를 읽고, 누를 문구는 strings.xml의 문구 ID로 찾는다(한국어·영어 모두).

문구 ID로 찾으므로 화면 작업으로 문구나 배치가 바뀌어도 따라간다. 화면 구조 해석과 문구 찾기는 기기 없이 테스트한다.
"""
import re
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

_BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")
_PLACEHOLDER = re.compile(r"%(\d+\$)?[sd]")
LANGUAGES = ("values", "values-en")


@dataclass(frozen=True)
class Node:
    """화면의 요소 하나."""

    text: str
    desc: str
    bounds: tuple[int, int, int, int]
    clickable: bool
    checked: bool
    enabled: bool

    @property
    def center(self) -> tuple[int, int]:
        """요소 가운데 좌표."""
        x1, y1, x2, y2 = self.bounds
        return (x1 + x2) // 2, (y1 + y2) // 2

    @property
    def label(self) -> str:
        """보이는 글자(없으면 설명)."""
        return self.text or self.desc


def parse_nodes(dump: str) -> list[Node]:
    """uiautomator XML을 요소 목록으로 바꾼다. 읽을 수 없으면 빈 목록."""
    start = dump.find("<?xml")
    if start < 0:
        start = dump.find("<hierarchy")
    if start < 0:
        return []
    try:
        root = ET.fromstring(dump[start:].strip())
    except ET.ParseError:
        return []
    nodes = []
    for element in root.iter("node"):
        match = _BOUNDS.match(element.get("bounds", ""))
        if not match:
            continue
        nodes.append(Node(
            text=element.get("text", ""),
            desc=element.get("content-desc", ""),
            bounds=tuple(int(v) for v in match.groups()),
            clickable=element.get("clickable") == "true",
            checked=element.get("checked") == "true",
            enabled=element.get("enabled") != "false",
        ))
    return nodes


class Strings:
    """앱 문구(strings.xml). 문구 ID로 한국어·영어 문구를 찾고, 자리 표시(%1$d 등)는 무엇이든 맞는 패턴으로 바꾼다."""

    def __init__(self, res_dir: Path):
        self.by_language = {lang: _read_strings(res_dir / lang / "strings.xml") for lang in LANGUAGES}

    def texts(self, string_id: str) -> list[str]:
        """그 문구 ID의 모든 언어 문구."""
        values = [table[string_id] for table in self.by_language.values() if string_id in table]
        if not values:
            raise KeyError(f"strings.xml에 없는 문구 ID: {string_id}")
        return values

    def text(self, string_id: str, language: str) -> str:
        """그 언어 폴더(values, values-en)의 문구."""
        return self.by_language[language][string_id]

    def matches(self, string_id: str, shown: str) -> bool:
        """화면 글자가 그 문구 ID(어느 언어든)와 같은지. 자리 표시는 무엇이든 맞는다."""
        return any(_pattern(text).fullmatch(shown.strip()) for text in self.texts(string_id))


def _pattern(template: str) -> re.Pattern:
    """문구 원문을 화면 글자와 비교할 정규식으로 바꾼다."""
    parts = _PLACEHOLDER.split(template)
    literal = [re.escape(p) for i, p in enumerate(parts) if i % 2 == 0 and p is not None]
    return re.compile(".+?".join(literal), re.DOTALL)


def _read_strings(path: Path) -> dict[str, str]:
    """strings.xml을 문구 ID → 문구로 읽는다. 안드로이드 따옴표 이스케이프(\\')를 푼다."""
    root = ET.parse(path).getroot()
    return {e.get("name"): (e.text or "").replace("\\'", "'").replace('\\"', '"') for e in root.iter("string")}


class Screen:
    """에뮬레이터 화면을 문구 ID로 읽고 누른다."""

    def __init__(self, device, strings: Strings):
        self.device = device
        self.strings = strings

    def nodes(self) -> list[Node]:
        """지금 화면 요소."""
        return parse_nodes(self.device.ui_dump())

    def labels(self) -> list[str]:
        """지금 화면에 보이는 글자 목록."""
        return [n.label for n in self.nodes() if n.label]

    def find(self, string_id: str, nodes: list[Node] | None = None) -> Node | None:
        """그 문구가 보이는 요소. 없으면 None."""
        for node in nodes if nodes is not None else self.nodes():
            if node.label and self.strings.matches(string_id, node.label):
                return node
        return None

    def has(self, string_id: str) -> bool:
        """그 문구가 보이는지."""
        return self.find(string_id) is not None

    def wait_any(self, string_ids: list[str], timeout: float = 15) -> str | None:
        """문구 중 하나가 보일 때까지 기다린다. 보인 문구 ID, 시간이 지나면 None."""
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            nodes = self.nodes()
            for string_id in string_ids:
                if self.find(string_id, nodes):
                    return string_id
            time.sleep(0.7)
        return None

    def tap(self, string_id: str, timeout: float = 15) -> None:
        """그 문구를 찾아 누른다. 시간 안에 보이지 않으면 실패."""
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            node = self.find(string_id)
            if node:
                self.device.tap(*node.center)
                time.sleep(1.0)
                return
            time.sleep(0.7)
        raise AssertionError(f"화면에 '{string_id}' 문구가 없어 누를 수 없음. 보이는 글자: {self.labels()[:12]}")

    def tap_label(self, label: str) -> None:
        """보이는 글자(문구 ID가 아닌 실제 글자)를 그대로 찾아 누른다."""
        for node in self.nodes():
            if node.label.strip() == label.strip():
                self.device.tap(*node.center)
                time.sleep(1.0)
                return
        raise AssertionError(f"화면에 '{label}' 글자가 없음")

    def type_into(self, label_id: str, text: str) -> None:
        """그 문구가 붙은 입력 칸을 눌러 글자를 넣는다."""
        self.tap(label_id)
        self.device.type_text(text)
        time.sleep(0.5)
