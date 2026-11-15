#!/usr/bin/env python3
"""시연 영상에 겹칠 경로 그림(SVG)을 만든다: 시연 경로, 보호구역, 단속 카메라, 안내가 나오는 지점(번호)과 출발·도착.

경로는 tools/route.gpx, 구역은 app/src/main/assets/zones.json, 안내 지점은 expected/route_en.json의 atKm을 쓴다.
PNG가 필요하면 Edge로 그린다:  msedge --headless --screenshot=route.png --window-size=1280,720 route.svg

예)  python tools/smoke/route_map.py tools/smoke/out/route.svg
"""
import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from smokelib import paths  # noqa: E402
from smokelib.route import _gpx_replay, load_expectation  # noqa: E402

WIDTH, HEIGHT, MARGIN = 1280, 720, 70
NEAR_ROUTE_M = 150
RULE_LABEL = {"SPEED_CAMERA": "Speed camera · 과속 단속 카메라", "SCHOOL_ZONE": "School zone · 어린이보호구역"}


def main() -> None:
    """경로 그림을 만들어 저장한다."""
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else paths.OUT / "route.svg"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(svg(), encoding="utf-8")
    print(out)


def svg() -> str:
    """경로 그림 SVG 글자."""
    gpx = _gpx_replay()
    expected = load_expectation("route_en.json")
    points = gpx.densify(gpx.load_points(str(paths.REPO / expected.route)), 10)
    zones = json.loads((paths.REPO / "app" / "src" / "main" / "assets" / "zones.json").read_text(encoding="utf-8"))
    route = [p for p, _ in points]
    project = _projector(route)
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH}" height="{HEIGHT}" viewBox="0 0 {WIDTH} {HEIGHT}">',
             '<rect width="100%" height="100%" fill="#f7f7f5"/>',
             '<style>text{font-family:"Malgun Gothic","Segoe UI",sans-serif}</style>']
    for zone in zones["schoolZones"]:
        if _near(route, (zone["lat"], zone["lon"]), zone["radiusM"] + NEAR_ROUTE_M):
            x, y = project(zone["lat"], zone["lon"])
            r = zone["radiusM"] * project.px_per_m
            parts.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{r:.1f}" fill="#f6c343" fill-opacity="0.28" stroke="#d99a00" stroke-width="2"/>')
    line = " ".join(f"{project(lat, lon)[0]:.1f},{project(lat, lon)[1]:.1f}" for lat, lon in route)
    parts.append(f'<polyline points="{line}" fill="none" stroke="#2b6cb0" stroke-width="7" stroke-linecap="round" stroke-linejoin="round"/>')
    for cam in zones["speedCameras"]:
        if _near(route, (cam["lat"], cam["lon"]), NEAR_ROUTE_M):
            x, y = project(cam["lat"], cam["lon"])
            parts.append(f'<rect x="{x - 7:.1f}" y="{y - 7:.1f}" width="14" height="14" fill="#c53030" stroke="white" stroke-width="2"/>')
    for cam in zones["parkingCameras"]:
        if _near(route, (cam["lat"], cam["lon"]), 200):
            x, y = project(cam["lat"], cam["lon"])
            parts.append(f'<rect x="{x - 8:.1f}" y="{y - 8:.1f}" width="16" height="16" fill="#6b46c1" stroke="white" stroke-width="2" transform="rotate(45 {x:.1f} {y:.1f})"/>')
    for number, announcement in enumerate(expected.announcements, 1):
        lat, lon = _point_at(points, announcement.at_km)
        x, y = project(lat, lon)
        parts.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="16" fill="#1a202c" stroke="white" stroke-width="3"/>'
                     f'<text x="{x:.1f}" y="{y + 6:.1f}" text-anchor="middle" font-size="17" font-weight="700" fill="white">{number}</text>')
    for label, (lat, lon), color in (("출발 Start", route[0], "#2f855a"), ("도착 End", route[-1], "#6b46c1")):
        x, y = project(lat, lon)
        parts.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="10" fill="{color}" stroke="white" stroke-width="3"/>'
                     f'<text x="{x + 16:.1f}" y="{y + 6:.1f}" font-size="20" font-weight="700" fill="{color}">{label}</text>')
    parts.append(_legend(expected))
    parts.append("</svg>")
    return "\n".join(parts)


