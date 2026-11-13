package com.annyeongdrive.app.vehicle

/**
 * 좌석별 안전띠 경고 신호 묶음. 안전띠 규칙은 이 객체 한 곳에만 둔다.
 * 경고가 없다는 것은 착용 / 빈 좌석 / 센서 없음 / 인식 못 함 중 무엇이든 될 수 있으므로 "알 수 없음"과 같게 다룬다.
 * 그래서 "모두 착용"을 판단하는 기능은 일부러 두지 않는다. 착용 확인은 운전자의 수동 확인으로만 한다(결정 문서 7장).
 *
 * @property statuses 좌석별 경고 신호. 신호가 없는 좌석은 알 수 없음으로 본다
 */
data class SeatBeltReadings(val statuses: Map<Seat, SeatBeltStatus>) {

    /**
     * 미착용 경고가 있는 좌석을 좌석 순서대로 돌려준다. 출발 전 점검에서 그 좌석을 더 짚어 안내한다.
     *
     * @return 경고가 확인된 좌석. 없으면 빈 목록
     */
    fun warningSeats(): List<Seat> = Seat.entries.filter { statuses[it] == SeatBeltStatus.WARNING }

    companion object {
        /** 신호를 아직 받지 못한 상태. 모든 좌석이 알 수 없음이다. */
        val UNKNOWN = SeatBeltReadings(Seat.entries.associateWith { SeatBeltStatus.UNKNOWN })
    }
}
