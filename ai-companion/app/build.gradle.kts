import java.util.Base64
import java.util.Properties

plugins {
    id("com.android.application")
}

val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}

val stableDebugKey = layout.buildDirectory.file("harin-stable-debug.keystore").get().asFile
val stableDebugKeyB64 = rootProject.file("ci/harin-debug.keystore.b64")
stableDebugKey.parentFile.mkdirs()
stableDebugKey.writeBytes(Base64.getDecoder().decode(stableDebugKeyB64.readText().trim()))

android {
    namespace = "com.kow565.perchancecompanion"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kow565.harincompanion"
        minSdk = 26
        targetSdk = 35
        versionCode = versionProps.getProperty("VERSION_CODE").toInt()
        versionName = versionProps.getProperty("VERSION_NAME")
    }

    signingConfigs {
        create("stableDebug") {
            storeFile = stableDebugKey
            storePassword = "harin-debug-2026"
            keyAlias = "harin"
            keyPassword = "harin-debug-2026"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("stableDebug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
