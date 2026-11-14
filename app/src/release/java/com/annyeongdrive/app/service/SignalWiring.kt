package com.annyeongdrive.app.service

import android.content.Context
import com.annyeongdrive.app.gear.CarGearSource
import com.annyeongdrive.app.vehicle.car.CarSpeedSource
import com.annyeongdrive.app.vehicle.car.CarUxRestrictionSource
import com.annyeongdrive.app.vehicle.pleos.PleosDriverSeatSource
import com.annyeongdrive.app.vehicle.pleos.PleosSeatBeltSource

/**
 * release 빌드의 입력 어댑터 묶음. 기어·착좌·안전띠는 Pleos Vehicle SDK, 차속·화면 제한은 Android 차량 라이브러리의 실제 신호를 쓴다.
 * 위치는 조립 지점(DriveRuntime)이 FusedLocation으로 바꾼다. 가짜 묶음에서는 위치 자리만 남는다.
 * debug 빌드는 같은 이름의 함수가 가짜 한 벌을 그대로 돌려준다(확인용 화면의 버튼으로 조작).
 *
 * @param context 앱 Context
 * @param fakes 가짜 입력 어댑터 한 벌(위치 자리만 쓴다)
 * @return 엔진이 구독할 입력 어댑터 묶음
 */
internal fun driveSignalSources(context: Context, fakes: FakeDriveSignals): DriveSignalSources =
    fakes.asSources().copy(
        gear = CarGearSource(context),
        speed = CarSpeedSource(context),
        ux = CarUxRestrictionSource(context),
        driverSeat = PleosDriverSeatSource(context),
        seatBelts = PleosSeatBeltSource(context),
    )
