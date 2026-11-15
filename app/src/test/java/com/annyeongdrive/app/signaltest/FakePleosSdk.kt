package com.annyeongdrive.app.signaltest

import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatBeltNotBuckledWarningListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import com.annyeongdrive.app.vehicle.Seat
import java.lang.reflect.Proxy
import com.annyeongdrive.app.vehicle.pleos.PleosSignalValues
import ai.pleos.playground.vehicle.api.Gear as PleosGear
import ai.pleos.playground.vehicle.api.Safety as PleosSafety
import ai.pleos.playground.vehicle.api.Seat as PleosSeat

/**
 * 앱이 쓰지 않는 SDK 기능을 모두 "지원하지 않음" 오류로 채운 창구를 만든다. 가짜 SDK가 필요한 기능만 직접 구현하고 나머지를 이것에 맡긴다.
 * 앱이 예상하지 않은 SDK 기능을 부르면 테스트가 바로 실패해 드러난다.
 *
 * @param T SDK 창구 인터페이스
 * @return 모든 기능이 오류를 내는 창구
 */
private inline fun <reified T : Any> unsupported(): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        throw UnsupportedOperationException("가짜 SDK가 지원하지 않는 기능: ${method.name}")
    } as T

/**
 * 가짜 Pleos SDK 기어 창구. 테스트가 SDK 값(VehicleGear)을 그대로 넣는다.
 * 구독한 콜백을 기억해, 실제 SDK처럼 값이 바뀌면 콜백을 부른다.
 */
class FakePleosGear : PleosGear by unsupported() {

    /** 현재 기어 읽기에 돌려줄 값. */
    var current: VehicleGear = VehicleGear.GEAR_PARK

    /** 현재 기어 읽기를 실패시킬 오류. null이면 성공한다. */
    var readFailure: Exception? = null

    private val listeners = mutableListOf<GearSelectionListener>()

    /** 지금 구독 중인 콜백 수. */
    val subscribers: Int
        get() = listeners.size

    /**
     * 현재 기어를 읽는다. [readFailure]가 있으면 실패를 알린다.
     *
     * @param onComplete 기어를 받을 함수
     * @param onFailed 오류를 받을 함수
     */
    override fun getCurrentGearSelectionState(onComplete: (VehicleGear?) -> Unit, onFailed: (Exception) -> Unit) {
        val failure = readFailure
        if (failure != null) {
            onFailed(failure)
            return
        }
        onComplete(current)
    }

    /**
     * 기어 콜백을 구독한다.
     *
     * @param listener 콜백
     */
    override fun registerGearSelection(listener: GearSelectionListener) {
        listeners += listener
    }

    /**
     * 기어 콜백 구독을 놓는다.
     *
     * @param listener 콜백
     */
    override fun unregisterGearSelection(listener: GearSelectionListener) {
        listeners -= listener
    }

    /**
     * SDK가 기어 변경을 알리는 것처럼 구독 중인 콜백을 부른다.
     *
     * @param gear SDK 기어 값. 받지 못한 값은 null
     */
    fun select(gear: VehicleGear?) {
        current = gear ?: VehicleGear.GEAR_UNKNOWN
        listeners.toList().forEach { it.onSelected(gear) }
    }

    /**
     * SDK가 구독 오류를 알리는 것처럼 구독 중인 콜백을 부른다.
     *
     * @param error 오류
     */
    fun fail(error: Exception) {
        listeners.toList().forEach { it.onFailed(error) }
    }
}

/**
 * 가짜 Pleos SDK 좌석 창구. 테스트가 SDK 착좌 값(Boolean, 표준과 반대: true = 비어 있음)을 좌석별로 넣는다.
 */
class FakePleosSeat : PleosSeat by unsupported() {

    private val current = Seat.entries.associateWith<Seat, Boolean?> { SDK_VACANT }.toMutableMap()
    private val listeners = mutableMapOf<Seat, MutableList<SeatOccupancyListener>>()

    /** 지금 구독 중인 콜백 수(모든 좌석). */
    val subscribers: Int
        get() = listeners.values.sumOf { it.size }

    /**
     * 좌석 하나의 현재 착좌 값을 읽는다. 모르면 null을 준다.
     *
     * @param area 좌석 위치
     * @param onComplete 착좌 값을 받을 함수
     * @param onFailed 오류를 받을 함수
     */
    override fun getCurrentSeatOccupancy(area: SeatArea, onComplete: (Boolean?) -> Unit, onFailed: (Exception) -> Unit) {
        onComplete(current[seatOf(area)])
    }

