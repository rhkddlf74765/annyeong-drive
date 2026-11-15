"""앱 로그(AnnyeongTts)와 알림 목록(dumpsys notification)을 읽기 쉬운 값으로 바꾼다. 기기 없이 테스트한다."""
import re
from dataclasses import dataclass

_LINE = re.compile(r"^\s*(?P<time>\d+\.\d+)\s+\d+\s+\d+\s+(?P<level>[VDIWEF])\s+(?P<tag>[^:]+?)\s*:\s?(?P<msg>.*)$")
_SPEAK = re.compile(r"^speak\[(?P<locale>[^\]]+)\] \(queue=\d+\): (?P<text>.*)$")


@dataclass(frozen=True)
class SpeechEvent:
    """음성 로그 하나.

    kind: speak(말하기 요청) · done(재생 끝) · error(재생 오류) · stop(멈춤)
    """

    time: float
    kind: str
    locale: str = ""
    text: str = ""


@dataclass(frozen=True)
class Notification:
    """앱 알림 하나."""

    id: int
    channel: str
    text: str
    created_ms: int


def parse_speech(logcat: str) -> list[SpeechEvent]:
    """logcat(-v epoch) 출력에서 AnnyeongTts 음성 로그를 순서대로 뽑는다."""
    events = []
    for raw in logcat.splitlines():
        match = _LINE.match(raw)
        if not match or match["tag"].strip() != "AnnyeongTts":
            continue
        event = _speech_of(float(match["time"]), match["msg"].strip())
        if event:
            events.append(event)
    return events


def _speech_of(time: float, message: str) -> SpeechEvent | None:
    """음성 로그 한 줄을 사건으로 바꾼다. 관계없는 줄은 None."""
    speak = _SPEAK.match(message)
    if speak:
        return SpeechEvent(time, "speak", speak["locale"], speak["text"])
    if message.startswith("event onDone"):
        return SpeechEvent(time, "done")
    if message.startswith("event onError"):
        return SpeechEvent(time, "error", text=message)
    if message.startswith("stopAll"):
        return SpeechEvent(time, "stop", text=message)
    return None


def spoken(events: list[SpeechEvent]) -> list[SpeechEvent]:
    """말하기 요청만 고른다."""
    return [e for e in events if e.kind == "speak"]


def finished_count(events: list[SpeechEvent]) -> int:
    """재생이 끝난 문장 수."""
    return sum(1 for e in events if e.kind == "done")


def errors(events: list[SpeechEvent]) -> list[SpeechEvent]:
    """재생 오류."""
    return [e for e in events if e.kind == "error"]


_RECORD = re.compile(r"NotificationRecord\(0x[0-9a-f]+: pkg=(?P<pkg>\S+) .*? id=(?P<id>-?\d+) .*?channel=(?P<channel>[^ )]+)")
_TEXT = re.compile(r"android\.text=String \((?P<text>.*)\)\s*$")
_CREATED = re.compile(r"mCreationTimeMs=(?P<ms>\d+)")


def parse_notifications(dump: str, package: str) -> list[Notification]:
    """dumpsys notification --noredact 출력에서 그 앱의 알림을 뽑는다. 묶음(ranker_group) 알림은 뺀다. 오래된 것부터."""
    found: dict[int, Notification] = {}
    current = None
    for line in dump.splitlines():
        record = _RECORD.search(line)
        if record:
            current = _start(record, package)
            continue
        if current is None:
            continue
        text = _TEXT.search(line)
        if text and not current["text"]:
            current["text"] = text["text"]
        created = _CREATED.search(line)
        if created:
            current["created"] = int(created["ms"])
            if current["keep"]:
                found[current["id"]] = Notification(current["id"], current["channel"], current["text"], current["created"])
            current = None
    return sorted(found.values(), key=lambda n: (n.created_ms, n.id))


def _start(record: re.Match, package: str) -> dict:
    """알림 기록 하나를 읽기 시작한다."""
    keep = record["pkg"] == package and "ranker_group" not in record.group(0)
    return {"id": int(record["id"]), "channel": record["channel"], "text": "", "created": 0, "keep": keep}
