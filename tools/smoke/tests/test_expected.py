"""기대 목록 파일: 영어·한국어 파일이 읽히고, 같은 경로·같은 안내 구성·같은 위치이며, 위치가 앞으로만 간다. 근거: 11/15 스모크 설계(기대 목록 하나를 JVM과 스모크가 함께 쓴다)."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from smokelib import paths  # noqa: E402
from smokelib.route import load_expectation  # noqa: E402


class ExpectationFileTest(unittest.TestCase):
    """기대 목록 파일 형식."""

    en = load_expectation("route_en.json")
    ko = load_expectation("route_ko.json")

    def test_영어는_말하고_한국어는_알림으로만_띄운다(self):
        """spoken 값."""
        # 상황 · 행동 · 결과
        self.assertTrue(self.en.spoken)
        self.assertFalse(self.ko.spoken)

    def test_두_언어가_같은_경로와_같은_규칙_순서와_문장_수를_가진다(self):
        """언어만 다르고 안내 구성은 같다."""
        # 상황 · 행동 · 결과
        self.assertEqual(self.en.route, self.ko.route)
        self.assertEqual([a.rule for a in self.en.announcements], [a.rule for a in self.ko.announcements])
        self.assertEqual([len(a.sentences) for a in self.en.announcements], [len(a.sentences) for a in self.ko.announcements])
        self.assertEqual([a.at_km for a in self.en.announcements], [a.at_km for a in self.ko.announcements])

    def test_안내_위치는_경로를_따라_앞으로만_간다(self):
        """atKm이 모두 있고 늘어난다."""
        # 상황
        positions = [a.at_km for a in self.en.announcements]
        # 행동 · 결과
        self.assertNotIn(None, positions)
        self.assertEqual(sorted(positions), positions)

    def test_경로_파일이_저장소에_있다(self):
        """route가 가리키는 GPX."""
        # 상황 · 행동 · 결과
        self.assertTrue((paths.REPO / self.en.route).is_file())

    def test_처음_만난_규칙은_두_문장_다시_만나면_한_문장이다(self):
        """규칙별 첫 안내만 2문장(8단계 결정)."""
        # 상황
        seen = set()
        counts = []
        # 행동
        for a in self.en.announcements:
            counts.append((a.rule in seen, len(a.sentences)))
            seen.add(a.rule)
        # 결과
        self.assertTrue(all(n == (1 if again else 2) for again, n in counts), counts)


if __name__ == "__main__":
    unittest.main()
