package com.annyeongdrive.app.ui.home

import com.annyeongdrive.app.R
import com.annyeongdrive.app.ui.ActionScreenModel

/** 상태 머신 T13·T17의 안내 제목, 알림 문구 및 확인 동작. */
internal val PARKING_WARNING_CONTENT = ActionScreenModel(
    title = R.string.guide_channel_parking,
    body = listOf(R.string.guide_parking_core, R.string.guide_parking_consequence),
    primaryAction = R.string.parking_warning_acknowledge,
)
