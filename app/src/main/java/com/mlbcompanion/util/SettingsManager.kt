package com.baseballnerd.app.util

import android.content.Context
import android.content.SharedPreferences

class SettingsManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("mlb_settings", Context.MODE_PRIVATE)

    companion object {
        private var instance: SettingsManager? = null

        fun get(context: Context): SettingsManager {
            if (instance == null) {
                instance = SettingsManager(context.applicationContext)
            }
            return instance!!
        }

        const val SCORE_VIEW_LIST = 0
        const val SCORE_VIEW_CARD = 1
    }

    var scoreViewType: Int
        get() = prefs.getInt("score_view_type", SCORE_VIEW_CARD)
        set(value) = prefs.edit().putInt("score_view_type", value).apply()

    // Dark mode is ON by default
    var darkMode: Boolean
        get() = prefs.getBoolean("dark_mode", true)
        set(value) = prefs.edit().putBoolean("dark_mode", value).apply()
}
