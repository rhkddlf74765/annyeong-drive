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

    fun update(data: LocationData) {
        _latest.value = data
        _count.value += 1
        _error.value = null
    }

    fun fail(message: String) {
        _error.value = message
    }
}
