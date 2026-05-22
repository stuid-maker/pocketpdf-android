# ProGuard/R8 rules for PocketPDF
# W4: Added rules for PdfBox, MediaPipe, Moshi, Room to prevent obfuscation breakage.

# ── PdfBox-Android ──────────────────────────────────────
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# ── MediaPipe Text Embedder ──────────────────────────────
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# ── Moshi (KSP codegen) ─────────────────────────────────
-keep class com.asuka.pocketpdf.data.remote.dto.** { *; }
-keep class * extends com.squareup.moshi.JsonAdapter { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }

# ── Room entities ────────────────────────────────────────
-keep class com.asuka.pocketpdf.data.local.entity.** { *; }

# ── OkHttp / Okio ────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**

# ── Coroutines ───────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.internal.FastServiceLoader {}

# ── Timber ────────────────────────────────────────────────
-dontwarn org.jetbrains.annotations.**

# ── Keep line numbers for crash debugging ────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
