package com.annyeongdrive.app.session

import com.annyeongdrive.app.zones.Geo

/**
 * 새 주행이 시작됐는지 알려 주는 신호원. 환경마다 다른 신호원을 쓴다.
 * - 에뮬레이터: 위치 순간이동(JumpTripBoundary). GPX를 처음부터 다시 재생하면 위치가 튄다.
 * - 실차(확장 예정): 내비 경로 안내 시작·도착(NaviHelper), 기어 P 장시간 정차, 시동 꺼짐.
 */
interface TripBoundary {
    /** 위치가 들어올 때마다 부른다. true면 이 위치부터 새 주행이다. 첫 위치는 항상 true. */
    fun isNewTrip(lat: Double, lon: Double, timeMs: Long): Boolean
}

/**
 * 실제 주행으로는 나올 수 없는 위치 이동이면 새 주행으로 본다. 둘 중 하나면 새 주행이다.
 * 1. 앞으로 튐: 한 번에 minJumpM(80m) 넘게, maxSpeedMps(70m/s, 시속 약 250km) 넘는 속도
 * 2. 뒤로 튐: 진행 방향의 반대쪽(120도 넘게 차이)으로 maxReverseMps(20m/s, 시속 72km) 넘는 속도로
 *    minReverseJumpM(20m) 넘게 이동. 출발 직후 멈추고 GPX를 다시 재생한 경우(11/8)를 잡는다.
 */
class JumpTripBoundary(
    private val minJumpM: Double = 80.0,
    private val maxSpeedMps: Double = 70.0,
    private val minReverseJumpM: Double = 20.0,
    private val maxReverseMps: Double = 20.0,
    private val reverseAngleDeg: Double = 120.0,
) : TripBoundary {

    private var last: Triple<Double, Double, Long>? = null
    private var headingFrom: Pair<Double, Double>? = null
    private var headingDeg: Double? = null

    /** 마지막 판단에서 이동한 거리(로그용). */
    var lastJumpM: Double = 0.0
        private set

    override fun isNewTrip(lat: Double, lon: Double, timeMs: Long): Boolean {
        val prev = last
        last = Triple(lat, lon, timeMs)
        if (prev == null) {
            lastJumpM = 0.0
            headingFrom = lat to lon
            headingDeg = null
            return true
        }
        val jump = Geo.distanceM(prev.first, prev.second, lat, lon)
        val dtSec = (timeMs - prev.third).coerceAtLeast(1L) / 1000.0
        val speed = jump / dtSec
        lastJumpM = jump

        val forwardJump = jump > minJumpM && speed > maxSpeedMps
        val heading = headingDeg
        val reverseJump = heading != null && jump > minReverseJumpM && speed > maxReverseMps &&
            Geo.angleDiff(Geo.bearingDeg(prev.first, prev.second, lat, lon), heading) > reverseAngleDeg

        if (forwardJump || reverseJump) {
            headingFrom = lat to lon
            headingDeg = null
            return true
        }
        updateHeading(lat, lon)
        return false
    }

    /** 15m 이상 움직였을 때만 진행 방향을 갱신한다. GPS가 10m쯤 흔들려도 방향이 뒤집히지 않게 한다. */
    private fun updateHeading(lat: Double, lon: Double) {
        val from = headingFrom ?: run { headingFrom = lat to lon; return }
        if (Geo.distanceM(from.first, from.second, lat, lon) < HEADING_MIN_MOVE_M) return
        headingDeg = Geo.bearingDeg(from.first, from.second, lat, lon)
        headingFrom = lat to lon
    }
}

private const val HEADING_MIN_MOVE_M = 15.0
