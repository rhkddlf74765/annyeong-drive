package com.annyeongdrive.app.ui

/** 제목, 안내, 동작이 고정된 화면의 문구 ID. */
internal data class ActionScreenModel(
    val title: Int,
    val body: List<Int>,
    val primaryAction: Int,
    val secondaryAction: Int? = null,
)
