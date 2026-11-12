package com.annyeongdrive.app.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/** 운전자 데이터 파일. 반납·예비 초기화 때 이 파일만 비운다. */
private val Context.driverSessionStore: DataStore<Preferences> by preferencesDataStore(name = "driver_session")

/** 업체 데이터 파일. 초기화하지 않는다. */
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
