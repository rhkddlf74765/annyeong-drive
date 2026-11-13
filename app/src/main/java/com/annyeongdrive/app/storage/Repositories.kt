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
import java.io.IOException

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
        val stored = readStoredText()
        val decoded = stored?.let(codec::decode)
        val aligned = decoded?.alignContentVersion(contentVersion)
        latest.value = aligned
        return aligned
    }

    /**
     * 저장 파일의 JSON 문자열을 읽는다. 파일이 깨져 읽지 못하면 운전자 데이터가 없는 것으로 본다.
     * 운전자 데이터는 다시 만들 수 있고, 읽지 못한 기록을 남겨 두는 것보다 지우는 편이 개인정보 원칙에 맞다.
     *
     * @return JSON 문자열. 없거나 읽지 못하면 null
     */
    private suspend fun readStoredText(): String? = try {
        dataStore.data.first()[DOCUMENT_KEY]
    } catch (e: IOException) {
        null
    }

    /**
     * 대여를 바꿔 즉시 저장한다. 메모리([session])를 먼저 바꾸고 파일에 쓴다.
     * 그래서 파일 쓰기가 끝나기 전에도 Coordinator는 바뀐 값을 읽는다(반납 직후 옛 대여를 읽지 않게).
     *
     * @param change 지금 대여(없으면 null)를 받아 새 대여를 돌려주는 함수. null을 돌려주면 지운다
     * @return 저장한 대여
     */
    suspend fun update(change: (RentalSession?) -> RentalSession?): RentalSession? {
        val next = change(latest.value)
        latest.value = next
        dataStore.edit { prefs -> writeTo(prefs, next) }
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

/** 업체 설정 파일을 읽은 결과. */
enum class VendorLoadState {
    /** 아직 읽지 않았다. */
    NOT_LOADED,

    /** 저장된 업체 설정이 없다(처음 설치). 처음 설치 기본값을 쓴다. */
    NOT_SAVED,

    /** 저장된 업체 설정을 읽었다. */
    LOADED,

    /**
     * 파일이 있는데 읽지 못했다(파일 손상, 다른 저장 구조 버전, 깨진 내용).
     * 관리자 코드 해시도 알 수 없으므로 업체 설정 진입과 반납 확인을 막고, 파일을 덮어쓰지 않는다.
     */
    UNREADABLE,
}

/**
 * 업체 데이터(vendor_config) 저장소. VendorConfig 하나를 저장하고, 최신 값을 [config]로 내보낸다.
 * 운전자 데이터 초기화와 상관없이 남는다. 읽지 못한 파일은 덮어쓰지 않는다(업체가 복구할 수 있게).
 *
 * @param dataStore vendor_config 파일의 DataStore(앱 전체에서 하나)
 */
class VendorConfigRepository(
    private val dataStore: DataStore<Preferences>,
) {

    private val codec = DocumentCodec.forVendorConfig()
    private val latest = MutableStateFlow(VendorConfig.initial())
    private val state = MutableStateFlow(VendorLoadState.NOT_LOADED)

    /** 최신 업체 설정. 저장된 것이 없거나 읽지 못했으면 처음 설치 기본값이다. */
    val config: StateFlow<VendorConfig> = latest.asStateFlow()

    /** 업체 설정 파일을 읽은 결과. 읽지 못했으면 업체 설정 진입과 반납 확인을 막는다. */
    val loadState: StateFlow<VendorLoadState> = state.asStateFlow()

    /**
     * 파일에서 업체 설정을 읽어 [config]에 올린다. 없으면 처음 설치 기본값을 둔다.
     * 파일이 깨졌거나 내용을 읽지 못하면 앱을 멈추지 않고 [VendorLoadState.UNREADABLE]로 표시한다.
     *
     * @return 읽은 결과
     */
    suspend fun load(): VendorLoadState {
        val result = readDocument()
        state.value = result.first
        latest.value = result.second
        return result.first
    }

    /**
     * 업체 설정 파일을 읽어 결과와 쓸 업체 설정을 돌려준다.
     *
     * @return 읽은 결과와 업체 설정
     */
    private suspend fun readDocument(): Pair<VendorLoadState, VendorConfig> {
        val stored = try {
            dataStore.data.first()[DOCUMENT_KEY]
        } catch (e: IOException) {
            return VendorLoadState.UNREADABLE to VendorConfig.initial()
        }
        if (stored == null) return VendorLoadState.NOT_SAVED to VendorConfig.initial()
        val decoded = codec.decode(stored) ?: return VendorLoadState.UNREADABLE to VendorConfig.initial()
        return VendorLoadState.LOADED to decoded
    }

    /**
     * 업체 설정을 바꿔 즉시 저장한다. 관리자 코드 확인 결과(실패 횟수·잠금)도 이것으로 저장한다.
     * 메모리([config])를 먼저 바꾸고 파일에 쓴다.
     *
     * @param change 지금 업체 설정을 받아 새 업체 설정을 돌려주는 함수
     * @return 저장한 업체 설정
     * @throws IllegalStateException 파일을 읽지 못한 상태면(덮어쓰지 않으려고)
     */
    suspend fun update(change: (VendorConfig) -> VendorConfig): VendorConfig {
        check(state.value != VendorLoadState.UNREADABLE) { "읽지 못한 업체 설정 파일은 덮어쓰지 않는다" }
        val next = change(latest.value)
        latest.value = next
        state.value = VendorLoadState.LOADED
        dataStore.edit { prefs -> prefs[DOCUMENT_KEY] = codec.encode(next) }
        return next
    }
}
