"""경로 위치 찾기: 안내를 말한 시각으로 경로 위치(km)를 찾는다. 첫 점을 보내기 전이면 시작점(0km)이다.
근거: 11/15 스모크 E1(시작점 바로 앞 카메라 안내가 첫 점보다 먼저 나와 위치를 알 수 없음으로 실패)."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from smokelib.route import Replay  # noqa: E402


class KmAtTest(unittest.TestCase):
    """시각 → 경로 위치."""

    def setUp(self):
        """기기 없이 경로만 읽은 재생기."""
        self.replay = Replay(None, "tools/route.gpx", 50)

    def test_아무_점도_보내지_않았으면_위치를_모른다(self):
        """재생 전."""
        # 상황 · 행동 · 결과
        self.assertIsNone(self.replay.km_at(100.0))

    def test_첫_점을_보내기_전이면_경로_시작점이다(self):
        """출발 전에 시작점으로 옮겨 두므로 0km."""
        # 상황
        self.replay.sent = [(100.0, 0.0), (101.0, 0.014)]
        # 행동 · 결과
        self.assertEqual(0.0, self.replay.km_at(99.5))

    def test_보낸_점_사이면_마지막으로_보낸_점이다(self):
        """그 시각 직전 점의 위치."""
        # 상황
        self.replay.sent = [(100.0, 0.0), (101.0, 0.014), (102.0, 0.028)]
        # 행동 · 결과
        self.assertEqual(0.014, self.replay.km_at(101.5))


if __name__ == "__main__":
    unittest.main()
