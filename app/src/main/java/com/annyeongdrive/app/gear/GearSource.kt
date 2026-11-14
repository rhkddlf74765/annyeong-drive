package com.annyeongdrive.app.gear

import com.annyeongdrive.app.vehicle.SignalSource

/**
 * 기어 단 신호를 받아 그대로 알려 주는 입력 어댑터.
 * debug는 [FakeGearSource](확인용 화면 버튼), release는 [CarGearSource](Pleos Vehicle SDK 실제 기어)를 쓴다.
 * 이 파일의 위치(gear/GearSource.kt)는 기어 작업과 맞추기 위해 고정한다.
 */
interface GearSource : SignalSource<Gear>
