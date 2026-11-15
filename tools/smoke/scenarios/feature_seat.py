"""① 기능별: 운전석 착좌(F3 반납 뒤, F4 대여 중)."""
from smokelib.runtime import APP
from smokelib.scenario import scenario

from .common import PARKED_SCREENS


@scenario("F3", "feature", "반납 뒤 운전석이 비었다가 다시 앉으면 앱을 다시 켜지 않아도 다음 대여의 언어 선택이 뜬다",
          "CLAUDE.md 아키텍처(초기화는 반납 처리 완료로만), 상태 머신 T3·T5, 11/14 착좌 값 뒤집기 결정, 11/15 에뮬레이터 확인")
def seat_after_return(step):
    """반납 뒤 실제 착좌 신호로 다음 손님을 맞는다."""
    app = APP()
    step.situation("대여 중 → 관리자 코드로 반납 → 대기 화면")
    app.rented()
    app.return_car()
    step.action("운전석 비움 → 앉음")
    app.console.seat(False)
    step.check(app.current() == "WAITING", "비우기만 하면 대기 화면 그대로")
    app.console.seat(True)
    step.result("언어 선택이 뜬다")
    step.check(app.wait_screen("LANGUAGE", 20), f"언어 선택(지금: {app.current()})")


@scenario("F4", "feature", "대여 중 운전석이 비었다가 다시 앉아도 대여가 그대로 남는다",
          "CLAUDE.md 아키텍처(초기화는 착좌 해제가 아니라 반납 처리 완료로만)")
def seat_during_rental(step):
    """대여 중 내렸다 타도 언어 선택으로 돌아가지 않는다."""
    app = APP()
    step.situation("대여 중 정차 대기")
    app.rented(vendor_code=False)
    step.action("운전석 비움 → 앉음")
    app.console.seat(False)
    app.console.seat(True)
    step.result("언어 선택이 다시 뜨지 않고 정차 화면")
    found = app.wait_parked(10)
    step.check(found in PARKED_SCREENS, f"대여 유지, 정차 화면(지금: {found})")
