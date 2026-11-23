import groovy.json.JsonSlurper

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val runNumEnv = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
var computedVersionCode = 351
var computedVersionName = "3.5.1"

val versionFile = rootProject.file("version.json")
if (versionFile.exists()) {
    runCatching {
        @Suppress("UNCHECKED_CAST")
        val parsed = JsonSlurper().parse(versionFile) as Map<String, Any?>
        (parsed["versionCode"] as? Number)?.toInt()?.let { computedVersionCode = it }
        (parsed["versionName"] as? String)?.takeIf { it.isNotBlank() }?.let { computedVersionName = it }
    }
}
if (runNumEnv != null && runNumEnv > 0) {
    computedVersionCode = maxOf(computedVersionCode, 100 + runNumEnv)
}

android {
    namespace = "com.phyll.filmhub"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.phyll.filmhub"
        minSdk = 24
        targetSdk = 34
        versionCode = computedVersionCode
        versionName = computedVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("chillfilm.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "chillfilm2026"
            keyAlias = System.getenv("KEY_ALIAS") ?: "chillfilm"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "chillfilm2026"
        }
    }

    buildTypes {
        release {
            // Persistent keystore across local & GitHub builds so updates install in-place without losing saved data!
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
            applicationIdSuffix = ""
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
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    testImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Jetpack Compose UI (Adaptive for TV360 D-Pad & Mobile Touch)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // AndroidX Media3 (ExoPlayer + HLS + DASH + OkHttp DataSource)
    val media3Version = "1.3.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")
    implementation("androidx.media3:media3-datasource-okhttp:$media3Version")

    // Networking, JSON, Coroutines, Image Loading & QR Code
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("com.google.zxing:core:3.5.3")

    // Unit Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
