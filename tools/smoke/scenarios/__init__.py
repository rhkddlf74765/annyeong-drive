"""스모크 시나리오. 모듈을 읽으면 smokelib.scenario.REGISTRY에 등록된다. ① feature_* · ② linked · ③ full."""
from . import feature_vehicle, feature_seat, feature_setup, feature_learning, feature_parking, feature_call, feature_admin  # noqa: F401
from . import linked, full  # noqa: F401
