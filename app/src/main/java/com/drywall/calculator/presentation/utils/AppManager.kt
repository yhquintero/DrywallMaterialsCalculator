package com.drywall.calculator.presentation.utils

import android.content.Context
import android.util.Log

object AppManager {
    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun getAppContext(): Context {
        return appContext ?: throw IllegalStateException("AppManager not initialized")
    }
}