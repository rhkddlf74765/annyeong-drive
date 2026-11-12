package com.annyeongdrive.app.storage

import com.annyeongdrive.app.rental.AppLanguage
import com.annyeongdrive.app.rental.RentalSession
import com.annyeongdrive.app.rental.RentalStartRequest
import java.time.Instant

/** 저장 테스트에서 함께 쓰는 값. */
object StorageFixtures {

    /** 교육 문구 버전. */
    const val CONTENT_VERSION = "2026-11-10"

    /** 바뀐 교육 문구 버전. */
    const val NEW_CONTENT_VERSION = "2026-11-20"

    /** 관리자 코드. */
    const val ADMIN_CODE = "Rent2026"

    /** 고정 salt. */
    val SALT = ByteArray(16) { it.toByte() }

    /** 기준 시각. */
    val NOW: Instant = Instant.parse("2026-11-12T09:00:00Z")

    /** 핵심 퀴즈 가짜 순서(실제 순서는 6단계 교육 콘텐츠가 준다). */
    val QUIZ_IDS = listOf("quiz.lane_direction", "quiz.school_zone_stop", "quiz.roundabout_yield")

    /**
     * 막 시작한 대여를 만든다.
     *
     * @return 대여 설정 전의 대여
     */
    fun startedSession(): RentalSession = RentalSession.start(
        RentalStartRequest("rental-0001", NOW, AppLanguage.ENGLISH, CONTENT_VERSION),
    )
}
