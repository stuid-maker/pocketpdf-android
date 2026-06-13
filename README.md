# PocketPDF

PocketPDF is an Android PDF reader with on-device indexing and retrieval-augmented AI reading tools. It imports local PDFs, renders and searches them with PDFium, creates embeddings on the device, and connects to an OpenAI-compatible LLM for cited Q&A and summaries.

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin)](https://kotlinlang.org)
[![CI](https://img.shields.io/github/actions/workflow/status/stuid-maker/pocketpdf-android/ci.yml?branch=main&logo=githubactions)](https://github.com/stuid-maker/pocketpdf-android/actions)
[![Release](https://img.shields.io/badge/release-v1.2.0-7652A8)](https://github.com/stuid-maker/pocketpdf-android/releases)
[![Tests](https://img.shields.io/badge/tests-402%20JVM%20%2B%2031%20Android-brightgreen)](https://github.com/stuid-maker/pocketpdf-android/actions)

## Highlights

- Import local PDFs through Android's Storage Access Framework.
- Render, zoom, swipe, and search pages through a unified PDFium session.
- Highlight and underline selected text with Room-backed persistence.
- Build document embeddings locally with MediaPipe Text Embedder.
- Ask cited questions against the current document.
- Generate page or full-document summaries with bounded-concurrency Map-Reduce.
- Show generation stage, progress, and approximate remaining time.
- Collapse long-running summaries and keep reading without cancelling them.
- Stop generation with immediate OkHttp request cancellation.
- Regenerate the selected assistant response instead of the latest question.
- Use LM Studio or another OpenAI-compatible local/cloud endpoint.

## Architecture

PocketPDF is a single-module Clean Architecture application:

```text
ui (Compose, ViewModels, PdfPageView)
        |
        v
domain (models, use cases, repository contracts)
        ^
        |
data (Room, PDFium, MediaPipe, OkHttp, WorkManager)
```

The UI is Jetpack Compose. The PDF canvas remains a focused custom Android `View` hosted through `AndroidView`. The domain layer is pure Kotlin and does not depend on Android APIs.

| Area | Technology |
|---|---|
| Language | Kotlin 2.0.21, Java 17 |
| Build | AGP 8.7.3, Gradle 8.10.2, KSP |
| UI | Jetpack Compose Material 3 |
| Storage | Room 2.6.1, DataStore |
| PDF | PdfiumAndroid 1.0.30, PdfBox-Android compatibility path |
| Embedding | MediaPipe Text Embedder, Universal Sentence Encoder TFLite |
| Network | OkHttp 4.12, Moshi 1.15.1, SSE |
| Background work | WorkManager |
| DI | Hilt 2.52 |
| Tests | JUnit4, MockK, Turbine, Robolectric, Compose UI Test, Espresso |

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for data flows, contracts, and ADRs.

## Requirements

- Android 8.0 / API 26 or newer.
- JDK 17 for local builds.
- Android SDK 35.
- An OpenAI-compatible LLM endpoint for AI generation.

Embedding and retrieval run on the Android device. AI generation still needs a reachable LLM endpoint. For local-first use, run LM Studio on the development computer.

## Quick Start

```bash
git clone https://github.com/stuid-maker/pocketpdf-android.git
cd pocketpdf-android
./gradlew assembleDebug
```

Gradle automatically prepares:

```text
app/src/main/assets/models/universal_sentence_encoder.tflite
```

The pinned model is 6,120,274 bytes and is accepted only when its SHA-256 is:

```text
89ad3c74175dd8caa398cc22b657296d94302d20c525c12b58b29420f7249749
```

The file is downloaded to a temporary path, verified, then moved into place. A valid cached copy allows later offline builds.

### Local LLM

Start an LM Studio OpenAI-compatible server on port `1234`, then bridge a connected Android device or emulator:

```bash
curl http://localhost:1234/v1/models
adb reverse tcp:1234 tcp:1234
```

The default app endpoint is `http://localhost:1234/v1`. Settings also supports custom OpenAI-compatible endpoints and optional API keys.

## Verification

```bash
./gradlew testDebugUnitTest
./gradlew lint
./gradlew connectedDebugAndroidTest
./gradlew assembleRelease
```

The v1.2.0 release audit passed:

- 402 JVM tests.
- 31 Android instrumentation tests on Android 16 / API 36.1.
- Android lint with zero errors.
- Signed release APK verification.
- Embedded model path and SHA-256 verification.

See [docs/project-audit-2026-06-13.md](docs/project-audit-2026-06-13.md) for the full result.

## Release Builds

Create `local.properties` from [local.properties.example](local.properties.example) and set the release keystore values. `SENTRY_DSN` is optional.

```bash
./gradlew assembleRelease
```

The release asset is the signed APK only. Source code, screenshots, plans, reports, local configuration, credentials, and development artifacts are not attached to GitHub Releases.

## Documentation

- [CHANGELOG.md](CHANGELOG.md)
- [ROADMAP.md](ROADMAP.md)
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- [docs/project-audit-2026-06-13.md](docs/project-audit-2026-06-13.md)
- [CONTRIBUTING.md](CONTRIBUTING.md)
- [docs/dev-log/](docs/dev-log/)

## Known Limits

- Scanned PDFs without a text layer require OCR, which is not bundled.
- AI generation depends on the configured LLM endpoint.
- Chat and summary cache content is stored in the app database without application-level encryption.
- The current PDFium version remains pinned to `1.0.30`; newer `1.0.x` artifacts use Kotlin 2.2 metadata and are incompatible with this Kotlin 2.0 toolchain.

## License

[MIT](LICENSE)
