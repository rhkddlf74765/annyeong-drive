package com.annyeongdrive.app.location

import ai.pleos.playground.location.fused.model.LocationData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 서비스가 받은 최신 위치를 화면 등 다른 곳에서 볼 수 있게 보관한다. */
object LocationState {
    private val _latest = MutableStateFlow<LocationData?>(null)
    val latest: StateFlow<LocationData?> = _latest

    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _events = MutableStateFlow<List<String>>(emptyList())
    /** 최근 구역 사건(최신이 위). 개발 확인용. */
    val events: StateFlow<List<String>> = _events

    fun addEvent(text: String) {
        _events.value = (listOf(text) + _events.value).take(MAX_EVENTS)
    }

    fun update(data: LocationData) {
        _latest.value = data
        _count.value += 1
        _error.value = null
    }

    fun fail(message: String) {
        _error.value = message
    }

    private const val MAX_EVENTS = 8
}
