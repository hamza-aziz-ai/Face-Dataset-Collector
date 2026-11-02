plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing (D-022). Read only from the shell of whoever builds, so no keystore path or password lives in the repo or on this machine's disk. Without them the release stays unsigned, as in CI.
val releaseStoreFile: String? = providers.environmentVariable("RELEASE_STORE_FILE").orNull

android {
    namespace = "com.example.facedatasetcollector"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.facedatasetcollector"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                // PKCS12 keystores use one password for the store and the key.
                storePassword = providers.environmentVariable("RELEASE_STORE_PASSWORD").get()
                keyPassword = providers.environmentVariable("RELEASE_STORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").get()
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    // Android provides org.json at runtime; JVM unit tests need a real copy.
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // CameraX core & lifecycle
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    // For Compose interop
    implementation(libs.camerax.view)

    // (Optional) Compose permissions helper
    implementation(libs.accompanist.permissions)

    // ML Kit on-device face detection
    implementation(libs.face.detection)

    // Enrolment API client
    implementation(libs.okhttp)

    // Setup QR code decoding, on-device (D-011)
    implementation(libs.zxing.core)
}