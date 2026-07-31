import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

/**
 * Reads a setting from local.properties or gradle.properties, trying each spelling in turn.
 *
 * Both files are accepted because either is a reasonable place to put this, and the aliases
 * exist because the BuildConfig field names and the Gradle property names look similar enough
 * to swap by accident. Guessing right beats failing with an empty value at runtime.
 */
fun setting(vararg names: String): String {
    for (name in names) {
        val value = localProperties.getProperty(name) ?: project.findProperty(name)?.toString()
        value?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return ""
}

// local.properties is gitignored, which makes it the better home for a personal token.
val huggingFaceToken: String = setting("hfToken", "HF_TOKEN", "huggingFaceToken", "modelAuthToken")

/**
 * A Hugging Face repo holding the models that exist nowhere public — the Whisper ExecuTorch
 * exports and the converted FunctionGemma bundle. Upload them once to a repo of your own and
 * the app fetches them from there; see MODELS.md.
 */
val huggingFaceModelRepo: String = setting("hfModelRepo", "HF_MODEL_REPO")

private val configuredBaseUrl: String = setting("modelBaseUrl", "MODEL_BASE_URL")

// A token pasted into the URL slot would otherwise reach the app and fail as a confusing
// "unknown protocol" at download time. Reject it here instead, without echoing the value.
val modelBaseUrl: String = when {
    configuredBaseUrl.isEmpty() -> ""
    configuredBaseUrl.startsWith("http://") || configuredBaseUrl.startsWith("https://") -> configuredBaseUrl
    else -> {
        logger.warn(
            "SentriAI: ignoring modelBaseUrl — it must start with http:// or https://. " +
                "If you meant to set a Hugging Face token, the key is hfToken."
        )
        ""
    }
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

        // Where the app fetches model files on first launch. Override in gradle.properties (or
        // with -PmodelBaseUrl=…) rather than editing this file. The Gemma bundles are gated on
        // HuggingFace and the Whisper .pte files are local exports, so in practice this points
        // at your own storage; see app/src/main/assets/README.md.
        buildConfigField("String", "MODEL_BASE_URL", "\"$modelBaseUrl\"")
        buildConfigField("String", "HF_MODEL_REPO", "\"$huggingFaceModelRepo\"")
        // Empty here so release builds never carry a token; the debug variant below overrides
        // it with whatever local.properties holds.
        buildConfigField("String", "HF_TOKEN", "\"\"")

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
        // "json" is here for the Whisper tokenizer: a compressed asset has no readable length,
        // so ModelStore cannot tell a complete extracted copy from a truncated one.
        noCompress += listOf("pte", "ptd", "bin", "task", "json")
    }

    buildTypes {
        debug {
            // Pulling a gated model straight from Hugging Face is a development convenience.
            // The token only reaches the debug APK, and the button that uses it is compiled
            // behind BuildConfig.DEBUG.
            buildConfigField("String", "HF_TOKEN", "\"$huggingFaceToken\"")
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // The engine classes log through android.util.Log, which is an unimplemented stub in
            // the JVM test runtime. Returning defaults lets their pure logic be tested off-device.
            isReturnDefaultValues = true
        }
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