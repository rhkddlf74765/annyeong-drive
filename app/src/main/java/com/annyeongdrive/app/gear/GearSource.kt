package com.annyeongdrive.app.gear

import com.annyeongdrive.app.vehicle.SignalSource

/**
 * 기어 단 신호를 받아 그대로 알려 주는 입력 어댑터.
 * 지금은 [FakeGearSource]를 쓰고, 기어 작업이 끝나면 같은 인터페이스의 CarGearSource로 바꾼다.
 * 이 파일의 위치(gear/GearSource.kt)는 기어 작업과 맞추기 위해 고정한다.
 */
interface GearSource : SignalSource<Gear>
