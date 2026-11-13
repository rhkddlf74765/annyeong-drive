package com.annyeongdrive.app.service

import com.annyeongdrive.app.drive.DriveEvent
import com.annyeongdrive.app.storage.DriverSessionRepository
import com.annyeongdrive.app.storage.VendorConfigRepository

/**
 * 앱이 시작될 때의 순서를 지킨다: 저장소 읽기 → 앱 시작(AppStarted) → 입력 신호 구독.
 * 저장소를 읽기 전에 신호를 받으면 Reader가 빈 값을 보고 잘못 판단하므로(예: 대여가 있는데 T2로 감) 순서가 중요하다.
 * 저장소 읽기와 앱 시작은 프로세스마다 한 번만 하고, 신호 구독은 서비스가 다시 만들어질 때마다 한다.
 * 상태 머신: T4(예비 초기화) → T5
 *
 * @param driverSessions 운전자 데이터 저장소
 * @param vendorConfigs 업체 데이터 저장소
 * @param engine 입력 → Coordinator → 출력 연결
 */
class DriveStartup(
    private val driverSessions: DriverSessionRepository,
    private val vendorConfigs: VendorConfigRepository,
    private val engine: DriveEngine,
) {

    private var loaded = false

    /** 저장소를 읽고 앱 시작을 처리한 뒤 신호 구독을 시작한다. 여러 번 불러도 읽기와 앱 시작은 한 번만 한다. */
    suspend fun start() {
        loadOnce()
        engine.start()
    }

    /** 신호 구독을 멈춘다. 다시 [start]하면 읽기 없이 구독만 다시 시작한다. */
    fun stop() = engine.stop()

    /** 처음 한 번만 저장소를 읽고 앱 시작 이벤트를 보낸다. 예비 초기화 시각이 지났으면 여기서 운전자 데이터가 지워진다. */
    private suspend fun loadOnce() {
        if (loaded) return
        driverSessions.load()
        vendorConfigs.load()
        engine.send(DriveEvent.AppStarted)
        loaded = true
    }
}
