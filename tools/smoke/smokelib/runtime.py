"""실행 중인 앱 조작 객체 하나를 시나리오에 넘긴다. run_smoke.py가 정하고 시나리오가 APP()으로 꺼낸다."""
_app = None


def set_app(app) -> None:
    """이번 실행의 앱 조작 객체를 정한다."""
    global _app
    _app = app


def APP():  # noqa: N802 - 시나리오에서 짧게 부르려고 대문자
    """이번 실행의 앱 조작 객체."""
    if _app is None:
        raise RuntimeError("앱 조작 객체가 없음: run_smoke.py로 실행해야 한다")
    return _app
