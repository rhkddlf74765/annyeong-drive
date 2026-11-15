package com.annyeongdrive.app.uitest

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * 화면 코드의 경계: ui 패키지는 차량 신호·어댑터·판단 핵심·서비스를 직접 가져다 쓰지 않는다. 운전 상태는 화면 고르기(AppScreen)만 읽는다.
 * 화면 작업을 AI에게 맡겨도 화면 안에 판단(기어·차속을 보고 닫기 등)이 들어가면 UI Test 워크플로가 실패해 드러난다.
 * 근거: CLAUDE.md 아키텍처(판단은 DriveCoordinator와 도메인 객체에, 화면은 Coordinator가 내보낸 상태만 읽는다),
 * 노션 "화면 작업 안내서 (11/15)" 8장 구조 규칙.
 */
class UiLayerBoundaryTest {

    private val uiFiles = File("src/main/java/com/annyeongdrive/app/ui").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /** 화면 코드가 차량 신호·어댑터(기어·차속·착좌·Pleos SDK·android.car·위치)를 직접 가져다 쓰지 않는지 확인한다. */
    @Test
    fun `화면 코드는 차량 신호와 어댑터를 직접 가져다 쓰지 않는다`() {
        // 상황 · 행동
        val found = importsStartingWith(SIGNAL_PACKAGES)

        // 결과
        assertEquals(emptyList<String>(), found)
    }

    /** 화면 코드가 판단 핵심(Coordinator·차량 스냅샷)과 서비스를 직접 가져다 쓰지 않는지 확인한다. */
    @Test
    fun `화면 코드는 판단 핵심과 서비스를 직접 가져다 쓰지 않는다`() {
        // 상황 · 행동
        val found = importsStartingWith(DECISION_PACKAGES)

        // 결과
        assertEquals(emptyList<String>(), found)
    }

    /** 운전 상태(drive.state)는 화면 고르기 파일(AppScreen.kt)에서만 읽는지 확인한다. */
    @Test
    fun `운전 상태는 화면 고르기에서만 읽는다`() {
        // 상황 · 행동
        val found = importsStartingWith(listOf(STATE_PACKAGE)).filterNot { it.startsWith("AppScreen.kt:") }

        // 결과
        assertEquals(emptyList<String>(), found)
    }

    /**
     * 주어진 패키지로 시작하는 import 줄을 화면 코드 전체에서 찾는다.
     *
     * @param prefixes 찾을 패키지 앞부분 목록
     * @return "파일 이름: import 줄" 목록
     */
    private fun importsStartingWith(prefixes: List<String>): List<String> = uiFiles.flatMap { file ->
        file.readLines()
            .map(String::trim)
            .filter { line -> prefixes.any { line.startsWith("import $it") } }
            .map { "${file.name}: $it" }
    }

    private companion object {
        /** 차량 신호·어댑터 패키지. */
        val SIGNAL_PACKAGES = listOf(
            "com.annyeongdrive.app.gear.",
            "com.annyeongdrive.app.vehicle.pleos.",
            "com.annyeongdrive.app.vehicle.car.",
            "com.annyeongdrive.app.vehicle.fake.",
            "com.annyeongdrive.app.vehicle.UxRestriction",
            "com.annyeongdrive.app.vehicle.SeatOccupancy",
            "com.annyeongdrive.app.vehicle.SpeedSource",
            "com.annyeongdrive.app.location.",
            "ai.pleos.",
            "android.car.",
        )

        /** 판단 핵심과 서비스 패키지. */
        val DECISION_PACKAGES = listOf(
            "com.annyeongdrive.app.drive.DriveCoordinator",
            "com.annyeongdrive.app.drive.VehicleSnapshot",
            "com.annyeongdrive.app.drive.DriveContext",
            "com.annyeongdrive.app.service.",
        )

        /** 운전 상태 패키지. */
        const val STATE_PACKAGE = "com.annyeongdrive.app.drive.state."
    }
}
