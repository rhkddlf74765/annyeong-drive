package com.annyeongdrive.app

import android.app.Application
import com.annyeongdrive.app.service.DriveRuntime

/**
 * 앱 프로세스 하나에 하나 있는 Application. 조립 지점([DriveRuntime])을 한 번 만들어 갖는다.
 * object 싱글턴 대신 여기에 두어, 서비스와 화면이 같은 Coordinator·저장소를 보게 한다.
 */
class AnnyeongApplication : Application() {

    /** 앱 전체에서 함께 쓰는 저장소·엔진 묶음. */
    lateinit var runtime: DriveRuntime
        private set

    /** 조립 지점을 만든다. */
    override fun onCreate() {
        super.onCreate()
        runtime = DriveRuntime(this)
    }
}
