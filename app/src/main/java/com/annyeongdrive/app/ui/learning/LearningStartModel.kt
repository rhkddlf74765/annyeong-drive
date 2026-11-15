package com.annyeongdrive.app.ui.learning

import com.annyeongdrive.app.R
import com.annyeongdrive.app.ui.ActionScreenModel

/** 상태 머신 T25·T23의 교육 시작 안내와 동작. */
internal val LEARNING_START_CONTENT = ActionScreenModel(
    title = R.string.learning_start_title,
    body = listOf(R.string.learning_start_body),
    primaryAction = R.string.action_learning_begin,
    secondaryAction = R.string.action_later,
)
