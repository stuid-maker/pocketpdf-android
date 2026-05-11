package com.asuka.pocketpdf

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Application 入口，挂 Hilt 注解作为依赖图根容器。
 * 此处只做"全局只能做一次"的初始化（日志、必要的客户端单例提示等）。
 * 业务相关初始化交给各 Hilt module / WorkManager。
 */
@HiltAndroidApp
class PocketPdfApp : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.tag(TAG).i("PocketPdfApp onCreate · build=%s", BuildConfig.BUILD_TYPE)
    }

    private companion object {
        const val TAG = "PocketPdfApp"
    }
}
