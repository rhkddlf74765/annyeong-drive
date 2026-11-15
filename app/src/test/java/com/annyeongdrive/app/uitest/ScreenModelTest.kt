package com.annyeongdrive.app.uitest

import com.annyeongdrive.app.R
import com.annyeongdrive.app.admin.ReturnScreenState
import com.annyeongdrive.app.drive.LearningAvailability
import com.annyeongdrive.app.learning.CORE_CATALOG
import com.annyeongdrive.app.learning.LearningPlan
import com.annyeongdrive.app.learning.LearningSituation
import com.annyeongdrive.app.learning.LearningView
import com.annyeongdrive.app.rental.AppLanguage
import com.annyeongdrive.app.setup.FuelGuidance
import com.annyeongdrive.app.setup.HiPassGuidance
import com.annyeongdrive.app.setup.PrecheckProgress
import com.annyeongdrive.app.setup.PrecheckView
import com.annyeongdrive.app.ui.admin.ReturnScreenModel
import com.annyeongdrive.app.ui.home.PARKING_WARNING_CONTENT
import com.annyeongdrive.app.ui.learning.LEARNING_START_CONTENT
import com.annyeongdrive.app.ui.learning.LearningAccessModel
import com.annyeongdrive.app.ui.learning.LearningScreenModel
import com.annyeongdrive.app.ui.setup.LANGUAGE_OPTIONS
import com.annyeongdrive.app.ui.setup.PrecheckScreenModel
import com.annyeongdrive.app.vehicle.FuelType
import com.annyeongdrive.app.vehicle.Seat
import com.annyeongdrive.app.vendor.HiPassAvailability
import com.annyeongdrive.app.vendor.ReturnTerms
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 화면 작업 안내서(11/15) 6장의 문구와 버튼 상태를 확인한다.
 * 운전 상태는 AppScreenTest에서 검증하며 이 테스트는 순수 화면 모델을 확인한다.
 */
class ScreenModelTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val plan = LearningPlan.of(
        CORE_CATALOG,
        LearningSituation(null, null, FuelType.UNKNOWN, HiPassAvailability.UNKNOWN),
    )

    /** 언어 버튼의 순서와 선택 값을 유지한다. */
    @Test
    fun `언어 버튼은 한국어와 영어를 고른 순서대로 넘긴다`() {
        // 상황 · 행동
        val labels = LANGUAGE_OPTIONS.map { it.language to it.label }

        // 결과
        assertEquals(
            listOf(AppLanguage.KOREAN to R.string.language_korean, AppLanguage.ENGLISH to R.string.language_english),
            labels,
        )
    }

    /** 신호에 경고가 없어도 수동 확인 상태를 그대로 그린다. */
    @Test
    fun `안전띠 경고가 없어도 수동 확인 안내를 유지한다`() {
        // 상황
        val view = PrecheckView(emptyList(), FuelGuidance.AskVendor, HiPassGuidance.AskVendor, ReturnTerms())
        val progress = PrecheckProgress(needsSeatBeltConfirmation = true)

        // 행동
        val screen = PrecheckScreenModel.of(view, progress, zone)

        // 결과
        assertTrue(screen.warningSeatLabels.isEmpty())
        assertFalse(screen.seatBeltsConfirmed)
        assertTrue(screen.showConfirmationPrompt)
        assertEquals(R.string.precheck_fuel_ask, screen.fuelMessage)
        assertEquals(R.string.precheck_hipass_ask, screen.hiPassMessage)
        assertEquals(null, screen.returnPlace)
        assertEquals(null, screen.returnDue)
        assertEquals(null, screen.returnEnergy)
    }

    /** 입력된 안내는 자원 ID와 업체 값으로 그대로 유지한다. */
    @Test
    fun `점검 화면은 경고 좌석과 업체 설정을 함께 보여 준다`() {
        // 상황
        val view = PrecheckView(
            listOf(Seat.DRIVER, Seat.REAR_RIGHT),
            FuelGuidance.Fuel(FuelType.DIESEL),
            HiPassGuidance.Available("Rental desk"),
            ReturnTerms(place = "Jeju Airport", dueAt = Instant.parse("2026-11-15T09:00:00Z"), energyRule = "Full"),
        )

        // 행동
        val screen = PrecheckScreenModel.of(view, PrecheckProgress(seatBeltConfirmed = true), zone)

        // 결과
        assertEquals(listOf(R.string.seat_driver, R.string.seat_rear_right), screen.warningSeatLabels)
        assertTrue(screen.seatBeltsConfirmed)
        assertFalse(screen.showConfirmationPrompt)
        assertEquals(R.string.precheck_fuel_body, screen.fuelMessage)
        assertEquals(R.string.fuel_diesel, screen.fuelLabel)
        assertEquals(R.string.precheck_hipass_available, screen.hiPassMessage)
        assertEquals("Rental desk", screen.hiPassSettlement)
        assertEquals("Jeju Airport", screen.returnPlace)
        assertEquals("2026-11-15 18:00", screen.returnDue)
        assertEquals("Full", screen.returnEnergy)
    }

    /** 주정차 경고와 교육 시작이 각각 기존 안내와 동작을 노출한다. */
    @Test
    fun `주정차 확인과 교육 시작은 기존 문구와 동작을 보여 준다`() {
        // 상황 · 행동
        val parking = PARKING_WARNING_CONTENT
        val start = LEARNING_START_CONTENT

        // 결과
        assertEquals(listOf(R.string.guide_parking_core, R.string.guide_parking_consequence), parking.body)
        assertEquals(R.string.parking_warning_acknowledge, parking.primaryAction)
        assertEquals(null, parking.secondaryAction)
        assertEquals(listOf(R.string.learning_start_body), start.body)
        assertEquals(R.string.action_learning_begin, start.primaryAction)
        assertEquals(R.string.action_later, start.secondaryAction)
    }

    /** 처음 카드와 마지막 카드의 버튼 상태를 유지한다. */
    @Test
    fun `첫 카드에는 이전 동작이 잠기고 끝 카드에는 퀴즈 시작이 보인다`() {
        // 상황
        val card = plan.cards.first()
        val total = plan.cards.size

        // 행동
        val first = LearningScreenModel.of(LearningView.Card(card, 1, total)) as LearningScreenModel.Card
        val last = LearningScreenModel.of(LearningView.Card(card, total, total)) as LearningScreenModel.Card

        // 결과
        assertFalse(first.hasPrevious)
        assertFalse(first.startsQuiz)
        assertTrue(last.hasPrevious)
        assertTrue(last.startsQuiz)
        assertEquals(card.texts.title.res, first.title)
    }

    /** 선택지 순서와 정답 확인 활성 상태를 유지한다. */
    @Test
    fun `퀴즈 정답 확인은 선택한 답이 있을 때만 활성화된다`() {
        // 상황
        val question = plan.questions.first()
        val view = LearningView.Question(question, 1, plan.questions.size, selected = null)

        // 행동
        val before = LearningScreenModel.of(view) as LearningScreenModel.Question
        val after = LearningScreenModel.of(view.copy(selected = 1)) as LearningScreenModel.Question

        // 결과
        assertEquals(question.texts.options.map { it.res }, before.options)
        assertEquals(null, before.selected)
        assertFalse(before.canCheck)
        assertEquals(1, after.selected)
        assertTrue(after.canCheck)
    }

    /** 해설과 결과의 콘텐츠를 기존 계획에서 가져온다. */
    @Test
    fun `해설은 기존 정답을 쓰고 복습은 응답 수와 규칙을 보여 준다`() {
        // 상황
        val question = plan.questions.first()
        val card = plan.cards.first()

        // 행동
        val explanation = LearningScreenModel.of(LearningView.Explanation(question, 1, 5, false))
            as LearningScreenModel.Explanation
        val result = LearningScreenModel.of(LearningView.Result(2, 5, listOf(card))) as LearningScreenModel.Result

        // 결과
        assertFalse(explanation.correct)
        assertEquals(question.texts.options[question.correctIndex].res, explanation.answer)
        assertEquals(question.texts.explanation.res, explanation.body)
        assertEquals(2, result.answered)
        assertEquals(5, result.total)
        assertEquals(card.texts.title.res, result.reviewCards.single().title)
        assertEquals(card.texts.body.res, result.reviewCards.single().body)
    }

    /** 정차 대기와 이어 보기에서 동일한 교육 버튼 조건과 안내를 쓴다. */
    @Test
    fun `교육 버튼은 열 수 있는 상태에서만 활성화된다`() {
        // 상황
        val availability = LearningAvailability.entries

        // 행동
        val models = availability.map(LearningAccessModel::of)

        // 결과
        assertEquals(listOf(true, false, false), models.map { it.canOpen })
        assertEquals(
            listOf(null, R.string.learning_unavailable_checking, R.string.learning_unavailable_restricted),
            models.map { it.message },
        )
    }

    /** 코드 입력을 표시하는 기존 상태와 잠금 안내를 유지한다. */
    @Test
    fun `반납 코드는 미설정과 설정 읽기 실패에서만 입력을 숨긴다`() {
        // 상황
        val states = listOf(
            ReturnScreenState.Entry,
            ReturnScreenState.WrongCode(2),
            ReturnScreenState.Locked(Instant.parse("2026-11-15T09:00:10Z")),
            ReturnScreenState.AdminCodeNotSet,
            ReturnScreenState.ConfigUnreadable,
        )

        // 행동
        val models = states.map { ReturnScreenModel.of(it, zone) }

        // 결과
        assertEquals(listOf(true, true, true, false, false), models.map { it.showCodeEntry })
        assertEquals(
            listOf(R.string.return_entry, R.string.code_wrong, R.string.code_locked,
                R.string.return_code_not_set, R.string.config_unreadable),
            models.map { it.message },
        )
        assertEquals(listOf(2), models[1].messageArgs)
        assertEquals(listOf("18:00:10"), models[2].messageArgs)
    }
}