    /**
     * 좌석 하나의 착좌 콜백을 구독한다.
     *
     * @param area 좌석 위치
     * @param listener 콜백
     */
    override fun registerSeatOccupancy(area: SeatArea, listener: SeatOccupancyListener) {
        listeners.getOrPut(seatOf(area)) { mutableListOf() } += listener
    }

    /**
     * 좌석 하나의 착좌 콜백 구독을 놓는다.
     *
     * @param area 좌석 위치
     * @param listener 콜백
     */
    override fun unregisterSeatOccupancy(area: SeatArea, listener: SeatOccupancyListener) {
        listeners[seatOf(area)]?.remove(listener)
    }

    /**
     * 구독 전 읽을 현재 착좌 값을 정한다.
     *
     * @param seat 좌석
     * @param sdkValue SDK 착좌 값(true = 비어 있음, false = 앉음). 모르면 null
     */
    fun setCurrent(seat: Seat, sdkValue: Boolean?) {
        current[seat] = sdkValue
    }

    /**
     * SDK가 착좌 변경을 알리는 것처럼 그 좌석의 콜백을 부른다.
     *
     * @param seat 좌석
     * @param sdkValue SDK 착좌 값(true = 비어 있음, false = 앉음). 모르면 null
     */
    fun report(seat: Seat, sdkValue: Boolean?) {
        current[seat] = sdkValue
        listeners[seat].orEmpty().toList().forEach { it.onSeatOccupancyUpdated(sdkValue) }
    }

    /**
     * SDK가 그 좌석의 구독 오류를 알리는 것처럼 콜백을 부른다.
     *
     * @param seat 좌석
     * @param error 오류
     */
    fun fail(seat: Seat, error: Exception) {
        listeners[seat].orEmpty().toList().forEach { it.onFailed(error) }
    }

    companion object {
        /** SDK가 "비어 있음"으로 주는 값(표준 원값 1). */
        const val SDK_VACANT = true

        /** SDK가 "앉음"으로 주는 값(표준 원값 2). */
        const val SDK_OCCUPIED = false
    }
}

/** 가짜 Pleos SDK 안전 창구. 테스트가 좌석별 안전띠 미착용 경고 값(Boolean)을 넣는다. */
class FakePleosSafety : PleosSafety by unsupported() {

    private val listeners = mutableMapOf<Seat, MutableList<SeatBeltNotBuckledWarningListener>>()

    /** 지금 구독 중인 콜백 수(모든 좌석). */
    val subscribers: Int
        get() = listeners.values.sumOf { it.size }

    /**
     * 좌석 하나의 경고 콜백을 구독한다.
     *
     * @param area 좌석 위치
     * @param listener 콜백
     */
    override fun registerSeatBeltNotBuckledWarning(area: SeatArea, listener: SeatBeltNotBuckledWarningListener) {
        listeners.getOrPut(seatOf(area)) { mutableListOf() } += listener
    }

    /**
     * 좌석 하나의 경고 콜백 구독을 놓는다.
     *
     * @param area 좌석 위치
     * @param listener 콜백
     */
    override fun unregisterSeatBeltNotBuckledWarning(area: SeatArea, listener: SeatBeltNotBuckledWarningListener) {
        listeners[seatOf(area)]?.remove(listener)
    }

    /**
     * SDK가 경고 변경을 알리는 것처럼 그 좌석의 콜백을 부른다.
     *
     * @param seat 좌석
     * @param warning 경고가 있으면 true, 없으면 false. 모르면 null
     */
    fun report(seat: Seat, warning: Boolean?) {
        listeners[seat].orEmpty().toList().forEach { it.onSeatBeltNotBuckledWarningUpdated(warning) }
    }

    /**
     * SDK가 그 좌석의 구독 오류를 알리는 것처럼 콜백을 부른다.
     *
     * @param seat 좌석
     * @param error 오류
     */
    fun fail(seat: Seat, error: Exception) {
        listeners[seat].orEmpty().toList().forEach { it.onFailed(error) }
    }
}

/**
 * Pleos 좌석 위치를 앱 좌석으로 되돌린다. 가짜 SDK가 좌석별 콜백을 나눠 두는 데 쓴다.
 *
 * @param area Pleos 좌석 위치
 * @return 앱 좌석
 */
private fun seatOf(area: SeatArea): Seat {
    val value: SeatValue = area.values.first()
    return PleosSignalValues.SEAT_AREAS.entries.first { it.value == value }.key
}
