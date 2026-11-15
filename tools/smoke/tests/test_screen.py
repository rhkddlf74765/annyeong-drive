"""화면 읽기: uiautomator 화면 구조를 읽고, 문구 ID로 한국어·영어 화면 글자를 찾는다. 근거: 11/15 스모크 설계(문구 ID로 찾아 화면 작업을 따라간다)."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from smokelib import paths  # noqa: E402
from smokelib.screen import Strings, parse_nodes  # noqa: E402

DUMP = """UI hierchary dumped to: /dev/tty<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><hierarchy rotation="0">
<node index="0" text="" content-desc="" checked="false" clickable="false" enabled="true" bounds="[0,0][1920,1080]">
<node index="1" text="Before you drive" content-desc="" checked="false" clickable="false" enabled="true" bounds="[877,114][1037,142]" />
<node index="2" text="Everyone is wearing a seat belt" content-desc="" checked="false" clickable="true" enabled="true" bounds="[901,214][1141,238]" />
<node index="3" text="문제 2 / 5" content-desc="" checked="false" clickable="false" enabled="true" bounds="[877,300][1000,330]" />
</node></hierarchy>"""


class ParseNodesTest(unittest.TestCase):
    """화면 구조 해석."""

    def test_앞에_붙은_안내문을_건너뛰고_요소와_가운데_좌표를_읽는다(self):
        """dump 출력 앞의 'UI hierchary dumped' 글자가 있어도 읽는다."""
        # 상황 · 행동
        nodes = parse_nodes(DUMP)
        # 결과
        labels = [n.label for n in nodes if n.label]
        self.assertEqual(["Before you drive", "Everyone is wearing a seat belt", "문제 2 / 5"], labels)
        self.assertEqual((1021, 226), nodes[2].center)
        self.assertTrue(nodes[2].clickable)

    def test_읽을_수_없는_출력은_빈_목록이다(self):
        """dump가 실패해 XML이 없으면 빈 목록."""
        # 상황 · 행동 · 결과
        self.assertEqual([], parse_nodes("ERROR: could not get idle state."))


class StringsTest(unittest.TestCase):
    """문구 ID로 찾기(저장소의 실제 strings.xml)."""

    strings = Strings(paths.RES)

    def test_한국어와_영어_문구_모두_같은_문구_ID로_찾는다(self):
        """점검 제목은 한국어·영어 어느 쪽이든 맞는다."""
        # 상황 · 행동 · 결과
        self.assertTrue(self.strings.matches("precheck_title", "출발 전 점검"))
        self.assertTrue(self.strings.matches("precheck_title", self.strings.text("precheck_title", "values-en")))
        self.assertFalse(self.strings.matches("precheck_title", "학습 결과"))

    def test_자리_표시가_있는_문구는_숫자가_들어가도_맞는다(self):
        """'문제 %1$d / %2$d'는 '문제 2 / 5'와 맞는다."""
        # 상황 · 행동 · 결과
        self.assertTrue(self.strings.matches("learn_question_position", "문제 2 / 5"))

    def test_없는_문구_ID는_바로_알린다(self):
        """문구 ID가 바뀌면 조용히 지나가지 않고 KeyError."""
        # 상황 · 행동 · 결과
        with self.assertRaises(KeyError):
            self.strings.texts("no_such_string_id")


if __name__ == "__main__":
    unittest.main()
