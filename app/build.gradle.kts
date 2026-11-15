plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.annyeongdrive.app"
    compileSdk {
        version = release(37)
    }
    // 주행 중 화면 제한(CarUxRestrictionsManager). SDK에 들어 있는 차량용 라이브러리를 컴파일에 연결한다(7단계, 11/14 승인).
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.annyeongdrive.app"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                // kotlin.**을 넣으면 R8이 표준 라이브러리 클래스를 옮기며 접근 오류(IllegalAccessError)로 앱이 시작하자마자 멈춘다(11/14 에뮬레이터 확인).
                packageScope = setOf("androidx.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Pleos Connect SDK
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navihelper)
    implementation(libs.pleos.fusedlocation)
    implementation(libs.pleos.texttospeech)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // 진행 저장: JSON 직렬화와 Preferences DataStore (결정 문서 4장)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
// 테스트 묶음 나누기(11/15 결정): -PtestGroup=signal이면 신호 시나리오 테스트(signaltest 패키지)만, ui면 화면 테스트(uitest 패키지)만,
// unit이면 그 둘을 뺀 나머지를 돌린다. 없으면 전부. GitHub Actions는 Unit Test · Signal Test · UI Test를 따로 돌린다.
val testGroup = providers.gradleProperty("testGroup").orNull
val signalTests = "com.annyeongdrive.app.signaltest.*"
val uiTests = "com.annyeongdrive.app.uitest.*"
tasks.withType<Test>().configureEach {
    when (testGroup) {
        "unit" -> filter.excludeTestsMatching(signalTests).excludeTestsMatching(uiTests)
        "signal" -> filter.includeTestsMatching(signalTests)
        "ui" -> filter.includeTestsMatching(uiTests)
    }
}
