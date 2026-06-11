# PocketPDF · 口袋 PDF — RAG 阅读助手 for Android

> An Android PDF reader with **on-device RAG** — import PDFs, chunk & vectorize, then ask questions or generate summaries via a **local LLM** (LM Studio on your PC). Fully offline-capable after setup.

<div align="center">

[![GitHub Repo](https://img.shields.io/github/stars/stuid-maker/pocketpdf-android?style=flat&logo=github)](https://github.com/stuid-maker/pocketpdf-android)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android)](https://github.com/stuid-maker/pocketpdf-android)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)
[![CI](https://img.shields.io/github/actions/workflow/status/stuid-maker/pocketpdf-android/ci.yml?branch=main&logo=githubactions)](https://github.com/stuid-maker/pocketpdf-android/actions)
[![Status](https://img.shields.io/badge/status-v1.0.0--release-green)](ROADMAP.md)
[![Tests](https://img.shields.io/badge/tests-246%20passed-brightgreen)](https://github.com/stuid-maker/pocketpdf-android/actions)
[![Tag](https://img.shields.io/github/v/tag/stuid-maker/pocketpdf-android?logo=git)](https://github.com/stuid-maker/pocketpdf-android/tags)

</div>

<p align="center">
  <b>English</b> · <a href="https://github.com/stuid-maker/pocketpdf-android">GitHub</a> · <a href="docs/ARCHITECTURE.md">Architecture</a> · <a href="ROADMAP.md">Roadmap</a>
</p>

---

## Features

- 📄 **Import & Read PDFs** — Browse local PDFs, render pages, bookmark your place
- 🔍 **RAG Q&A** — Ask questions about your document; get answers with page‑level citations
- 🤖 **AI Summaries** — One‑tap page‑level or full‑document summaries
- 💻 **Local LLM Support** — Connect to LM Studio (or any OpenAI‑compatible server) on your PC; no internet required after setup
- 🌙 **Dark Theme** — Material 3 with a purple accent, light & dark modes
- 🧩 **Smart Chunking** — Text is automatically chunked, embedded (MiniLM‑L6‑v2), and indexed for fast retrieval

## Architecture

PocketPDF follows **Clean Architecture** with **MVVM** in a single‑module layout:

```
┌──────────────────┐
│   ui (Compose+VM)  │  ← Activities, ViewModels, Compose screens
└────────┬─────────┘
         │ depends on
┌────────▼─────────┐
│     domain       │  ← Use Cases, Domain Models, Repository Interfaces
└────────▲─────────┘
         │ implements
┌────────┴─────────┐
│      data        │  ← Room, Retrofit (OpenAI‑compat), PdfBox, Embedder
└──────────────────┘
```

Dependency rule is strict: `ui → domain ← data`. The `domain` layer has **zero Android dependencies**.

## Tech Stack

| Layer | Choice | Badge |
|-------|--------|-------|
| Language | Kotlin 2.0 | [![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin)](https://kotlinlang.org) |
| Platform | minSdk 26 (Android 8.0+) | [![Android](https://img.shields.io/badge/minSdk-26-3DDC84?logo=android)](https://developer.android.com) |
| UI | Jetpack Compose (Material 3) | [![Compose](https://img.shields.io/badge/Compose-M3-4285F4?logo=jetpackcompose)](https://developer.android.com/jetpack/compose) |
| Architecture | Clean Architecture + MVVM + Repository | — |
| DI | Hilt | [![Hilt](https://img.shields.io/badge/DI-Hilt-2C3E50?logo=dagger)](https://dagger.dev/hilt) |
| Async | Coroutines + Flow + StateFlow | [![Coroutines](https://img.shields.io/badge/Coroutines-1.9-18C8D6?logo=kotlin)](https://kotlinlang.org/docs/coroutines-overview.html) |
| Local DB | Room | [![Room](https://img.shields.io/badge/Room-2.7-15A97E?logo=sqlite)](https://developer.android.com/training/data-storage/room) |
| Networking | Retrofit + OkHttp + okhttp-sse | [![Retrofit](https://img.shields.io/badge/Retrofit-2.11-E65100?logo=square)](https://square.github.io/retrofit) |
| PDF Rendering | Pdfium-Android | — |
| PDF Text | PdfBox-Android | — |
| Embedding | MediaPipe TextEmbedder (Universal Sentence Encoder) | — |
| LLM Backend | **LM Studio** (default: Gemma 3 4B-IT Q4_K_M) via OpenAI‑compatible API + `adb reverse` | — |
| Testing | JUnit4 + MockK + Turbine + Espresso | [![Tests](https://img.shields.io/badge/Tests-JUnit%20%7C%20MockK%20%7C%20Turbine-25A162)](https://github.com/stuid-maker/pocketpdf-android) |
| CI | GitHub Actions | [![CI](https://img.shields.io/badge/CI-GitHub%20Actions-2088FF?logo=githubactions)](https://github.com/stuid-maker/pocketpdf-android/actions) |

## Screenshots

### 📚 Library

<div style="display: flex; flex-wrap: wrap; gap: 12px; justify-content: center;">
  <img src="docs/screenshots/library-main.png" width="250" alt="Library main view" />
  <img src="docs/screenshots/library-empty.png" width="250" alt="Empty library state" />
  <img src="docs/screenshots/library-menu.png" width="250" alt="Library context menu" />
</div>

### 📖 Reader

<div style="display: flex; flex-wrap: wrap; gap: 12px; justify-content: center;">
  <img src="docs/screenshots/reader-pdf.png" width="250" alt="PDF reader view" />
  <img src="docs/screenshots/reader-summary.png" width="250" alt="AI summary in reader" />
  <img src="docs/screenshots/reader-error.png" width="250" alt="Reader error state" />
</div>

### 💬 Chat / Q&A

<div style="display: flex; flex-wrap: wrap; gap: 12px; justify-content: center;">
  <img src="docs/screenshots/chat-empty.png" width="250" alt="Empty chat view" />
  <img src="docs/screenshots/chat-conversation.png" width="250" alt="Chat with conversation" />
</div>

### ⚙️ Settings

<div style="display: flex; flex-wrap: wrap; gap: 12px; justify-content: center;">
  <img src="docs/screenshots/settings-main.png" width="250" alt="Settings main page" />
  <img src="docs/screenshots/settings-model-dropdown.png" width="250" alt="Model preset dropdown" />
</div>

## Quick Start

### Prerequisites

- [Android Studio](https://developer.android.com/studio) (Ladybug or later)
- Android device or emulator (API 26+)
- [LM Studio](https://lmstudio.ai/) on your PC (for LLM features)
- **Embedding model** — the on-device vectorization engine needs a TFLite model file. Download it automatically via the Gradle task (recommended) or manually from Google's official storage bucket.

### Setup

```bash
# 1. Clone the repository
git clone https://github.com/stuid-maker/pocketpdf-android.git
cd pocketpdf-android

# 2. Download the embedding model (REQUIRED for AI features)
#    Download universal_sentence_encoder.tflite (~68 MB) from Google's official bucket:
#    https://storage.googleapis.com/mediapipe-models/text_embedder/universal_sentence_encoder/float32/1/universal_sentence_encoder.tflite
#    Place it at: app/src/main/assets/models/universal_sentence_encoder.tflite

# 3. Open in Android Studio
#    File → Open → select pocketpdf-android → wait for Gradle sync

# 4. Build & run
#    Select a device and press Run (▶)
```

&gt; **Why is this step needed?** The `.tflite` model file is too large to store in Git (~68 MB). Download it from Google's official MediaPipe model storage bucket. If you skip this step, PDF indexing will fail and all AI features (Q&amp;A, summaries) will be unavailable.

### LLM Backend (LM Studio)

```bash
# On your PC: Start LM Studio → Developer tab → Start Server (port 1234)
# Verify it's running:
curl http://localhost:1234/v1/models

# Bridge to your Android device:
adb reverse tcp:1234 tcp:1234

# You're all set — PocketPDF will discover models automatically.
```

## Roadmap

| Week | Theme | Status | Tag |
|------|-------|--------|-----|
| W0 | Environment setup + docs skeleton | ✅ Done | `v0.0.1-env-ready` |
| W1 | PDF reader demo | ✅ Done | `v0.1.0-pdf-reader` |
| W2 | Text chunking + vectorization + indexing | ✅ Done | `v0.2.0-indexed` |
| W3 | Retrieval + LLM bridging + summarization | ✅ Done | `v0.3.0-summary` |
| W4 | Q&A with citation backlinks + polish | 🟡 In Progress | `v0.4.0-qa` |
| W5 | Tests + docs + demo video | ⚪ Pending | `v1.0.0-release` |

## Documentation

- [`ARCHITECTURE.md`](docs/ARCHITECTURE.md) — Detailed architectural decisions
- [`PLAN.md`](PLAN.md) — Project plan & technical choices
- [`ROADMAP.md`](ROADMAP.md) — Week‑by‑week task list
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — Coding conventions & Git workflow
- [`dev-log/`](docs/dev-log/) — Weekly development logs

## License

[MIT](LICENSE) © stuid-maker
