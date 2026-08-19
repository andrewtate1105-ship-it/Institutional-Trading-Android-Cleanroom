plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseStoreFile = System.getenv("ANDROID_RELEASE_STORE_FILE")
val releaseStorePassword = System.getenv("ANDROID_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("ANDROID_RELEASE_KEY_ALIAS")
val releaseKeyPassword = System.getenv("ANDROID_RELEASE_KEY_PASSWORD")
val releaseSigningReady = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

val marketDataBaseUrl = System.getenv("MARKET_DATA_BASE_URL")?.trim().orEmpty()
val marketDataBaseUrlLiteral = marketDataBaseUrl
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace = "com.institutionaltrading.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.institutionaltrading.mobile"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0-cleanroom"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "MARKET_DATA_BASE_URL", "\"$marketDataBaseUrlLiteral\"")
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug { isMinifyEnabled = false }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
