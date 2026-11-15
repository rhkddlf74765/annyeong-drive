package com.annyeongdrive.app.ui.learning

import com.annyeongdrive.app.R
import com.annyeongdrive.app.drive.LearningAvailability

/** 정차 대기·이어 보기에서 공통으로 표시하는 교육 버튼 상태와 안내 문구. */
internal data class LearningAccessModel(val canOpen: Boolean, val message: Int?) {
    companion object {
        /** 판단 로직이 반환한 교육 진입 상태의 화면 표현을 고른다. */
        fun of(availability: LearningAvailability): LearningAccessModel = when (availability) {
            LearningAvailability.AVAILABLE -> LearningAccessModel(true, null)
            LearningAvailability.CHECKING_VEHICLE -> LearningAccessModel(false, R.string.learning_unavailable_checking)
            LearningAvailability.RESTRICTED -> LearningAccessModel(false, R.string.learning_unavailable_restricted)
        }
    }
}
