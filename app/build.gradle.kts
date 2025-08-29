plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.mainactivity"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.mainactivity"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // ✅ Java tarafı 17
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // (İstersen) coreLibraryDesugaringEnabled = true
    }

    // ✅ Kotlin tarafı 17 – toolchain ile
    kotlinOptions { jvmTarget = "17" }
    kotlin {
        jvmToolchain(17)
    }

    buildFeatures { compose = true }
    kotlinOptions { jvmTarget = "17" } // (tekrar yazmak sorun değil)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Eğer üstte desugaring açtıysan bunu da ekleyebilirsin:
    // coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}
