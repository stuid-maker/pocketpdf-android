package com.asuka.pocketpdf

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid
import timber.log.Timber
import javax.inject.Inject

/**
 * Application 入口，挂 Hilt 注解作为依赖图根容器。
 * 此处只做"全局只能做一次"的初始化（日志、兼容路径资源加载器等）。
 *
 * PdfBox-Android 目前仅用于旧的长按标注字符位置路径。初始化说明：
 * `PDFBoxResourceLoader.init(applicationContext)` 调用一次即把 PdfBox 内置字体 / 字符表
 * 资源指向 Android assets。**没有这步**调用，PDFDocument.load 会因找不到默认资源在第一次
 * 解析时抛 `NullPointerException`（PdfBox 内部 Standard14Fonts 加载失败）。
 *
 * W2 Day 4: 实现 [Configuration.Provider] 以接入 HiltWorkerFactory，
 * 让 [androidx.work.WorkManager] 能通过 Hilt 注入 [androidx.hilt.work.HiltWorker]。
 */
@HiltAndroidApp
class PocketPdfApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        initSentry()
        PDFBoxResourceLoader.init(applicationContext)
        Timber.tag(TAG).i("PocketPdfApp onCreate · build=%s · version=%s",
            BuildConfig.BUILD_TYPE, BuildConfig.VERSION_NAME)
    }

    private fun initSentry() {
        val dsn = BuildConfig.SENTRY_DSN
        if (dsn.isBlank()) {
            Timber.tag(TAG).d("Sentry DSN not configured, skipping crash reporting")
            return
        }
        SentryAndroid.init(this) { options ->
            options.dsn = dsn
            options.tracesSampleRate = if (BuildConfig.DEBUG) 1.0 else 0.2
            options.isDebug = BuildConfig.DEBUG
            options.release = BuildConfig.VERSION_NAME
        }
        Timber.tag(TAG).i("Sentry initialized · version=%s", BuildConfig.VERSION_NAME)
    }

    private companion object {
        const val TAG = "PocketPdfApp"
    }
}
