package com.annyeongdrive.app.service

import android.content.Context
import com.annyeongdrive.app.vehicle.SeatOccupancy
import com.annyeongdrive.app.vehicle.car.CarUxRestrictionSource
import com.annyeongdrive.app.vehicle.fake.FakeDriverSeatSource

/**
 * release 빌드의 입력 어댑터 묶음. 화면 제한은 실제 차량 신호를 쓰고, 나머지는 실제 어댑터가 생길 때까지 가짜를 쓴다.
 * 운전석 착좌 신호는 아직 확인하지 못해(결정 문서 7장) 앱을 켠 사람이 운전석에 앉아 있다고 본다.
 * 그렇지 않으면 release에는 착좌를 바꿀 방법이 없어 대기 화면에서 넘어가지 못한다(11/14 에뮬레이터 확인).
 * debug 빌드는 같은 이름의 함수가 가짜 한 벌을 그대로 돌려준다(확인용 화면의 버튼으로 조작).
 *
 * @param context 앱 Context
 * @param fakes 가짜 입력 어댑터 한 벌
 * @return 엔진이 구독할 입력 어댑터 묶음
 */
internal fun driveSignalSources(context: Context, fakes: FakeDriveSignals): DriveSignalSources =
    fakes.asSources().copy(
        ux = CarUxRestrictionSource(context),
        driverSeat = FakeDriverSeatSource(SeatOccupancy.OCCUPIED),
    )
