package com.baseballnerd.app.ui

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.baseballnerd.app.util.SettingsManager

class BaseballNerdApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Apply the user's dark/light mode preference before any Activity or
        // Fragment is created. Doing this here — rather than in Activity.onCreate()
        // — prevents the brief flash where individual views (e.g. game cards) render
        // with the wrong system theme before the Activity recreates itself.
        val isDark = SettingsManager.get(this).darkMode
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
