plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.example.running"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.example.running"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    buildTypes {
        release {
            // TODO: Add your own signing config for the release build.
            // Signing with the debug keys for now, so `flutter run --release` works.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

flutter {
    source = "../.."
}

// dev 편의 (회귀 R-005): 디버그 빌드 때마다 adb reverse 터널을 자동 개통한다.
// 실기기 USB 재연결·앱 재실행 시 터널이 소리 없이 사라져 NETWORK_ERROR가
// 재발하는 것을 원천 차단 — 사람이 기억할 필요가 없어야 재발하지 않는다.
// 기기 미연결·다중 기기·CI(adb 없음/exit≠0)에서는 조용히 건너뛴다.
val adbReverseDev = tasks.register<Exec>("adbReverseDev") {
    val adbExe = android.adbExecutable
    onlyIf { adbExe.exists() }
    commandLine(adbExe.absolutePath, "reverse", "tcp:8080", "tcp:8080")
    isIgnoreExitValue = true
}
tasks.whenTaskAdded {
    if (name == "assembleDebug") {
        finalizedBy(adbReverseDev)
    }
}
