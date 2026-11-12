package com.annyeongdrive.app.storage

import com.annyeongdrive.app.drive.LearningResumePoint
import com.annyeongdrive.app.drive.LearningStatusReader
import com.annyeongdrive.app.drive.RentalStatusReader
import com.annyeongdrive.app.learning.QuizOrder
import com.annyeongdrive.app.rental.LearningProgress
import com.annyeongdrive.app.rental.LearningScreen
import com.annyeongdrive.app.rental.RentalSession
import com.annyeongdrive.app.time.Clock
import com.annyeongdrive.app.vendor.VendorConfig
import kotlinx.coroutines.flow.StateFlow

/**
 * 저장소의 최신 대여·업체 설정으로 [RentalStatusReader]를 구현한다. 판단은 Aggregate에 맡기고 여기서는 읽기만 한다.
 *
 * @param session 최신 대여(DriverSessionRepository.session)
 * @param vendor 최신 업체 설정(VendorConfigRepository.config)
 * @param clock 지금 시각
 */
class StoredRentalStatusReader(
    private val session: StateFlow<RentalSession?>,
    private val vendor: StateFlow<VendorConfig>,
    private val clock: Clock,
) : RentalStatusReader {

    /**
     * 대여 설정을 마친 대여가 있는지 알려 준다.
     * 상태 머신: T2, T3 가드
     *
     * @return 대여가 있고 출발 전 점검을 마쳤으면 true
     */
    override fun hasRentalSetup(): Boolean {
        val current = session.value ?: return false
        return current.hasRentalSetup()
    }

    /**
     * 반납 예정 시각 + N시간이 지났는지 알려 준다.
     * 상태 머신: T4 가드
     *
     * @return 예비 초기화 시각이 지났으면 true
     */
    override fun isResetOverdue(): Boolean = vendor.value.isResetOverdue(clock.now())
}

/**
 * 저장소의 최신 대여와 문항 순서로 [LearningStatusReader]를 구현한다. 대여가 없으면 시작 전으로 본다.
 *
 * @param session 최신 대여(DriverSessionRepository.session)
 * @param order 핵심 퀴즈 문항 순서(6단계 교육 콘텐츠가 준다)
 */
class StoredLearningStatusReader(
    private val session: StateFlow<RentalSession?>,
    private val order: QuizOrder,
) : LearningStatusReader {

    /**
     * 교육을 시작한 적이 있는지 알려 준다.
     * 상태 머신: B1 가드
     *
     * @return 시작했으면 true
     */
    override fun hasStarted(): Boolean {
        val learning = currentLearning() ?: return false
        return learning.begun
    }

    /**
     * 시작했지만 끝내지 않은 교육이 있는지 알려 준다.
     * 상태 머신: T15 가드
     *
     * @return 미완료 교육이 있으면 true
     */
    override fun hasUnfinished(): Boolean {
        val learning = currentLearning() ?: return false
        return learning.hasUnfinished(order.questionIds())
    }

    /**
     * 지금 문제가 마지막 문항인지 알려 준다.
     * 상태 머신: T29, T30, B6 가드
     *
     * @return 마지막 문항이면 true
     */
    override fun isLastQuestion(): Boolean {
        val learning = currentLearning() ?: return false
        return learning.isLastQuestion(order.questionIds())
    }

    /**
     * 이어 보기로 들어갈 위치를 알려 준다. 문제를 보고 있었으면 문제, 그 밖에는 학습 카드다.
     * 상태 머신: B2
     *
     * @return 저장된 위치
     */
    override fun resumePoint(): LearningResumePoint {
        val screen = currentLearning()?.attempt?.position?.screen
        return when (screen) {
            LearningScreen.QUIZ -> LearningResumePoint.QUIZ
            LearningScreen.CARD -> LearningResumePoint.CARD
            null -> LearningResumePoint.CARD
        }
    }

    /**
     * 최신 대여의 교육 진행을 돌려준다.
     *
     * @return 교육 진행. 대여가 없으면 null
     */
    private fun currentLearning(): LearningProgress? = session.value?.learning
}
