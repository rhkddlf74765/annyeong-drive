package com.annyeongdrive.app.service

/** 출력 창구가 몇 번, 무엇으로 불렸는지 기록하는 테스트용 가짜 묶음. */
class RecordingPorts {

    /** 교육 화면 닫기 횟수. */
    var screenClosed = 0
        private set

    /** 학습 음성 중단 횟수. */
    var speechStopped = 0
        private set

    /** 운전자 데이터 지우기 횟수. */
    var driverDataErased = 0
        private set

    /** 주정차 경고에 넘어온 구역 식별자. */
    val parkingWarnings = mutableListOf<String>()

    /** 오주유 안내 횟수. */
    var fuelChecks = 0
        private set

    /**
     * 기록하는 창구 묶음을 만든다.
     *
     * @return 실행기에 넘길 출력 창구 묶음
     */
    fun asPorts(): DriveOutputPorts = DriveOutputPorts(
        learningScreen = { screenClosed += 1 },
        learningSpeech = { speechStopped += 1 },
        driverData = { driverDataErased += 1 },
        notifier = object : DriveNotifierPort {
            /**
             * 주정차 경고 구역을 기록한다.
             *
             * @param zoneId 구역 식별자
             */
            override fun announceParkingWarning(zoneId: String) {
                parkingWarnings += zoneId
            }

            /** 오주유 안내 횟수를 센다. */
            override fun announceFuelCheck() {
                fuelChecks += 1
            }
        },
    )

    /**
     * 어떤 창구도 불리지 않았는지 알려 준다.
     *
     * @return 아무것도 불리지 않았으면 true
     */
    fun nothingCalled(): Boolean =
        screenClosed == 0 && speechStopped == 0 && driverDataErased == 0 && parkingWarnings.isEmpty() && fuelChecks == 0
}
