package com.annyeongdrive.app.storage

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File
import java.nio.file.Files

/**
 * 테스트용 DataStore 도우미. 임시 폴더에 실제 파일을 만들고, 앱을 껐다 켠 것처럼 닫았다가 다시 열 수 있다.
 * 같은 파일에 DataStore를 둘 열면 예외가 나므로, 다시 열기 전에 반드시 [close]한다.
 * 파일은 OkioStorage로 연다. 기본 FileStorage는 Windows JVM에서 임시 파일을 기존 파일 위로
 * 이름 바꾸지 못해 두 번째 저장부터 실패하기 때문이다(Android 기기에서는 생기지 않는 테스트 환경 문제).
 */
class TempDataStores {

    private val dir: File = Files.createTempDirectory("annyeong-datastore").toFile()
    private val jobs = mutableListOf<kotlinx.coroutines.Job>()

    /**
     * 이름에 해당하는 파일의 DataStore를 연다.
     *
     * @param name 파일 이름(예: driver_session)
     * @param replaceCorrupt 파일이 깨졌을 때 빈 내용으로 바꿀지. 앱의 driver_session과 같게 하려면 true
     * @return 열린 DataStore
     */
    fun open(name: String, replaceCorrupt: Boolean = false): DataStore<Preferences> {
        val job = SupervisorJob()
        jobs += job
        val storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { fileOf(name).toOkioPath() }
        val scope = CoroutineScope(Dispatchers.IO + job)
        if (replaceCorrupt) {
            val handler = ReplaceFileCorruptionHandler { emptyPreferences() }
            return PreferenceDataStoreFactory.create(storage = storage, corruptionHandler = handler, scope = scope)
        }
        return PreferenceDataStoreFactory.create(storage = storage, scope = scope)
    }

    /**
     * 이름에 해당하는 저장 파일. 파일이 깨진 상황을 만들 때 직접 쓴다.
     *
     * @param name 파일 이름(예: vendor_config)
     * @return 저장 파일
     */
    fun fileOf(name: String): File = File(dir, "$name.preferences_pb")

    /** 열린 DataStore를 모두 닫는다. 앱이 꺼진 것과 같다. */
    fun close() = runBlocking {
        jobs.forEach { it.cancelAndJoin() }
        jobs.clear()
    }

    /** 닫고 임시 폴더를 지운다. 테스트가 끝날 때 부른다. */
    fun delete() {
        close()
        dir.deleteRecursively()
    }
}
