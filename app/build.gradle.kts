import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.opencode.multilensipcam"
    compileSdk = 34

    val localDebugKeystore = rootProject.file(".android/debug.keystore")

    defaultConfig {
        applicationId = "com.opencode.multilensipcam"
        minSdk = 25
        targetSdk = 34
        versionCode = 100
        versionName = "0.5.31"
    }

    signingConfigs {
        create("localDebug") {
            storeFile = localDebugKeystore
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            if (localDebugKeystore.isFile) {
                signingConfig = signingConfigs.getByName("localDebug")
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
}

val archiveDebugApkOutputs = {
    val debugDir = layout.buildDirectory.dir("outputs/apk/debug").get().asFile
    val sourceApk = debugDir.resolve("app-debug.apk")
    if (!sourceApk.isFile) {
        throw GradleException("Debug APK not found at ${sourceApk.absolutePath}")
    }

    val timestamp = LocalDateTime.now()
        .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
    val versionName = android.defaultConfig.versionName ?: "0.0.0"
    val versionCode = android.defaultConfig.versionCode ?: 0
    val archiveName = "LensCast-debug-v$versionName-$versionCode-$timestamp.apk"
    val latestName = "LensCast-debug-latest.apk"

    copy {
        from(sourceApk)
        into(debugDir)
        rename { archiveName }
    }

    delete(fileTree(rootDir) {
        include("LensCast-debug-latest*.apk")
    })

    copy {
        from(sourceApk)
        into(rootDir)
        rename { latestName }
    }

    logger.lifecycle("Archived debug APK: ${debugDir.resolve(archiveName).absolutePath}")
    logger.lifecycle("Updated latest debug APK: ${rootDir.resolve(latestName).absolutePath}")
}

tasks.register("archiveDebugApk") {
    group = "build"
    description = "Archives the debug APK with version/timestamp and refreshes the project-root latest APK copy."

    doLast {
        archiveDebugApkOutputs()
    }
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    doLast {
        archiveDebugApkOutputs()
    }
}
