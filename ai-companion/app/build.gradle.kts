plugins {
    id("com.android.application")
}

android {
    namespace = "com.kow565.perchancecompanion"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kow565.perchancecompanion"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
