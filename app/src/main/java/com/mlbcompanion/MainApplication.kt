package com.baseballnerd.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.baseballnerd.app.util.SettingsManager

/**
 * Main Application class to handle global initialization, 
 * especially forcing the theme based on user preferences.
 */
class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Apply dark/light mode globally at startup. 
        // This ensures all activities and fragments use the correct theme 
        // consistently from the moment they are created.
        val settings = SettingsManager.get(this)
        val isDarkMode = settings.darkMode
        
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
