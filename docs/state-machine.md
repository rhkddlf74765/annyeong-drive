# 전체 상태 머신

노션 "Annyeong Drive 개발 과정 기록" 메인 페이지의 "전체 상태 머신" 섹션을 그대로 옮긴 것이다(2026-11-11 개정판).
원본: https://app.notion.com/p/74f40f22bcf4835b9b798156ec41a121
노션 원본이 바뀌면 이 문서도 함께 갱신한다.

---

**11/11 개정: "외국인 렌터카 11개 기능 실행안"(11/10) 반영.** 출발 전 점검과 교육·퀴즈를 정차 상태에 넣고, 대체 주차장 안내·진입 실패·경로 미리보기·비상등 자동 트리거를 뺐다. 초기화는 착좌 해제가 아니라 반납 처리 완료 시에만 한다(교육 진행은 대여 기간 동안 유지). 오주유 방지는 기능 플래그로 켜고 끌 수 있는 뼈대로 둔다(11/11 결정, 철회 가능). 국가별 차이 안내는 확장 기능이다. 지금은 구현하지 않지만 문구·퀴즈를 (규칙 ID, 언어, 운전 국가) 키로 찾고, 국가가 없으면 언어 기본값으로 떨어지도록 설계한다.

앱 전체는 운전 상태 하나로 움직인다. 입력(차량·내비·위치 이벤트)은 DriveCoordinator가 받아 이 상태 머신에 따라 기능을 하나만 고른다. P 이벤트에 여러 기능이 걸릴 때는 **반납 처리 > 검수된 주정차 금지 구역 > 주유소(오주유 방지, 기능 켜짐일 때) > 미완료 교육 이어 보기 > 일반 정차** 순서로 판단한다. 주행 중 규칙 안내는 주행 상태에서 동작하고, 교육 화면은 정차 상태에서만 열린다.

교육 진입 조건: 기어 P + 차속 0 + UX 제한 없음. 기어 D/R, 차속 발생, UX 제한 중 하나라도 생기면 팝업 없이 교육 화면과 학습 음성을 즉시 닫고, 다음 정차 때 "이어 보기"만 보여 준다.

```mermaid
%%{init: {"htmlLabels": false}}%%
stateDiagram-v2
    direction LR
    state "대기" as Idle
    state "대여 설정" as Setup
    state "정차 (P)" as Parked
    state "주행 (D/R)" as Driving
    state "반납 처리" as Returning
    state "초기화" as Reset

    [*] --> Idle
    Idle --> Setup : 운전석 착좌 [대여 설정 없음]
    Idle --> Parked : 운전석 착좌 [대여 설정 있음]
    Setup --> Parked : 언어 선택 + 출발 전 점검 완료
    Parked --> Driving : 기어 D/R 또는 차속 발생 (교육 화면·음성 즉시 중단)
    Driving --> Parked : 기어 P + 차속 0
    Parked --> Returning : "반납 완료" 버튼 (정차 중)
    Returning --> Reset : 관리자 코드 확인
    Idle --> Reset : 앱 시작 시 반납 예정 + N시간 경과
    Reset --> Idle

    state Setup {
        state "언어 선택" as Lang
        state "출발 전 점검 (안전띠·유종·하이패스·반납 조건)" as Precheck
        [*] --> Lang
        Lang --> Precheck
    }

    state Driving {
        state "일반 주행 (규칙 상기)" as Cruising
        [*] --> Cruising
        Cruising --> Cruising : P0 보호구역·제한속도·단속 카메라
        Cruising --> Cruising : P1 회전·유턴·요금소·차로 단속 접근
    }

    state Parked {
        state "맥락 판단" as Context
        state "주정차 경고 (검수 구역)" as NoParking
        state "이어 보기 제안" as Resume
        state "정차 대기" as Rest
        state "교육·퀴즈" as Learning
        state "수동 도움 카드 (P1)" as Help
        state "오주유 방지 (뼈대, 기능 플래그)" as Fuel
        state ctx <<choice>>
        [*] --> Context
        Context --> ctx
        ctx --> NoParking : 검수된 금지 구역 안
        ctx --> Fuel : 주유소 반경 안 [기능 켜짐]
        ctx --> Resume : 미완료 교육 있음
        ctx --> Rest : 그 외
        NoParking --> Rest : 안내 확인
        Fuel --> Rest : 안내 확인
        Resume --> Learning : 이어 보기
        Resume --> Rest : 나중에 보기
        Rest --> Learning : 사용자가 교육·퀴즈 열기
        Rest --> Help : 사용자가 도움 메뉴 선택
        Learning --> Rest : 완료·닫기
    }

    state Learning {
        state "교육 시작" as Start
        state "학습 카드" as Card
        state "문제" as Quiz
        state "해설" as Explain
        state "결과·복습" as Result
        [*] --> Start
        Start --> Card
        Card --> Quiz
        Quiz --> Explain : 정답 확인
        Quiz --> Quiz : 건너뛰기 (미완료로 남김)
        Explain --> Quiz : 다음 문제
        Explain --> Result : 마지막 문항
    }
```

| 상태 | 들어가는 조건 | 이 상태에서 동작하는 기능 |
| --- | --- | --- |
| 대기 | 앱 시작, 초기화 완료 | 없음 |
| 대여 설정 | 착좌 + 대여 설정 없음 | 대여 시작(언어 선택), 출발 전 점검 |
| 주행 | 기어 D/R 또는 차속 발생 | 주행 중 규칙 안내(P0 보호구역·제한속도·카메라, P1 회전·유턴·요금소·차로 상기), 주행 중 화면 제한 |
| 정차 | 기어 P + 차속 0 | 주정차 경고(검수 구역), 교육·퀴즈, 이어 보기, 수동 도움 카드(P1) |
| 반납 처리 | 정차 중 "반납 완료" 버튼 | 관리자 코드 입력·해시 비교, 오류 잠금 |
| 초기화 | 관리자 코드 확인, 또는 앱 시작 시 반납 예정 시각 + N시간 경과 | 언어·국가 설정, 교육 진행, 안내 기록 삭제. 착좌 해제만으로는 지우지 않는다 |
