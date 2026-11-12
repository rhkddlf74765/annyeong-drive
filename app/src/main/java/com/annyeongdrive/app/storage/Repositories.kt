package com.annyeongdrive.app.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.annyeongdrive.app.rental.RentalSession
import com.annyeongdrive.app.vendor.VendorConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/** 파일 하나에 JSON 문서 하나를 넣는 키. */
private val DOCUMENT_KEY = stringPreferencesKey("document")

/**
 * 운전자 데이터(driver_session) 저장소. RentalSession 하나를 저장하고, 최신 값을 [session]으로 내보낸다.
 * 답하거나 건너뛸 때마다 [update]로 즉시 저장한다(결정 문서 4장). 초기화는 이 파일만 비운다.
 *
 * @param dataStore driver_session 파일의 DataStore(앱 전체에서 하나)
 * @param contentVersion 지금 앱의 교육 문구 버전. 다르면 교육 진행만 새로 시작한다
 */
class DriverSessionRepository(
    private val dataStore: DataStore<Preferences>,
    private val contentVersion: String,
) {

    private val codec = DocumentCodec.forRentalSession()
    private val latest = MutableStateFlow<RentalSession?>(null)

    /** 최신 대여. 없으면 null. Reader가 이 값을 읽는다. */
    val session: StateFlow<RentalSession?> = latest.asStateFlow()

    /**
     * 파일에서 대여를 읽어 [session]에 올린다. 앱 시작 때 AppStarted 이벤트 전에 부른다.
     * 교육 문구 버전이 다르면 교육 진행만 새로 시작한 값으로 맞춘다.
     *
     * @return 읽은 대여. 없거나 읽을 수 없으면 null
     */
    suspend fun load(): RentalSession? {
        val stored = dataStore.data.first()[DOCUMENT_KEY]
        val decoded = stored?.let(codec::decode)
        val aligned = decoded?.alignContentVersion(contentVersion)
        latest.value = aligned
        return aligned
    }

    /**
     * 대여를 바꿔 즉시 저장한다.
     *
     * @param change 지금 대여(없으면 null)를 받아 새 대여를 돌려주는 함수. null을 돌려주면 지운다
     * @return 저장한 대여
     */
    suspend fun update(change: (RentalSession?) -> RentalSession?): RentalSession? {
        val next = change(latest.value)
        dataStore.edit { prefs -> writeTo(prefs, next) }
        latest.value = next
        return next
    }

    /**
     * 운전자 데이터를 지운다. 반납 처리·예비 초기화의 할 일(ResetDriverData)을 실행할 때 부른다.
     * 상태 머신: T5
     */
    suspend fun clear() {
        update { null }
    }

    /**
     * 대여를 저장 항목에 쓰거나 지운다.
     *
     * @param prefs 고칠 저장 항목
     * @param session 쓸 대여. null이면 지운다
     */
    private fun writeTo(prefs: MutablePreferences, session: RentalSession?) {
        if (session == null) {
            prefs.remove(DOCUMENT_KEY)
            return
        }
        prefs[DOCUMENT_KEY] = codec.encode(session)
    }
}

/**
 * 업체 데이터(vendor_config) 저장소. VendorConfig 하나를 저장하고, 최신 값을 [config]로 내보낸다.
 * 운전자 데이터 초기화와 상관없이 남는다.
 *
 * @param dataStore vendor_config 파일의 DataStore(앱 전체에서 하나)
 */
class VendorConfigRepository(
    private val dataStore: DataStore<Preferences>,
) {

    private val codec = DocumentCodec.forVendorConfig()
    private val latest = MutableStateFlow(VendorConfig.initial())

    /** 최신 업체 설정. 저장된 것이 없으면 처음 설치 기본값이다. */
    val config: StateFlow<VendorConfig> = latest.asStateFlow()

    /**
     * 파일에서 업체 설정을 읽어 [config]에 올린다. 없으면 처음 설치 기본값을 둔다.
     *
     * @return 읽은 업체 설정
     */
    suspend fun load(): VendorConfig {
        val stored = dataStore.data.first()[DOCUMENT_KEY]
        val decoded = stored?.let(codec::decode)
        val loaded = decoded ?: VendorConfig.initial()
        latest.value = loaded
        return loaded
    }

    /**
     * 업체 설정을 바꿔 즉시 저장한다. 관리자 코드 확인 결과(실패 횟수·잠금)도 이것으로 저장한다.
     *
     * @param change 지금 업체 설정을 받아 새 업체 설정을 돌려주는 함수
     * @return 저장한 업체 설정
     */
    suspend fun update(change: (VendorConfig) -> VendorConfig): VendorConfig {
        val next = change(latest.value)
        dataStore.edit { prefs -> prefs[DOCUMENT_KEY] = codec.encode(next) }
        latest.value = next
        return next
    }
}
