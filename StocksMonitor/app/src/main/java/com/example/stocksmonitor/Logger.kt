package com.example.stocksmonitor

import android.util.Log

object Logger {
    // Will be set from SettingsManager when app starts
    var enableDebugLogs: Boolean = true
    
    fun d(tag: String, message: String) {
        if (enableDebugLogs) {
            Log.d(tag, message)
        }
    }
    
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        // Always log errors, even in release builds
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
    
    fun w(tag: String, message: String) {
        if (enableDebugLogs) {
            Log.w(tag, message)
        }
    }
    
    fun i(tag: String, message: String) {
        if (enableDebugLogs) {
            Log.i(tag, message)
        }
    }
}
