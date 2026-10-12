import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.peteroomen.powerbubble"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.peteroomen.powerbubble"
        minSdk = 28 // GLOBAL_ACTION_LOCK_SCREEN
        targetSdk = 36
        // CI numbers each build, so every release is newer than the last (local builds are 1)
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        versionCode = (build ?: 0) + 1
        versionName = "0.1.${build ?: 0}"
    }

    // CI signs with the shared debug key it's given, so updates install over the app on the phone
    signingConfigs {
        getByName("debug") {
            System.getenv("DEBUG_KEYSTORE_FILE")?.let { storeFile = file(it) }
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
}
