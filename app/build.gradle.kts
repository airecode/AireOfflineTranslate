import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // AGP 9 ships Kotlin built in, so only the Compose compiler plugin is applied on top.
    alias(libs.plugins.compose.compiler)
}

/**
 * Release signing details, kept out of the repository.
 *
 * `keystore.properties` is gitignored along with the keystore itself. A leaked upload key lets
 * someone publish builds that Play accepts as yours, which is not a thing a git history should ever
 * be able to hand out. Absent the file the release build simply goes unsigned, so cloning and
 * building still works for anyone who only wants a debug APK.
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
}
val hasReleaseSigning = keystorePropertiesFile.exists()

android {
    namespace = "com.example.myapplication"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.aire.translate"
        minSdk = 33
        targetSdk = 37
        versionCode = 46
        versionName = "5.3-icon-tips"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Null without a keystore, which leaves an unsigned build rather than failing the
            // configuration phase for anyone who just cloned the repo.
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null

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
