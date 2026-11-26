// 所有外掛都載入在根目錄的同一個 classloader，避免 Kotlin 與 Android 外掛互相看不到。
// Android Gradle Plugin（AGP）只在有 Android SDK 時才載入，
// 這樣沒有 SDK（也連不到 Google Maven）的環境仍可建置與測試 :core。
buildscript {
    val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
        System.getenv("ANDROID_SDK_ROOT") != null ||
        rootDir.resolve("local.properties").let { it.exists() && it.readText().contains("sdk.dir") }
    if (hasAndroidSdk) {
        repositories {
            google()
            mavenCentral()
        }
        dependencies {
            classpath(libs.android.gradle.plugin)
        }
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