def _legend(expected) -> str:
    """오른쪽 범례: 안내 번호와 문장, 기호 설명."""
    x, y = WIDTH - 470, 40
    rows = [f'<rect x="{x - 20}" y="{y - 30}" width="470" height="{88 + 34 * len(expected.announcements) + 96}" rx="12" fill="white" fill-opacity="0.92" stroke="#cbd5e0"/>',
            f'<text x="{x}" y="{y}" font-size="22" font-weight="700" fill="#1a202c">Demo route 3.8 km · 시연 경로 (GPX)</text>']
    y += 40
    for number, announcement in enumerate(expected.announcements, 1):
        label = RULE_LABEL.get(announcement.rule, announcement.rule)
        if announcement.sentences[0].startswith("School zone camera"):
            label = "School zone camera · 보호구역 단속 카메라"
        rows.append(f'<circle cx="{x + 12}" cy="{y - 6}" r="12" fill="#1a202c"/><text x="{x + 12}" y="{y - 1}" text-anchor="middle" font-size="14" font-weight="700" fill="white">{number}</text>'
                    f'<text x="{x + 34}" y="{y}" font-size="17" fill="#2d3748">{label} · {announcement.at_km:.2f} km</text>')
        y += 34
    y += 14
    rows += [f'<rect x="{x + 5}" y="{y - 14}" width="14" height="14" fill="#c53030"/><text x="{x + 34}" y="{y}" font-size="16" fill="#4a5568">단속 카메라 Camera</text>',
             f'<circle cx="{x + 12}" cy="{y + 24}" r="10" fill="#f6c343" fill-opacity="0.5" stroke="#d99a00"/><text x="{x + 34}" y="{y + 30}" font-size="16" fill="#4a5568">어린이보호구역 School zone</text>',
             f'<rect x="{x + 5}" y="{y + 46}" width="14" height="14" fill="#6b46c1" transform="rotate(45 {x + 12} {y + 53})"/><text x="{x + 34}" y="{y + 60}" font-size="16" fill="#4a5568">주정차 단속 카메라 Parking camera</text>']
    return "\n".join(rows)


def _projector(route):
    """위경도를 그림 좌표로 바꾸는 함수(경로가 왼쪽 영역에 꽉 차게, 북쪽이 위)."""
    lats = [p[0] for p in route]
    lons = [p[1] for p in route]
    mid = math.radians(sum(lats) / len(lats))
    m_per_lat = 111_195.0
    m_per_lon = m_per_lat * math.cos(mid)
    width_m = (max(lons) - min(lons)) * m_per_lon
    height_m = (max(lats) - min(lats)) * m_per_lat
    area_w, area_h = WIDTH - 520 - 2 * MARGIN, HEIGHT - 2 * MARGIN
    scale = min(area_w / max(width_m, 1), area_h / max(height_m, 1))

    def project(lat, lon):
        x = MARGIN + (lon - min(lons)) * m_per_lon * scale + (area_w - width_m * scale) / 2
        y = MARGIN + (max(lats) - lat) * m_per_lat * scale + (area_h - height_m * scale) / 2
        return x, y

    project.px_per_m = scale
    return project


def _near(route, point, within_m) -> bool:
    """점이 경로에서 그 거리 안에 있는지(경로 점 기준)."""
    gpx = _gpx_replay()
    return any(gpx.dist_m(p, point) <= within_m for p in route[::3])


def _point_at(points, km):
    """경로 시작부터 km 지점의 위치."""
    for p, d in points:
        if d / 1000 >= km:
            return p
    return points[-1][0]


if __name__ == "__main__":
    main()
