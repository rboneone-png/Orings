// 只在根目錄宣告 Kotlin 外掛，讓 :core 與 :app 共用同一份 Kotlin Gradle Plugin。
// Android（AGP）與 Compose 外掛只在 :app 套用，這樣沒有 Android SDK 的環境也能建置 :core。
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}
