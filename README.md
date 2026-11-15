# Annyeong Drive

**외국인 렌터카 운전자를 위한 Pleos Connect 차량용 앱** — 출발 전에는 점검과 한국 운전 규칙 교육·퀴즈를, 주행 중에는 위치에 맞춘 짧은 규칙 안내를, 정차 순간에는 주정차 확인 안내를 제공하고, 반납하면 운전자 기록을 지운다.

현대차그룹 Pleos 앱 개발 공모전 1차 제출본(v1.0).

## 제출 정보

| 항목 | 값 |
| --- | --- |
| APK | [`release/annyeong-drive-1.0.apk`](release/annyeong-drive-1.0.apk) (release 빌드, 정식 키 서명) |
| APK SHA-256 | `B8141FDF0F466881EB46F64D6F4D65E0348F688CCA5187D4E03BFEBE44CBA18A` |
| APK를 빌드한 코드 | `main` · `6792368e4c939c9ffcf01055d3b8bc08cf6ed1f5` |
| 시연용 관리자 코드 | `Demo2026` (처음 쓰는 기기에서 업체 설정으로 직접 정한다) |

## 테스트 환경

| 항목 | 값 |
| --- | --- |
| 에뮬레이터 | **Pleos Connect v2.1.0** (Android Studio AVD, 시스템 이미지 `pleos_car_emulator`, Android 14 · API 34, x86_64) |
| 화면 규격 | **2560 × 1440**, 160 dpi |
| 사용자 | Pleos 사용자(user 10)로 실행 |
| 앱 SDK 설정 | minSdk 33, targetSdk·compileSdk 37 |
| 지원 언어 | 화면: 한국어·영어 / 음성 안내: 영어(한국어 대여는 알림으로 안내) |

## 실행 방법

1. Android Studio Device Manager에서 Pleos Connect v2.1.0 에뮬레이터를 켠다.
2. APK를 설치한다.
   ```
   adb install -r release/annyeong-drive-1.0.apk
   ```
3. 에뮬레이터 앱 목록에서 **Annyeong_drive**를 연다. 권한 창에서 모두 **허용**한다.
   - 위치, 알림, Pleos 위치·음성 권한, 차속 권한(`android.car.permission.CAR_SPEED`, 권한 창에 "알 수 없는 작업"으로 보인다).
   - 차속 권한을 거부하면 앱은 켜지지만 정차·주행을 알 수 없다.
4. 대기 화면(영어·한국어 함께 표시)에서 **운전석 착좌 신호**가 들어오면 언어 선택이 시작된다(아래 "필요한 설정").
5. 언어 선택 → 출발 전 점검(안전띠 직접 확인 → 점검 마침) → 교육·퀴즈.
6. 반납을 시험하려면 정차 대기 화면 → **업체 설정**에서 관리자 코드(예: `Demo2026`)를 먼저 정한 뒤 **반납 완료**에서 그 코드를 입력한다.

## 필요한 설정 (에뮬레이터에서 차량 신호 넣기)

release APK는 실제 차량 신호를 받는다. 에뮬레이터에서는 Pleos Connect에 들어 있는 **가상 콘솔**(에뮬레이터 안 포트 8088)과 GPX 위치로 신호를 넣는다.

| 신호 | 앱이 받는 곳 | 에뮬레이터에서 넣는 법 |
| --- | --- | --- |
| 기어·운전석 착좌·안전띠 경고 | Pleos Vehicle SDK 2.3.2 | 가상 콘솔 |
| 차속 | android.car `PERF_VEHICLE_SPEED` | 가상 콘솔 |
| 주행 중 화면 제한 | android.car `CarUxRestrictionsManager` | 기어·차속을 넣으면 시스템이 바꾼다 |
| 위치 | Pleos FusedLocation 1.1.3 | GPX 재생 |
| 통화 | Android 오디오 모드 | `adb emu gsm call/accept/cancel 01012345678` |

```
# 가상 콘솔: PC 브라우저에서 http://127.0.0.1:18088 (기어 · 차속)
adb forward tcp:18088 tcp:8088

# 운전석 착좌(콘솔 화면에 버튼이 없어 HTTP로 넣는다. false는 비움)
curl -X PUT "http://127.0.0.1:18088/vehicle/seat?kind=occupied&area=driver&value=true"

# 시연 경로 위치 재생(3.8km, 보호구역·단속 카메라 안내 6건 + 끝 지점 주정차 확인 안내)
python tools/gpx_replay.py replay tools/route.gpx --kmh 50
```

