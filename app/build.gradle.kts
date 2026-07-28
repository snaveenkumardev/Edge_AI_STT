plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.sentriai"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.sentriai"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // The ExecuTorch AAR only ships libexecutorch.so for these two ABIs. Without the
            // filter the APK still gains armeabi-v7a/x86 folders from other dependencies, and
            // System.loadLibrary("executorch") then fails at runtime on those devices.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    androidResources {
        // Model artifacts are copied out of assets to filesDir before ExecuTorch can open them
        // (it takes filesystem paths, not asset paths). Keeping them uncompressed avoids the
        // aapt compressed-asset size limit and makes that first-run copy a straight byte copy.
        noCompress += listOf("pte", "ptd", "bin", "task")
    }

    buildTypes {
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
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    // viewModel() + collectAsStateWithLifecycle() for the transcription screen.
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation("com.google.mediapipe:tasks-genai:0.10.35")
    debugImplementation(libs.androidx.compose.ui.tooling)

    // On-device inference. Pulls fbjni, soloader-nativeloader and its own R8 keep rules
    // transitively — nothing else to declare here.
    implementation(libs.pytorch.executorch.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}