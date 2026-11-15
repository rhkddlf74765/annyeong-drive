"""시나리오 등록: 검수받은 23개(F1~F11, L1~L9, E1~E3)가 모두 있고, 시나리오가 누르거나 찾는 문구 ID가 strings.xml에 모두 있다.
화면 작업으로 문구 ID가 바뀌면 에뮬레이터를 켜기 전에 여기서 걸린다. 근거: 11/15 스모크 시나리오 설계(사용자 검수)."""
import re
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

import scenarios  # noqa: E402,F401
from smokelib import paths  # noqa: E402
from smokelib.app import LANGUAGE_BUTTONS, SCREEN_MARKERS  # noqa: E402
from smokelib.scenario import REGISTRY  # noqa: E402
from smokelib.screen import Strings  # noqa: E402

REVIEWED = [f"F{i}" for i in range(1, 12)] + [f"L{i}" for i in range(1, 10)] + ["E1", "E2", "E3"]
_USE = re.compile(r"""(?:tap|has|find|type_into|text|wait_any)\(\s*\[?\s*["']([a-z][a-z0-9_]+)["']""")


class ScenarioRegistryTest(unittest.TestCase):
    """시나리오 목록."""

    def test_검수받은_시나리오가_빠짐없이_순서대로_있다(self):
        """F1~F11, L1~L9, E1~E3."""
        # 상황 · 행동 · 결과
        self.assertEqual(REVIEWED, [s.sid for s in REGISTRY])

    def test_시나리오마다_한국어_이름과_근거가_있다(self):
        """이름·근거가 비어 있지 않다."""
        # 상황 · 행동 · 결과
        self.assertTrue(all(s.name.strip() and s.basis.strip() for s in REGISTRY))


class StringIdUsageTest(unittest.TestCase):
    """시나리오가 쓰는 문구 ID."""

    strings = Strings(paths.RES)

    def test_시나리오와_조작_코드가_쓰는_문구_ID가_모두_strings_xml에_있다(self):
        """tap·has·wait_any 등에 적은 ID와 화면 표지 ID."""
        # 상황
        used = {sid for _, sid in SCREEN_MARKERS} | set(LANGUAGE_BUTTONS.values())
        for source in list((ROOT / "scenarios").glob("*.py")) + [ROOT / "smokelib" / "app.py"]:
            used |= set(_USE.findall(source.read_text(encoding="utf-8")))
        # 행동
        missing = sorted(sid for sid in used if not self._exists(sid))
        # 결과
        self.assertEqual([], missing)

    def _exists(self, string_id: str) -> bool:
        """문구 ID가 있는지."""
        try:
            self.strings.texts(string_id)
            return True
        except KeyError:
            return False


if __name__ == "__main__":
    unittest.main()