- 정차할 때는 차속 0을 넣고 멈춘 것을 확인한 뒤(1~2초) 기어 P를 넣는다.
- 한 번에 시연 흐름(대여 → 점검 → 교육 → 주행 안내 → 정차 → 반납)을 진행하려면 `python tools/smoke/demo.py --apk release/annyeong-drive-1.0.apk` (에뮬레이터를 켜고 가상 콘솔·GPX를 자동으로 넣는다. 브라우저 `http://127.0.0.1:18090/live.html`에 경로와 현재 위치·안내 문장을 표시).

## 주요 기능

1. **대여 시작**: 운전석 착좌 → 언어 선택 → 출발 전 점검(전 좌석 안전띠 직접 확인, 업체가 정한 유종·하이패스·반납 조건). 센서 경고가 없다는 이유로 착용 처리하지 않는다.
2. **교육·퀴즈**: 학습 카드 6개, 4지선다 퀴즈 5개, 해설과 다시 볼 규칙. P · 차속 0 · 화면 제한 없음일 때만 열린다.
3. **주행 중 자동 전환**: D·R 또는 차속이 생기면 교육 화면을 닫고 학습 음성을 멈춘다. 정차하면 "이어서 확인할까요"로 멈춘 곳부터 이어 본다.
4. **주행 중 규칙 안내**: 시연 지역의 어린이보호구역·과속 단속 카메라에 다가가면 안내한다. 처음 만난 규칙은 두 문장, 다시 만나면 한 문장. 통화 중에는 말하지 않고 알림만 띄운다. 알림은 8초(주정차 경고 10초) 뒤 사라진다.
5. **주정차 확인 안내**: 주행 후 P로 정차한 곳이 주정차 단속 카메라 100m 안이면 표지의 허용 시간·예외를 확인하라고 안내한다(금지를 확정하지 않는다).
6. **반납**: 관리자 코드로 반납하면 언어·교육 진행 등 운전자 데이터를 지우고 업체 설정은 남긴다. 운전자 데이터는 기기에만 저장하고 백업에서 제외한다.

## 빌드와 시험

```
./gradlew assembleRelease                     # 제출용(실제 차량 신호). 서명은 tools/sign_release.ps1
./gradlew assembleDebug                       # 확인용(가짜 신호 + "Annyeong 확인용" 화면)
./gradlew testDebugUnitTest                   # 전체 JVM 시험
./gradlew testDebugUnitTest -PtestGroup=unit  # unit · signal · ui 묶음별(GitHub Actions에서 따로 실행)
```

- Kotlin 2.2.10, Jetpack Compose, AGP 9.4.1, Gradle 9.6.0, JDK 17 이상
- Pleos SDK: Vehicle 2.3.2, FusedLocation 1.1.3, TextToSpeech 3.3.1(NaviHelper 3.0.5는 의존성만 선언, 미연동)
- 구조: 입력 어댑터 → DriveEngine → DriveCoordinator(상태 패턴, 순수 Kotlin) → 출력(음성·알림·화면·저장소). 설계 문서는 `docs/`.
- 에뮬레이터 스모크(기능별 11 · 부분 연결 9 · 전체 흐름 3): `python tools/smoke/run_smoke.py --build`

## 알려진 한계 (1차 제출 기준)

- 운전석 착좌 값은 Pleos SDK 2.3.2가 표준 원값과 반대로 주어 뒤집어 읽는다(에뮬레이터 기준, 실차 확인 필요).
- 좌석별 안전띠 경고는 실제 신호에 연결했으나 가상 콘솔에 해당 입력이 없어 에뮬레이터로는 확인하지 못했다(JVM 신호 시험으로 확인).
- 주행 안내는 시연 지역 구역 데이터(`app/src/main/assets/zones.json`)만 쓴다. NaviHelper 연동과 내비 음성과의 겹침 확인은 후속.
- 영어 문구는 임시 번역. 일본어·스페인어는 계획(문구 파일 추가로 확장).
- 반납 장소 검증은 항상 통과(후속). 오주유 안내·도움 카드는 후속 기능.
