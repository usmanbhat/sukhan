package com.sukhan.poetry

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

internal class SettingsSheet(
    private val activity: Activity,
    private val palette: Palette,
    private val themeChoice: ThemeChoice,
    private val language: String,
    private val label: (String) -> String,
    private val arabicTypeface: Typeface,
    private val serifTypeface: Typeface,
    private val cacheSize: String,
    private val speechSpeed: Float,
    private val speechPitch: Float,
    private val onThemeChange: (ThemeChoice) -> Unit,
    private val onLanguageChange: (String) -> Unit,
    private val onSpeechSpeedChange: (Float) -> Unit,
    private val onSpeechPitchChange: (Float) -> Unit,
    private val onClearCache: () -> Unit,
    private val onAbout: () -> Unit
) {
    private val dialog = Dialog(activity)

    fun show() {
        val overlay = FrameLayout(activity).apply {
            setBackgroundColor(0x77000000.toInt())
            setOnClickListener { dialog.dismiss() }
        }
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = if (language == "ur" || language == "fa") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            setPadding(dp(20), dp(8), dp(20), dp(12))
            background = GradientDrawable().apply {
                setColor(palette.surface)
                val radius = dp(25).toFloat()
                cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            }
            isClickable = true
            elevation = dp(12).toFloat()
        }
        panel.setOnClickListener { }
        val sheetHeight = minOf(dp(760), (activity.resources.displayMetrics.heightPixels * 0.88f).toInt())
        overlay.addView(panel, FrameLayout.LayoutParams(-1, sheetHeight, Gravity.BOTTOM))

        val handle = View(activity).apply { background = rounded(palette.line, 3f) }
        panel.addView(handle, LinearLayout.LayoutParams(dp(42), dp(4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(4)
            bottomMargin = dp(13)
        })

        val header = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(text(label("settings"), 25f, palette.ink, serif = true), LinearLayout.LayoutParams(0, dp(46), 1f))
        header.addView(text("×", 28f, palette.muted).apply {
            gravity = Gravity.CENTER
            contentDescription = "Close settings"
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        panel.addView(header)

        val scroll = ScrollView(activity).apply { isFillViewport = true; clipToPadding = false }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(18))
            layoutDirection = panel.layoutDirection
        }
        scroll.addView(content)
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        section(content, "appearance")
        val themes = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val themeItems = listOf(
            Triple(ThemeChoice.SYSTEM, "◧", "system"),
            Triple(ThemeChoice.LIGHT, "☼", "light"),
            Triple(ThemeChoice.DARK, "☾", "dark"),
            Triple(ThemeChoice.SEPIA, "▤", "sepia")
        )
        themeItems.forEachIndexed { index, (choice, glyph, key) ->
            val selected = choice == themeChoice
            val item = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = rounded(if (selected) palette.raised2 else palette.raised, 13f, if (selected) palette.ink else palette.line)
                setPadding(dp(2), dp(8), dp(2), dp(7))
                setOnClickListener { dialog.dismiss(); onThemeChange(choice) }
            }
            item.addView(text(glyph, 19f, if (selected) palette.ink else palette.muted).apply { gravity = Gravity.CENTER })
            item.addView(text(label(key), 11f, if (selected) palette.ink else palette.muted, bold = selected).apply {
                gravity = Gravity.CENTER
                maxLines = 1
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
            themes.addView(item, LinearLayout.LayoutParams(0, dp(75), 1f).apply {
                if (index > 0) marginStart = dp(6)
            })
        }
        content.addView(themes, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        section(content, "language")
        listOf("en" to "english", "ur" to "urdu", "fa" to "persian").forEachIndexed { index, (code, key) ->
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(5), 0, dp(5), 0)
                minimumHeight = dp(49)
                setOnClickListener { dialog.dismiss(); onLanguageChange(code) }
            }
            row.addView(text(label(key), 16f, palette.ink), LinearLayout.LayoutParams(0, -2, 1f))
            if (language == code) row.addView(text("✓", 18f, palette.ink, bold = true).apply { gravity = Gravity.CENTER })
            content.addView(row, LinearLayout.LayoutParams(-1, -2))
            if (index < 2) content.addView(divider(), LinearLayout.LayoutParams(-1, dp(1)))
        }

        section(content, "speech")
        val voiceCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(11), dp(13), dp(11))
            background = rounded(palette.raised, 13f, palette.line)
        }
        voiceCard.addView(text(label("manaVoice"), 15f, palette.ink, bold = true))
        voiceCard.addView(text(label("singleVoiceOnly"), 12f, palette.muted).apply { setPadding(0, dp(4), 0, 0) })
        content.addView(voiceCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
        speechControl(content, "speed", "speedHint", speechSpeed, 0.75f, 1.25f,
            { String.format(Locale.ROOT, "%.2f×", it) }, onSpeechSpeedChange)
        speechControl(content, "pitch", "pitchHint", speechPitch, 0.80f, 1.20f,
            { String.format(Locale.ROOT, "%+.0f%%", (it - 1f) * 100f) }, onSpeechPitchChange)

        section(content, "storage")
        val storage = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val cacheInfo = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        cacheInfo.addView(text(label("cache"), 15f, palette.ink, bold = true))
        cacheInfo.addView(text(cacheSize, 12f, palette.muted).apply { setPadding(0, dp(3), 0, 0) })
        storage.addView(cacheInfo, LinearLayout.LayoutParams(0, -2, 1f))
        storage.addView(text(label("clear"), 13f, palette.ink, bold = true).apply {
            gravity = Gravity.CENTER
            background = rounded(palette.raised2, 11f, palette.line)
            setPadding(dp(18), dp(10), dp(18), dp(10))
            setOnClickListener { dialog.dismiss(); onClearCache() }
        })
        content.addView(storage, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        section(content, "about")
        val aboutRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(palette.raised, 13f, palette.line)
            setPadding(dp(13), dp(11), dp(13), dp(11))
            setOnClickListener { onAbout() }
        }
        aboutRow.addView(text("ⓘ", 22f, palette.muted).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(34), dp(36)))
        val aboutText = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        aboutText.addView(text(label("about"), 15f, palette.ink, bold = true))
        aboutText.addView(text(label("offlineInfo"), 12f, palette.muted).apply { setPadding(0, dp(3), 0, 0) })
        aboutRow.addView(aboutText, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) })
        aboutRow.addView(text("›", 22f, palette.muted))
        content.addView(aboutRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        content.addView(text("Sukhan · سخن", 12f, palette.muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(19), 0, dp(8))
        })

        dialog.setContentView(overlay)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setDimAmount(0f)
        dialog.show()
        dialog.window?.setLayout(-1, -1)
        dialog.window?.statusBarColor = palette.surface
        dialog.window?.navigationBarColor = palette.surface
        @Suppress("DEPRECATION")
        dialog.window?.decorView?.systemUiVisibility = if (palette.isDark) 0 else (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    }

    private fun section(parent: LinearLayout, key: String) {
        val title = text(label(key).uppercase(Locale.ROOT), 12f, palette.muted, bold = true).apply {
            letterSpacing = 0.12f
            setPadding(0, dp(18), 0, dp(6))
        }
        parent.addView(title)
        parent.addView(divider(), LinearLayout.LayoutParams(-1, dp(1)))
    }

    private fun divider(): View = View(activity).apply { setBackgroundColor(palette.line) }

    private fun speechControl(
        parent: LinearLayout,
        titleKey: String,
        hintKey: String,
        value: Float,
        minimum: Float,
        maximum: Float,
        format: (Float) -> String,
        onChange: (Float) -> Unit
    ) {
        val heading = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        heading.addView(text(label(titleKey), 14f, palette.ink, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        val valueLabel = text(format(value), 13f, palette.ink, bold = true).apply { gravity = Gravity.CENTER }
        heading.addView(valueLabel, LinearLayout.LayoutParams(dp(64), -2))
        parent.addView(heading, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(13) })

        var selectedValue = value
        val slider = SeekBar(activity).apply {
            max = 100
            progress = (((value.coerceIn(minimum, maximum) - minimum) / (maximum - minimum)) * max).toInt()
            progressTintList = ColorStateList.valueOf(palette.ink)
            thumbTintList = ColorStateList.valueOf(palette.ink)
            progressBackgroundTintList = ColorStateList.valueOf(palette.line)
            contentDescription = label(titleKey)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val next = minimum + (maximum - minimum) * progress / 100f
                    selectedValue = next
                    valueLabel.text = format(next)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) { onChange(selectedValue) }
            })
        }
        parent.addView(slider, LinearLayout.LayoutParams(-1, dp(38)))
        parent.addView(text(label(hintKey), 11f, palette.muted).apply {
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setPadding(0, 0, 0, dp(2))
        })
    }

    private fun rounded(color: Int, radiusDp: Float, stroke: Int? = null): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        stroke?.let { setStroke(dp(1), it) }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false, serif: Boolean = false): TextView = TextView(activity).apply {
        text = value
        textSize = size
        setTextColor(color)
        includeFontPadding = false
        typeface = when {
            value.any { it.code in 0x0600..0x08FF } -> arabicTypeface
            serif -> if (bold) Typeface.create(serifTypeface, Typeface.BOLD) else serifTypeface
            bold -> Typeface.create("sans-serif", Typeface.BOLD)
            else -> Typeface.create("sans-serif", Typeface.NORMAL)
        }
        layoutDirection = if (value.any { it.code in 0x0600..0x08FF }) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_INHERIT
    }

    private fun dp(value: Float): Int = AppTheme.dp(activity, value)
    private fun dp(value: Int): Int = AppTheme.dp(activity, value.toFloat())
}
