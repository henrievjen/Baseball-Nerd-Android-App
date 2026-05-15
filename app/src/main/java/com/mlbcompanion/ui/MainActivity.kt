package com.baseballnerd.app.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.baseballnerd.app.R
import com.baseballnerd.app.databinding.ActivityMainBinding
import com.baseballnerd.app.util.SettingsManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        val settings = SettingsManager.get(this)
        val isDarkMode = settings.darkMode

        // Enable edge-to-edge layout with fully transparent bars.
        // This allows our layout backgrounds to fill the entire screen area.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Match Activity backgrounds to the footer color (surface_dark)
        // to prevent the "blue" color leak behind the system navigation buttons.
        val footerColor = ContextCompat.getColor(this, R.color.surface_dark)
        window.setBackgroundDrawable(ColorDrawable(footerColor))
        binding.root.setBackgroundColor(footerColor)

        // Ensure status bar icons are white (not dark) on top of our navy headers
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            // Contrast navigation bar icons with the grey footer color in light mode
            isAppearanceLightNavigationBars = !isDarkMode
        }

        // Apply Window Insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            // Set the navy View's height to match the status bar height
            binding.statusBarBg.updateLayoutParams {
                height = systemBars.top
            }

            // Remove manual root padding.
            // Content is pushed from top by statusBarBg View.
            // BottomNavigationView handles its own internal safe area padding by default.
            // Removing manual padding here fixes the "extra row of spacing" issue.
            v.updatePadding(top = 0, bottom = 0)

            insets
        }

        // Force an inset pass to ensure layout updates immediately
        binding.root.requestApplyInsets()

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val builder = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(false)
                .setPopUpTo(navController.graph.findStartDestination().id, false, false)

            navController.navigate(item.itemId, null, builder.build())
            true
        }

        binding.bottomNavigation.setOnItemReselectedListener { item ->
            navController.popBackStack(item.itemId, false)
        }
    }
}
