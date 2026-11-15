"""로그 해석: 음성 로그(AnnyeongTts)와 알림 목록(dumpsys notification)을 바르게 읽는다. 근거: 11/15 에뮬레이터 실제 출력 모양."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from smokelib.logs import finished_count, parse_notifications, parse_speech, spoken  # noqa: E402

LOGCAT = """--------- beginning of main
1794679218.658 25258 25258 I AnnyeongTts: speak[en_US] (queue=0): Speed camera ahead. Limit 50 kilometers per hour.
1794679218.700 25258 25390 D [GAAS][SDK]OnDeviceStreamTts: doSpeak(com.annyeongdrive.app) text=Speed camera ahead.
1794679222.549 25258 25274 D AnnyeongTts: event onDone
1794679280.155 25258 25258 I AnnyeongTts: stopAll: speaking=false dropped=0
1794679290.001 25258 25274 D AnnyeongTts: event onError NETWORK
"""

DUMP = """  NotificationRecord(0x05fe5bcc: pkg=com.annyeongdrive.app user=UserHandle{10} id=2147483647 tag=ranker_group importance=4 key=10|com.annyeongdrive.app|2147483647|ranker_group|1010181|ranker_group: Notification(channel=parking_warning shortcut=null contentView=null))
      mCreationTimeMs=1794678104540
    NotificationRecord(0x01239cfa: pkg=com.annyeongdrive.app user=UserHandle{10} id=2008 tag=null importance=4 key=10|com.annyeongdrive.app|2008|null|1010181: Notification(channel=parking_warning shortcut=null contentView=null vibrate=null))
                android.title=String (Annyeong_drive)
                android.text=String (Parking enforcement area nearby. Check the signs for allowed times and exceptions.)
      mCreationTimeMs=1794679496806
    NotificationRecord(0x0fffe088: pkg=com.annyeongdrive.app user=UserHandle{10} id=2007 tag=null importance=4 key=10|com.annyeongdrive.app|2007|null|1010181: Notification(channel=road_rule shortcut=null contentView=null))
                android.title=String (Annyeong_drive)
                android.text=String (School zone camera ahead. Limit 30 kilometers per hour.)
      mCreationTimeMs=1794679102203
    NotificationRecord(0x0aaaaaaa: pkg=ai.pleos.other user=UserHandle{10} id=1 tag=null importance=4 key=x: Notification(channel=misc shortcut=null))
                android.text=String (다른 앱)
      mCreationTimeMs=1794679000000
"""


class SpeechLogTest(unittest.TestCase):
    """음성 로그 해석."""

    def test_말하기_요청과_재생_끝과_멈춤과_오류를_순서대로_읽는다(self):
        """다른 태그 줄은 빼고 AnnyeongTts 사건만 시각과 함께 읽는다."""
        # 상황 · 행동
        events = parse_speech(LOGCAT)
        # 결과
        self.assertEqual(["speak", "done", "stop", "error"], [e.kind for e in events])
        self.assertEqual("en_US", events[0].locale)
        self.assertEqual("Speed camera ahead. Limit 50 kilometers per hour.", events[0].text)
        self.assertAlmostEqual(1794679218.658, events[0].time, places=3)

    def test_말한_문장과_끝난_수를_센다(self):
        """말하기 요청 1개, 재생 끝 1개."""
        # 상황
        events = parse_speech(LOGCAT)
        # 행동 · 결과
        self.assertEqual(1, len(spoken(events)))
        self.assertEqual(1, finished_count(events))


class NotificationDumpTest(unittest.TestCase):
    """알림 목록 해석."""

    def test_앱_알림만_만든_순서대로_읽고_묶음_알림은_뺀다(self):
        """다른 앱 알림과 ranker_group 묶음은 빠진다."""
        # 상황 · 행동
        found = parse_notifications(DUMP, "com.annyeongdrive.app")
        # 결과
        self.assertEqual([2007, 2008], [n.id for n in found])
        self.assertEqual(["road_rule", "parking_warning"], [n.channel for n in found])
        self.assertEqual("School zone camera ahead. Limit 30 kilometers per hour.", found[0].text)


if __name__ == "__main__":
    unittest.main()
