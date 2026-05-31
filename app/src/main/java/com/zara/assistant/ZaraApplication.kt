package com.zara.assistant

import android.app.Application
import com.zara.assistant.identity.IdentityManager
import com.zara.assistant.utils.ZaraLogger

class ZaraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialization order is mandatory:
        // 1. Identity first — all other components may depend on it.
        // 2. Logger second — identity must exist before any logging.
        // 3. Remaining initialization follows.
        IdentityManager.init(this)
        ZaraLogger.init(BuildConfig.DEBUG)
    }
}
