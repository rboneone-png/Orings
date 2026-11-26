// 外掛版本由根目錄 build.gradle.kts 決定，這裡不帶版本
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.orings.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.orings.app"
        minSdk = 26
        targetSdk = 36
        // 發佈流程會用 -PappVersionName / -PappVersionCode 帶入版本號
        versionCode = (findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (findProperty("appVersionName") as String?) ?: "0.1.0"
    }

    // 固定的除錯簽章：GitHub Actions 每次都在新機器上編譯，若用自動產生的金鑰，
    // 每個版本簽章都不同，手機會因「套件衝突」拒絕覆蓋安裝。
    // 這是公開的除錯金鑰（密碼 android），只供測試安裝；上架 Google Play 需另設正式金鑰。
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.androidx.ui.tooling)
}
