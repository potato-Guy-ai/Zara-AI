package com.zara.assistant.utils

import android.util.Log

object ZaraLogger {
    private const val TAG = "Zara"
    private var verbose = true

    fun init(debug: Boolean = true) { verbose = debug }

    fun v(msg: String) { if (verbose) Log.v(TAG, msg) }
    fun d(msg: String) { Log.d(TAG, msg) }
    fun w(msg: String) { Log.w(TAG, msg) }
    fun e(msg: String) { Log.e(TAG, msg) }
}
