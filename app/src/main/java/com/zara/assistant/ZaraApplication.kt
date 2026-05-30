package com.zara.assistant

import android.app.Application
import com.zara.assistant.utils.ZaraLogger

class ZaraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ZaraLogger.init(BuildConfig.DEBUG)
    }
}
