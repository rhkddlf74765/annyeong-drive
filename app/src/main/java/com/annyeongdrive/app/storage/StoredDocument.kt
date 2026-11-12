package com.annyeongdrive.app.storage

import com.annyeongdrive.app.rental.RentalSession
import com.annyeongdrive.app.vendor.VendorConfig
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * 저장 파일 한 개의 내용. 저장 구조 버전과 데이터를 함께 둔다(결정 문서 4장 schemaVersion).
 *
 * @property schemaVersion 저장 구조 버전
 * @property data 저장한 데이터
 */
@Serializable
data class StoredDocument<T>(val schemaVersion: Int, val data: T)

/**
 * Aggregate 하나를 저장 문자열(JSON)로 바꾸고 되돌린다.
 * 저장 구조 버전이 다르거나 내용이 깨졌으면 읽지 않는다(null).
 *
 * @param serializer 데이터의 직렬화기
 * @param schemaVersion 지금 앱의 저장 구조 버전
 */
class DocumentCodec<T>(
    private val serializer: KSerializer<T>,
    private val schemaVersion: Int,
) {

    private val documentSerializer = StoredDocument.serializer(serializer)

    /**
     * 데이터를 저장 문자열로 바꾼다.
     *
     * @param data 저장할 데이터
     * @return JSON 문자열
     */
    fun encode(data: T): String = JSON.encodeToString(documentSerializer, StoredDocument(schemaVersion, data))

    /**
     * 저장 문자열을 데이터로 되돌린다.
     *
     * @param text JSON 문자열
     * @return 데이터. 저장 구조 버전이 다르거나 읽을 수 없으면 null
     */
    fun decode(text: String): T? {
        val document = parse(text) ?: return null
        if (document.schemaVersion != schemaVersion) return null
        return document.data
    }

    /**
     * JSON을 읽는다. 깨진 내용이면 null을 돌려준다.
     *
     * @param text JSON 문자열
     * @return 읽은 문서. 읽을 수 없으면 null
     */
    private fun parse(text: String): StoredDocument<T>? = try {
        JSON.decodeFromString(documentSerializer, text)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    companion object {
        /** 모르는 항목은 무시하고 기본값도 저장한다. */
        private val JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /** 운전자 데이터(driver_session)의 저장 구조 버전. */
        const val DRIVER_SCHEMA_VERSION = 1

        /** 업체 데이터(vendor_config)의 저장 구조 버전. */
        const val VENDOR_SCHEMA_VERSION = 1

        /**
         * 운전자 데이터용 codec을 만든다.
         *
         * @return RentalSession codec
         */
        fun forRentalSession(): DocumentCodec<RentalSession> =
            DocumentCodec(RentalSession.serializer(), DRIVER_SCHEMA_VERSION)

        /**
         * 업체 데이터용 codec을 만든다.
         *
         * @return VendorConfig codec
         */
        fun forVendorConfig(): DocumentCodec<VendorConfig> =
            DocumentCodec(VendorConfig.serializer(), VENDOR_SCHEMA_VERSION)
    }
}
