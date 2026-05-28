package com.chesstrainer

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestHomeScreenShortcut()
        setContent {
            ChessApp()
        }
    }

    private fun requestHomeScreenShortcut() {
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        if (prefs.getBoolean("shortcut_pinned", false)) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(this)) return
        val shortcut = ShortcutInfoCompat.Builder(this, "chessmaster")
            .setShortLabel("ChessMaster")
            .setLongLabel("ChessMaster - Шахматный тренажёр")
            .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
            .setIntent(Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
            })
            .build()
        ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)
        prefs.edit().putBoolean("shortcut_pinned", true).apply()
    }
}
