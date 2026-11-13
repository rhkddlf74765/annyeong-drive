package com.annyeongdrive.app.setup

import com.annyeongdrive.app.rental.AppLanguage
import com.annyeongdrive.app.rental.RentalStartRequest
import com.annyeongdrive.app.time.Clock
import java.util.UUID

/** 새 대여의 식별자를 만드는 창구. 테스트에서는 고정 값을 넣는다. */
fun interface RentalIdSource {

    /**
     * 새 대여 식별자를 만든다.
     *
     * @return 식별자
     */
    fun next(): String
}

/** 무작위 UUID로 대여 식별자를 만드는 [RentalIdSource]. */
class UuidRentalIdSource : RentalIdSource {

    /**
     * 무작위 UUID 문자열을 만든다.
     *
     * @return 식별자
     */
    override fun next(): String = UUID.randomUUID().toString()
}

/** 언어를 고른 순간 새 대여를 시작할 값(식별자·시각·문구 버전)을 만든다. */
fun interface RentalStarts {

    /**
     * 고른 언어로 대여 시작 값을 만든다.
     *
     * @param language 고른 언어
     * @return 대여 시작 값
     */
    fun request(language: AppLanguage): RentalStartRequest
}

/**
 * 시계·식별자·교육 문구 버전으로 대여 시작 값을 만드는 [RentalStarts].
 *
 * @param clock 지금 시각
 * @param ids 대여 식별자
 * @param contentVersion 지금 앱의 교육 문구 버전
 */
class DefaultRentalStarts(
    private val clock: Clock,
    private val ids: RentalIdSource,
    private val contentVersion: String,
) : RentalStarts {

    /**
     * 고른 언어로 대여 시작 값을 만든다.
     *
     * @param language 고른 언어
     * @return 대여 시작 값
     */
    override fun request(language: AppLanguage): RentalStartRequest =
        RentalStartRequest(ids.next(), clock.now(), language, contentVersion)
}
