package com.annyeongdrive.app.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * 운전자 데이터 파일. 반납·예비 초기화 때 이 파일만 비운다.
 * 파일이 깨지면 빈 내용으로 바꾼다. 운전자 데이터는 다시 만들 수 있고, 깨진 파일 때문에 저장이 막히면 안 된다.
 */
private val Context.driverSessionStore: DataStore<Preferences> by preferencesDataStore(
    name = "driver_session",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * 업체 데이터 파일. 초기화하지 않는다.
 * 파일이 깨져도 자동으로 비우지 않는다. 비우면 관리자 코드가 사라져 누구나 새 코드를 정할 수 있게 되기 때문이다.
 * 대신 저장소가 "읽지 못함"으로 표시하고 업체 설정 진입과 반납 확인을 막는다.
 */
private val Context.vendorConfigStore: DataStore<Preferences> by preferencesDataStore(name = "vendor_config")

/**
 * 앱 전체에서 파일마다 DataStore 인스턴스 하나만 쓰게 한다(결정 문서 4장).
 * 서비스와 화면이 각자 열면 예외로 앱이 죽기 때문에, 반드시 여기서 받는다.
 */
object AppDataStores {

    /**
     * 운전자 데이터(driver_session) DataStore를 돌려준다.
     *
     * @param context 아무 Context. 내부에서 applicationContext를 쓴다
     * @return 앱 전체에서 하나인 DataStore
     */
    fun driverSession(context: Context): DataStore<Preferences> = context.applicationContext.driverSessionStore

    /**
     * 업체 데이터(vendor_config) DataStore를 돌려준다.
     *
     * @param context 아무 Context. 내부에서 applicationContext를 쓴다
     * @return 앱 전체에서 하나인 DataStore
     */
    fun vendorConfig(context: Context): DataStore<Preferences> = context.applicationContext.vendorConfigStore
}
