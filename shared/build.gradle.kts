// shared/build.gradle.kts
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "me.dgol.friday.shared"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
}