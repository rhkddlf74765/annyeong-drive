package com.annyeongdrive.app.service

/** 교육·퀴즈 화면을 닫는 출력 창구. */
fun interface LearningScreenPort {

    /** 교육·퀴즈 화면을 팝업 없이 닫는다. */
    fun close()
}

/** 학습 음성을 멈추는 출력 창구. */
fun interface LearningSpeechPort {

    /** 학습 음성을 멈춘다. */
    fun stop()
}

/** 운전자 데이터를 지우는 출력 창구. */
fun interface DriverDataEraser {

    /** 운전자 데이터를 지운다. 부르는 순간 메모리에서 먼저 사라져야 한다. */
    fun erase()
}

/** 운전자에게 알림(음성 + heads-up)으로 안내하는 출력 창구. */
interface DriveNotifierPort {

    /**
     * 검수된 주정차 구역에서 확인을 요청하는 경고를 낸다.
     *
     * @param zoneId 검수된 구역 식별자
     */
    fun announceParkingWarning(zoneId: String)

    /** 오주유 방지 확인 안내를 낸다. */
    fun announceFuelCheck()
}

/**
 * 할 일 실행기가 쓰는 출력 창구 묶음. 네 창구가 조립 지점에서 실행기로 함께 넘어가서 묶었다(코딩 규칙 5).
 *
 * @property learningScreen 교육 화면 닫기
 * @property learningSpeech 학습 음성 중단
 * @property driverData 운전자 데이터 지우기
 * @property notifier 주정차·오주유 안내
 */
data class DriveOutputPorts(
    val learningScreen: LearningScreenPort,
    val learningSpeech: LearningSpeechPort,
    val driverData: DriverDataEraser,
    val notifier: DriveNotifierPort,
)
