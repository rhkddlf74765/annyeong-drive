#!/usr/bin/env python3
"""공공데이터 CSV 3개에서 시연 경로 주변만 골라 앱용 zones.json을 만든다.

입력 (data/raw/):
  school_zones_gangbuk.csv     어린이보호구역 표준데이터, 강북구분
  cameras_national.csv         무인교통단속카메라 표준데이터, 전국 (과속 카메라는 경찰청 자료)
  parking_cameras_gangbuk.csv  무인교통단속카메라 표준데이터, 강북구분 (구청 주정차 단속 카메라)
  다른 지역·이름을 쓰면 --school / --speed / --parking 옵션으로 지정한다.
출력:
  app/src/main/assets/zones.json

예)
  python tools/build_zones.py --route tools/route.gpx
  python tools/build_zones.py --route tools/route.gpx --corridor 800
"""
import argparse
import csv
import datetime as dt
import json
import math
import re
import sys
from pathlib import Path

SCHOOL_ZONE_RADIUS_M = 300   # 어린이보호구역 지정 기준(정문 반경 300m)
SCHOOL_ZONE_LIMIT_KMH = 30


def read_csv(path):
    for enc in ("utf-8-sig", "cp949"):
        try:
            with open(path, encoding=enc, newline="") as f:
                return list(csv.DictReader(f))
        except UnicodeDecodeError:
            continue
    sys.exit(f"인코딩을 읽지 못했다: {path}")


def load_route(path):
    text = Path(path).read_text(encoding="utf-8")
    pts = [(float(a), float(b)) for a, b in re.findall(r'lat="([-\d.]+)"\s+lon="([-\d.]+)"', text)]
    if len(pts) < 2:
        sys.exit("경로 GPX에 점이 2개 이상 필요하다.")
    return pts


def dist_m(a, b):
    r = 6371000.0
    la1, la2 = math.radians(a[0]), math.radians(b[0])
    dla, dlo = la2 - la1, math.radians(b[1] - a[1])
    h = math.sin(dla / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin(dlo / 2) ** 2
    return 2 * r * math.asin(math.sqrt(h))


def dist_to_route(p, route):
    return min(dist_m(p, q) for q in route)


def coord(row):
    try:
        lat, lon = float(row["위도"]), float(row["경도"])
    except (KeyError, ValueError):
        return None
    if not (33 < lat < 39 and 124 < lon < 132):
        return None
    return lat, lon


def to_int(s, default=0):
    try:
        return int(float(s))
    except (TypeError, ValueError):
        return default


def build(args):
    raw = Path(args.raw)
    route = load_route(args.route)

    schools, speed, parking = [], [], []

    for i, r in enumerate(read_csv(raw / args.school)):
        c = coord(r)
        if c and dist_to_route(c, route) <= args.corridor:
            schools.append({
                "id": f"SZ{i + 1:03d}",
                "name": r.get("대상시설명", "").strip(),
                "facilityType": r.get("시설종류", "").strip(),
                "lat": c[0], "lon": c[1],
                "radiusM": SCHOOL_ZONE_RADIUS_M,
                "speedLimitKmh": SCHOOL_ZONE_LIMIT_KMH,
            })

    for r in read_csv(raw / args.speed):
        c = coord(r)
        limit = to_int(r.get("제한속도"))
        if not c or limit <= 0:          # 제한속도 없는 줄은 과속 카메라가 아니다
            continue
        if dist_to_route(c, route) <= args.corridor:
            zone = {"2": "school", "1": "senior"}.get(r.get("보호구역구분", "").strip(), "none")
            speed.append({
                "id": "SC" + r.get("무인교통단속카메라관리번호", "").strip(),
                "lat": c[0], "lon": c[1],
                "speedLimitKmh": limit,
                "protectedZone": zone,
                "road": r.get("도로노선명", "").strip(),
                "place": r.get("설치장소", "").strip(),
            })

    for r in read_csv(raw / args.parking):
        c = coord(r)
        if c and dist_to_route(c, route) <= args.corridor:
            parking.append({
                "id": "PC" + r.get("무인교통단속카메라관리번호", "").strip(),
                "lat": c[0], "lon": c[1],
                "road": r.get("도로노선명", "").strip(),
                "place": r.get("설치장소", "").strip(),
            })

    out = {
        "version": 1,
        "generatedAt": dt.date.today().isoformat(),
        "source": "공공데이터포털 전국어린이보호구역표준데이터, 전국무인교통단속카메라표준데이터 (2026-05-06 기준)",
        "corridorM": args.corridor,
        "schoolZones": schools,
        "speedCameras": speed,
        "parkingCameras": parking,
    }
    dst = Path(args.out)
    dst.parent.mkdir(parents=True, exist_ok=True)
    dst.write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"어린이보호구역 {len(schools)}곳, 과속 카메라 {len(speed)}대, 주정차 카메라 {len(parking)}대 → {dst}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--route", required=True, help="시연 경로 GPX")
    ap.add_argument("--raw", default="data/raw", help="원본 CSV 폴더")
    ap.add_argument("--school", default="school_zones_gangbuk.csv", help="어린이보호구역 CSV 이름")
    ap.add_argument("--speed", default="cameras_national.csv", help="과속 카메라가 든 카메라 CSV 이름")
    ap.add_argument("--parking", default="parking_cameras_gangbuk.csv", help="주정차 카메라 CSV 이름")
    ap.add_argument("--out", default="app/src/main/assets/zones.json")
    ap.add_argument("--corridor", type=float, default=500, help="경로에서 이 거리(m) 안만 남긴다")
    build(ap.parse_args())


if __name__ == "__main__":
    main()
