plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.rinda.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rinda.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Default base URL for physical device over USB reverse or local dev:
        // When using USB: run "adb reverse tcp:8080 tcp:8080" so http://127.0.0.1:8080/ works directly!
        // When using emulator: http://10.0.2.2:8080/api/v1/
        buildConfigField("String", "DEFAULT_BASE_URL", "\"http://127.0.0.1:8080/api/v1/\"")
        buildConfigField("String", "EMULATOR_BASE_URL", "\"http://10.0.2.2:8080/api/v1/\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk)

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.swiperefreshlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // Retrofit & OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Room SQLite local database
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    // EncryptedSharedPreferences for JWT auth tokens
    implementation(libs.security.crypto)

    // WorkManager for background offline sync
    implementation(libs.work.runtime)

    // Maps & Charts & Images
    implementation(libs.osmdroid)
    implementation(libs.mpandroidchart)
    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
