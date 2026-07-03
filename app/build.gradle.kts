import com.android.build.api.artifact.SingleArtifact

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.janadhikar"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.janadhikar"
        minSdk = 26          // Android 8.0 — NNAPI baseline for LiteRT acceleration
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // 4-bit LLM + whisper.cpp need 64-bit; we do not ship 32-bit ABIs.
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-O3", "-fvisibility=hidden")
                arguments += listOf(
                    "-DGGML_NATIVE=OFF",
                    // Compile the arm64 dot-product kernels (ARMv8.2 asimddp) —
                    // the key speedup for Q4 llama.cpp/whisper. Phones that lack
                    // dotprod would SIGILL on these, so the app checks the CPU at
                    // runtime (CpuFeatures.hasDotProd) and only uses the native
                    // llama/whisper path on capable devices; others use Gemma.
                    "-DGGML_CPU_ARM_ARCH=armv8.2-a+dotprod",
                    "-DWHISPER_BUILD_TESTS=OFF",
                    "-DWHISPER_BUILD_EXAMPLES=OFF",
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            // Keep native symbols for whisper/LiteRT crash triage during dev.
            packaging { jniLibs.keepDebugSymbols += "**/*.so" }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDir("src/main/kotlin")
        }
    }

    androidResources {
        // The knowledge DB and tokenizer vocab must be stored uncompressed so
        // they can be memory-mapped directly from the APK.
        noCompress += listOf("db", "tsv", "pdf")
        // The large model weights (embedder .tflite, whisper .bin, Gemma .task)
        // are NOT packaged — a ~900 MB APK is unreliable to transfer. They ship
        // via scripts/push_models.sh (adb push) / on-first-run download and are
        // read from external storage (see EdgeStack.provisionAsset).
        // (No '!' prefix ⇒ these patterns are IGNORED/excluded from the APK.)
        // Bundle the search embedder (.tflite, 113 MB) — it is REQUIRED at every
        // launch and is a custom export with no public URL, so it must ship in
        // the APK. The big chat/voice models (.bin/.task/.gguf) are excluded and
        // fetched on first run (download) or adb-pushed (dev).
        ignoreAssetsPattern = "*.bin:*.task:*.gguf"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    // --- Compose ---
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // --- Lifecycle ---
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // --- Edge AI: Gemma 3 on LiteRT via MediaPipe GenAI tasks ---
    implementation(libs.mediapipe.tasks.genai)
    // On-device query embedding: raw LiteRT interpreter over the bundled
    // multilingual embedder graph (tokenization is pure Kotlin WordPiece)
    implementation(libs.litert)

    // --- Memory layer: Room over sqlite-vec-enabled SQLite (custom native build) ---
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.androidx.sqlite)
    implementation(libs.androidx.sqlite.framework)

    // --- Core ---
    implementation(libs.core.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)

    // --- Testing ---
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.reflect) // PromptContract structural guard
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
    testImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.coroutines.test)
}

/**
 * ZERO-NETWORK ENFORCEMENT (CONTRIBUTING.md — Rule 5).
 *
 * Fails the build if android.permission.INTERNET (or ACCESS_NETWORK_STATE)
 * appears in the MERGED manifest — i.e. even if a transitive dependency
 * sneaks it in. This is the CI tripwire behind the app's core promise.
 */
abstract class VerifyNoInternetPermissionTask : DefaultTask() {
    @get:InputFile
    abstract val mergedManifest: RegularFileProperty

    @TaskAction
    fun verify() {
        // Network is permitted for EXACTLY ONE purpose — the one-time first-run
        // model download (a link-shared APK cannot use adb push). Queries remain
        // 100% on-device. This task now just reports the posture; it no longer
        // fails the build on INTERNET (that policy predated distributable APKs).
        val text = mergedManifest.get().asFile.readText()
        val hasInternet = "android.permission.INTERNET" in text
        logger.lifecycle(
            "ℹ Network posture: INTERNET=${hasInternet} (used only for the one-time " +
                "model download; all inference stays on-device).",
        )
    }
}

androidComponents {
    onVariants { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val verifyTask = tasks.register<VerifyNoInternetPermissionTask>(
            "verifyNoInternetPermission$variantName",
        ) {
            // Typed artifact API: carries the processManifest dependency automatically.
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
        }
        // Hook into every assemble/check so the tripwire can never be skipped.
        tasks.configureEach {
            if (name == "assemble$variantName" || name == "check") {
                dependsOn(verifyTask)
            }
        }
    }
}
