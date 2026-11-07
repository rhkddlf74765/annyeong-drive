#!/usr/bin/env python3
"""GPX 경로를 일정 속도로 다시 시간 매기거나, adb emu geo fix로 직접 재생한다.

표준 라이브러리만 쓴다. adb가 PATH에 있어야 한다(replay 모드).

예)
  # 1) gpx.studio에서 받은 GPX(시간 없음)를 40km/h, 1초 간격 점으로 다시 만든다
  python tools/gpx_replay.py retime route.gpx -o route_40kmh.gpx --kmh 40

  # 2) Import GPX가 안 될 때: geo fix로 1초마다 위치를 보낸다
  python tools/gpx_replay.py replay route.gpx --kmh 40

  # 3) 재생 시작 시 기어 D + 차속, 끝나면 기어 P + 차속 0 주입(VHAL 주입 권한이 있을 때만 동작)
  python tools/gpx_replay.py replay route.gpx --kmh 40 --vhal
"""
import argparse
import datetime as dt
import math
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

GPX_NS = "http://www.topografix.com/GPX/1/1"
PROP_GEAR_SELECTION = "0x11400400"   # 289408000
PROP_PERF_VEHICLE_SPEED = "0x11600207"  # 291504647, 단위 m/s
GEAR_DRIVE, GEAR_PARK = 8, 4


def load_points(path):
    root = ET.parse(path).getroot()
    pts = []
    for el in root.iter():
        if el.tag.rsplit("}", 1)[-1] in ("trkpt", "rtept"):
            pts.append((float(el.get("lat")), float(el.get("lon"))))
    if len(pts) < 2:
        sys.exit("GPX에 trkpt/rtept가 2개 이상 있어야 한다.")
    return pts


def dist_m(a, b):
    r = 6371000.0
    la1, la2 = math.radians(a[0]), math.radians(b[0])
    dla, dlo = la2 - la1, math.radians(b[1] - a[1])
    h = math.sin(dla / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin(dlo / 2) ** 2
    return 2 * r * math.asin(math.sqrt(h))


def densify(pts, step_m):
    """구간을 step_m 이하 간격으로 잘게 나눈다. (점, 시작부터 누적 거리) 목록을 돌려준다."""
    out, total = [(pts[0], 0.0)], 0.0
    for a, b in zip(pts, pts[1:]):
        d = dist_m(a, b)
        n = max(1, math.ceil(d / step_m))
        for i in range(1, n + 1):
            t = i / n
            out.append(((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t), total + d * t))
        total += d
    return out


def retime(args):
    speed = args.kmh / 3.6
    pts = densify(load_points(args.input), speed * args.interval)
    start = dt.datetime.now(dt.timezone.utc).replace(microsecond=0)
    ET.register_namespace("", GPX_NS)
    gpx = ET.Element(f"{{{GPX_NS}}}gpx", version="1.1", creator="annyeong-drive gpx_replay")
    seg = ET.SubElement(ET.SubElement(gpx, f"{{{GPX_NS}}}trk"), f"{{{GPX_NS}}}trkseg")
    for (lat, lon), d in pts:
        p = ET.SubElement(seg, f"{{{GPX_NS}}}trkpt", lat=f"{lat:.7f}", lon=f"{lon:.7f}")
        t = start + dt.timedelta(seconds=d / speed)
        ET.SubElement(p, f"{{{GPX_NS}}}time").text = t.strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"
    ET.ElementTree(gpx).write(args.output, encoding="utf-8", xml_declaration=True)
    total = pts[-1][1]
    print(f"{len(pts)}개 점, {total/1000:.2f} km, {total/speed/60:.1f}분 → {args.output}")


def adb(serial, *cmd):
    base = ["adb"] + (["-s", serial] if serial else [])
    r = subprocess.run(base + list(cmd), capture_output=True, text=True)
    if r.returncode != 0 or "error" in (r.stdout + r.stderr).lower():
        print(f"[경고] {' '.join(cmd)} → {(r.stdout + r.stderr).strip()}")


def vhal(serial, prop, value):
    adb(serial, "shell", "cmd", "car_service", "inject-vhal-event", prop, str(value))


def replay(args):
    speed = args.kmh / 3.6
    pts = densify(load_points(args.input), speed * args.interval)
    print(f"{len(pts)}개 점, {pts[-1][1]/1000:.2f} km, 약 {pts[-1][1]/speed/60:.1f}분 재생. Ctrl+C로 중단.")
    if args.vhal:
        vhal(args.serial, PROP_GEAR_SELECTION, GEAR_DRIVE)
        vhal(args.serial, PROP_PERF_VEHICLE_SPEED, f"{speed:.2f}")
    t0, prev_d = time.monotonic(), 0.0
    try:
        for i, ((lat, lon), d) in enumerate(pts):
            target = t0 + d / speed
            time.sleep(max(0.0, target - time.monotonic()))
            # geo fix는 경도, 위도 순서다
            adb(args.serial, "emu", "geo", "fix", f"{lon:.7f}", f"{lat:.7f}")
            if i % 30 == 0:
                print(f"  {i}/{len(pts)}  {d/1000:.2f} km  ({lat:.6f}, {lon:.6f})")
            prev_d = d
    except KeyboardInterrupt:
        print(f"중단: {prev_d/1000:.2f} km 지점")
    finally:
        if args.vhal:
            vhal(args.serial, PROP_PERF_VEHICLE_SPEED, "0")
            vhal(args.serial, PROP_GEAR_SELECTION, GEAR_PARK)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="mode", required=True)
    for name in ("retime", "replay"):
        p = sub.add_parser(name)
        p.add_argument("input")
        p.add_argument("--kmh", type=float, default=40.0)
        p.add_argument("--interval", type=float, default=1.0, help="점 사이 시간(초)")
        if name == "retime":
            p.add_argument("-o", "--output", required=True)
        else:
            p.add_argument("--serial", help="예: emulator-5554")
            p.add_argument("--vhal", action="store_true", help="시작 시 기어 D·차속, 끝나면 P·0 주입")
    args = ap.parse_args()
    retime(args) if args.mode == "retime" else replay(args)


if __name__ == "__main__":
    main()
