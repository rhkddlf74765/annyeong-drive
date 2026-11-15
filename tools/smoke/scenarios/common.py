"""시나리오들이 함께 쓰는 값과 짧은 판정."""
import time

from smokelib.route import load_expectation

PARKED_SCREENS = ("PARKED_HOME", "LEARNING_START", "RESUME_OFFER")
ROUTE_EN = "route_en.json"
ROUTE_KO = "route_ko.json"


def parking_sentences(language: str) -> list[str]:
    """주정차 경고 첫 만남 문장(기대 목록 파일의 경로 끝 정차와 같다)."""
    name = ROUTE_EN if language == "en" else ROUTE_KO
    return list(load_expectation(name).stop_at_end.sentences)


def parking_utterance(language: str, encounter: int) -> str:
    """주정차 경고를 한 번에 말하는 글자. 첫 만남은 두 문장, 그 뒤는 첫 문장만."""
    sentences = parking_sentences(language)
    return " ".join(sentences if encounter == 1 else sentences[:1])


def wait_spoken(app, count: int, timeout: float = 30) -> list:
    """말하기 요청이 그 수만큼 쌓일 때까지 기다린다(재생 끝까지). 지금까지의 음성 사건을 돌려준다."""
    deadline = time.monotonic() + timeout
    events = app.speech()
    while time.monotonic() < deadline:
        events = app.speech()
        speaks = [e for e in events if e.kind == "speak"]
        done = [e for e in events if e.kind == "done"]
        if len(speaks) >= count and len(done) >= count:
            return events
        time.sleep(1)
    return events


def contains_in_order(haystack: list[str], needles: list[str]) -> bool:
    """needles가 haystack 안에 이어서(연속으로) 들어 있는지."""
    n = len(needles)
    return any(haystack[i:i + n] == needles for i in range(len(haystack) - n + 1))
