"""에뮬레이터 안 Pleos Connect Virtual Console(포트 8088)로 기어·차속·착좌를 넣는다.

세션을 1초마다 살려 두고, 주행 중에는 차속을 다시 보내 저절로 줄지 않게 한다(콘솔은 가속 없이 두면 감속한다).
정차는 차속 0을 넣고 콘솔 차속이 0에 머무는 것을 확인한 뒤 P를 넣는다(0을 한 번 보낸 뒤 감속을 이어 가는 콘솔 특성, 11/15 확인).
"""
import json
import threading
import time
import urllib.parse
import urllib.request

LOCAL_PORT = 18088
REMOTE_PORT = 8088
GEARS = {"P": "Park", "R": "Reverse", "N": "Neutral", "D": "Drive"}


class Console:
    """가상 콘솔 연결."""

    def __init__(self, device, client: str = "smoke"):
        self.device = device
        self.client = client
        self.base = f"http://127.0.0.1:{LOCAL_PORT}"
        self._cruise_kmh: float | None = None
        self._running = False
        self._thread: threading.Thread | None = None

    def forward(self) -> None:
        """PC 포트를 에뮬레이터 콘솔 포트에 잇는다."""
        self.device.adb("forward", f"tcp:{LOCAL_PORT}", f"tcp:{REMOTE_PORT}", check=False)

    def start(self) -> None:
        """포트를 잇고 세션 유지를 시작한다. 콘솔이 응답할 때까지 기다린다. adb 서버가 다시 뜨면 포트를 다시 잇는다."""
        self.device.on_server_restart = self.forward
        self.forward()
        deadline = time.monotonic() + 120
        while time.monotonic() < deadline:
            try:
                self._request("POST", "/browser/session", {"client": self.client})
                break
            except OSError:
                time.sleep(2)
        else:
            raise RuntimeError("가상 콘솔(8088)이 응답하지 않음")
        self._running = True
        self._thread = threading.Thread(target=self._keep_alive, daemon=True)
        self._thread.start()

    def stop(self) -> None:
        """세션 유지를 멈춘다."""
        self._running = False
        if self._thread:
            self._thread.join(timeout=3)

    def _keep_alive(self) -> None:
        """1초마다 세션을 살리고, 주행 중이면 2초마다 차속을 다시 보낸다."""
        tick = 0
        while self._running:
            try:
                self._request("POST", "/browser/session", {"client": self.client})
                cruise = self._cruise_kmh
                if cruise is not None and tick % 2 == 0:
                    self._controls(cruise, brake=0)
            except OSError:
                pass
            tick += 1
            time.sleep(1)

    def _request(self, method: str, path: str, params: dict) -> dict:
        """콘솔에 HTTP 요청을 보낸다. 연결이 끊겼으면(adb 서버 재시작) 포트를 다시 잇고 한 번 더 보낸다."""
        url = f"{self.base}{path}?{urllib.parse.urlencode(params)}"
        try:
            body = self._send(method, url)
        except OSError:
            time.sleep(2)
            self.forward()
            body = self._send(method, url)
        try:
            return json.loads(body)
        except json.JSONDecodeError:
            return {}

    @staticmethod
    def _send(method: str, url: str) -> str:
        """HTTP 요청 하나를 보내고 본문을 돌려준다."""
        request = urllib.request.Request(url, method=method)
        with urllib.request.urlopen(request, timeout=3) as response:
            return response.read().decode("utf-8", errors="replace")

    def _controls(self, kmh: float, brake: int) -> None:
        """차속·가속·제동을 보낸다(가속은 0)."""
        self._request("PUT", "/vehicle/controls", {"speed": f"{kmh:g}", "accelerator": 0, "brake": brake})

    def state(self) -> dict:
        """차량 상태 전체."""
        return self._request("GET", "/vehicle/state", {})

    def speed_kph(self) -> float:
        """콘솔이 알고 있는 차속(km/h)."""
        return float(self.state().get("controls", {}).get("speed_kph", 0))

    def gear(self, gear: str) -> None:
        """기어를 바꾼다(P·R·N·D)."""
        self._request("PUT", "/vehicle/gear", {"gear": GEARS[gear]})
        time.sleep(1.0)

    def seat(self, occupied: bool) -> None:
        """운전석 착좌를 바꾸고 콘솔 상태가 그 값이 될 때까지 기다린다(앱에 전달될 시간을 조금 더 준다)."""
        value = "true" if occupied else "false"
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            self._request("PUT", "/vehicle/seat", {"kind": "occupied", "area": "driver", "value": value})
            time.sleep(1.0)
            if self.driver_seated() == occupied:
                break
        time.sleep(1.5)

    def driver_seated(self) -> bool:
        """콘솔이 알고 있는 운전석 착좌."""
        return bool(self.state().get("seat", {}).get("occupied", {}).get("driver", False))

    def drive(self, kmh: float, gear: str = "D") -> None:
        """D(또는 R)로 바꾸고 그 차속으로 달린다. 차속은 정차할 때까지 유지한다."""
        self.gear(gear)
        self.cruise(kmh)

    def cruise(self, kmh: float) -> None:
        """기어는 그대로 두고 차속만 정해 유지한다."""
        self._cruise_kmh = kmh
        self._controls(kmh, brake=0)
        time.sleep(2.0)

    def stop_vehicle(self, timeout: float = 30) -> None:
        """차속 0을 넣고, 콘솔 차속이 0에 2초 넘게 머물 때까지 기다린다."""
        self._cruise_kmh = None
        self._controls(0, brake=30)
        deadline = time.monotonic() + timeout
        zero_since = None
        while time.monotonic() < deadline:
            if self.speed_kph() == 0:
                zero_since = zero_since or time.monotonic()
                if time.monotonic() - zero_since >= 2.0:
                    return
            else:
                zero_since = None
            time.sleep(0.4)
        raise RuntimeError("차속이 0으로 멈추지 않음")

    def park(self) -> None:
        """멈춘 것을 확인한 뒤 P를 넣는다."""
        self.stop_vehicle()
        self.gear("P")
