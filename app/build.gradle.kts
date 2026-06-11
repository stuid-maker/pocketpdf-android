plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.asuka.pocketpdf"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.asuka.pocketpdf"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Sentry DSN：通过 buildConfigField 注入，避免硬编码
        buildConfigField("String", "SENTRY_DSN", "\"${findProperty("SENTRY_DSN") ?: ""}\"")
    }

    signingConfigs {
        create("release") {
            val propsFile = rootProject.file("local.properties")
            if (propsFile.exists()) {
                val lines = propsFile.readLines()
                val props = lines.associate { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        "" to ""
                    } else {
                        val eq = trimmed.indexOf('=')
                        if (eq > 0) trimmed.substring(0, eq) to trimmed.substring(eq + 1)
                        else "" to ""
                    }
                }.filterKeys { it.isNotEmpty() }
                val keystorePath = props["KEYSTORE_PATH"]
                if (!keystorePath.isNullOrBlank()) {
                    storeFile = file(keystorePath)
                    storePassword = props["KEYSTORE_PASSWORD"] ?: ""
                    keyAlias = props["KEY_ALIAS"] ?: "pocketpdf"
                    keyPassword = props["KEY_PASSWORD"] ?: ""
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val cfg = signingConfigs.getByName("release")
            if (cfg.storeFile != null) {
                signingConfig = cfg
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    // 防止 .tflite 被 AAPT 压缩（ModelLoader 需要随机读取未压缩文件）
    aaptOptions {
        noCompress += "tflite"
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.recyclerview)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // Async
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Persistence (Room)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.activity)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // PDF
    implementation(libs.pdfbox.android)
    implementation(libs.pdfium.android)

    // AI / Embedding (MediaPipe)
    implementation(libs.mediapipe.tasks.text)

    // Network（不再使用 Retrofit，所有 LLM 调用统一走原生 OkHttp）
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.okhttp.sse)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.kotlin.codegen)

    // Logging
    implementation(libs.timber)

    // Crash Monitoring (Sentry)
    implementation(libs.sentry.android)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
