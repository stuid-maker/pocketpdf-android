import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.isFile) {
        propertiesFile.inputStream().use(::load)
    }
}

fun resolveConfigValue(key: String): String = sequenceOf(
    providers.gradleProperty(key).orNull,
    providers.environmentVariable(key).orNull,
    localProperties.getProperty(key),
).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

fun sha256(path: Path): String =
    Files.newInputStream(path).use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

fun prepareModelAsset(destination: Path, expectedSha256: String, download: (Path) -> Unit) {
    if (Files.isRegularFile(destination) && sha256(destination) == expectedSha256) return

    Files.createDirectories(destination.parent)
    val temporary = destination.resolveSibling("${destination.fileName}.download")
    Files.deleteIfExists(temporary)
    try {
        download(temporary)
        check(Files.isRegularFile(temporary)) {
            "Embedding model download did not create $temporary"
        }
        check(sha256(temporary) == expectedSha256) {
            "Embedding model SHA-256 verification failed. Delete the local model and retry."
        }
        try {
            Files.move(temporary, destination, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, destination, REPLACE_EXISTING)
        }
    } catch (error: Exception) {
        Files.deleteIfExists(temporary)
        if (error is IllegalStateException && error.message.orEmpty().contains("SHA-256")) {
            throw error
        }
        throw GradleException(
            "Unable to prepare the embedding model. Check network access to the pinned " +
                "MediaPipe model URL, then retry the build.",
            error,
        )
    }
}

val sentryDsn = resolveConfigValue("SENTRY_DSN")

val embeddingModelUrl =
    "https://storage.googleapis.com/mediapipe-models/text_embedder/" +
        "universal_sentence_encoder/float32/1/universal_sentence_encoder.tflite"
val embeddingModelSha256 =
    "89ad3c74175dd8caa398cc22b657296d94302d20c525c12b58b29420f7249749"
val embeddingModelFile =
    layout.projectDirectory.file("src/main/assets/models/universal_sentence_encoder.tflite")

val prepareEmbeddingModel by tasks.registering {
    group = "build setup"
    description = "Downloads and verifies the pinned MediaPipe embedding model."
    outputs.file(embeddingModelFile)
    outputs.upToDateWhen { false }

    doLast {
        prepareModelAsset(embeddingModelFile.asFile.toPath(), embeddingModelSha256) { temporary ->
            val connection = URI(embeddingModelUrl).toURL().openConnection().apply {
                connectTimeout = 30_000
                readTimeout = 60_000
            }
            connection.getInputStream().use { input ->
                Files.copy(input, temporary, REPLACE_EXISTING)
            }
        }
    }
}

android {
    namespace = "com.asuka.pocketpdf"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.asuka.pocketpdf"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Sentry DSN：通过 buildConfigField 注入，避免硬编码
        buildConfigField("String", "SENTRY_DSN", "\"${sentryDsn.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }

    signingConfigs {
        create("release") {
            if (localProperties.isNotEmpty()) {
                val keystorePath = localProperties.getProperty("KEYSTORE_PATH")
                if (!keystorePath.isNullOrBlank()) {
                    storeFile = file(keystorePath)
                    storePassword = localProperties.getProperty("KEYSTORE_PASSWORD", "")
                    keyAlias = localProperties.getProperty("KEY_ALIAS", "pocketpdf")
                    keyPassword = localProperties.getProperty("KEY_PASSWORD", "")
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
    androidResources {
        noCompress += "tflite"
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

tasks.configureEach {
    if (
        (name.startsWith("merge") && name.endsWith("Assets")) ||
        name.contains("lint", ignoreCase = true)
    ) {
        dependsOn(prepareEmbeddingModel)
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
