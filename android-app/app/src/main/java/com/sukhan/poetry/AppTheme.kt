package com.sukhan.poetry

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

internal enum class ThemeChoice(val key: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark"), SEPIA("sepia");

    companion object {
        fun from(value: String?): ThemeChoice = entries.firstOrNull { it.key == value } ?: SYSTEM
    }
}

internal data class Palette(
    val surface: Int,
    val raised: Int,
    val raised2: Int,
    val ink: Int,
    val muted: Int,
    val line: Int,
    val faint: Int,
    val accent: Int,
    val isDark: Boolean
)

internal object AppTheme {
    private fun c(hex: String): Int = Color.parseColor(hex)

    fun resolve(context: Context, choice: ThemeChoice): Palette {
        val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val dark = choice == ThemeChoice.DARK || (choice == ThemeChoice.SYSTEM && systemDark)
        return when {
            choice == ThemeChoice.SEPIA -> Palette(
                surface = c("#FAF6EC"), raised = c("#F4EEE0"), raised2 = c("#EDE6D4"),
                ink = c("#332B21"), muted = c("#6E6252"), line = c("#DCD2BC"),
                faint = c("#E7DFCE"), accent = c("#332B21"), isDark = false
            )
            dark -> Palette(
                surface = c("#0C0C0B"), raised = c("#131312"), raised2 = c("#1D1D1B"),
                ink = c("#F6F6F4"), muted = c("#8F8F88"), line = c("#262624"),
                faint = c("#333330"), accent = c("#F0F0ED"), isDark = true
            )
            else -> Palette(
                surface = c("#FCFCFB"), raised = c("#F6F6F4"), raised2 = c("#F0F0ED"),
                ink = c("#191917"), muted = c("#5C5C57"), line = c("#D9D9D4"),
                faint = c("#E8E8E4"), accent = c("#191917"), isDark = false
            )
        }
    }

    fun dp(context: Context, value: Float): Int = (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
