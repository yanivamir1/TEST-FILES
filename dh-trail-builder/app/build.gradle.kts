plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.dhtrailbuilder"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.dhtrailbuilder"
        minSdk = 24
        targetSdk = 34
        versionCode = 8
        versionName = "v8"
    }

    // CI runners are ephemeral, so the SDK's auto-generated ~/.android/debug.keystore is
    // different on every build - installing a newer APK over an older one then fails with
    // "package conflicts with an existing package" (signatures don't match). Signing every
    // debug build with this committed, fixed-password keystore instead keeps the signature
    // stable across builds, so installing an update over a previous install just works.
    signingConfigs {
        getByName("debug") {
            storeFile = file("../debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
}
