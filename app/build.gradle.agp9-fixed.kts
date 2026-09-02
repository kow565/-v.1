import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
}

android {
    namespace = "com.ojun.klaswatch"
    compileSdk = 37

    signingConfigs {
        create("klaswatchStable") {
            storeFile = rootProject.file("signing/klaswatch-dev.jks")
            storePassword = "_cT80CGwAAhR-W5vseE5ZKESXQHthPgA"
            keyAlias = "klaswatch"
            keyPassword = "_cT80CGwAAhR-W5vseE5ZKESXQHthPgA"
        }
    }

    defaultConfig {
        applicationId = "com.ojun.klaswatch"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.4.0"
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("klaswatchStable")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("org.jsoup:jsoup:1.23.2")
}

