package com.zara.assistant.utils

import android.util.Log

object ZaraLogger {
    private const val TAG = "Zara"
    private var debug = false

    fun init(isDebug: Boolean) { debug = isDebug }
    fun d(msg: String) { if (debug) Log.d(TAG, msg) }
    fun e(msg: String) { Log.e(TAG, msg) }
}
