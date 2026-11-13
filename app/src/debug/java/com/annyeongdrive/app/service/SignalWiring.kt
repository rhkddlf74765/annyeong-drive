package com.annyeongdrive.app.service

import android.content.Context

/**
 * debug 빌드의 입력 어댑터 묶음. 모든 신호를 가짜로 두어 확인용 화면의 버튼으로 바꾼다.
 * release 빌드는 같은 이름의 함수가 화면 제한만 실제 차량 신호로 바꾼다.
 *
 * @param context 앱 Context(debug에서는 쓰지 않는다)
 * @param fakes 가짜 입력 어댑터 한 벌
 * @return 엔진이 구독할 입력 어댑터 묶음
 */
@Suppress("UNUSED_PARAMETER")
internal fun driveSignalSources(context: Context, fakes: FakeDriveSignals): DriveSignalSources = fakes.asSources()
