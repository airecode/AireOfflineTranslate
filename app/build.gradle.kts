plugins {
    alias(libs.plugins.android.application)
    // AGP 9 ships Kotlin built in, so only the Compose compiler plugin is applied on top.
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.example.myapplication"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.aire.translate"
        minSdk = 33
        targetSdk = 37
        versionCode = 41
        versionName = "5.0-no-demo"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        // Exposes VERSION_NAME so the running build is visible in the UI.
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // ProcessLifecycleOwner: whole-app foreground/background, not per-activity.
    implementation(libs.androidx.lifecycle.process)

    // Live camera OCR: preview, frame analysis for the stability check, and stills.
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // On-device Gemma inference.
    implementation(libs.litertlm.android)
    // Google Play Billing for one-off donations.
    implementation(libs.billing.ktx)
    // Must be declared explicitly and kept ahead of what litertlm-android's POM asks for — see the
    // note in libs.versions.toml. Do not let this drift back down to 1.9.x.
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
