package com.sukhan.poetry

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import java.util.Locale
import java.util.concurrent.Executors

internal const val OPEN_DAILY_EXTRA = "com.sukhan.poetry.extra.OPEN_DAILY"

class DailyQuoteWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        val pending = goAsync()
        executor.execute {
            try {
                val app = context.applicationContext
                val local = LocalStore(app)
                val corpus = CorpusStore(app)
                val verse = corpus.dailyVerse()
                val themeChoice = ThemeChoice.from(local.getSetting("theme", "system"))
                val palette = AppTheme.resolve(app, themeChoice)
                val language = local.getSetting("language", "en")
                val cardBackground = when {
                    themeChoice == ThemeChoice.SEPIA -> R.drawable.widget_card_sepia
                    palette.isDark -> R.drawable.widget_card_dark
                    else -> R.drawable.widget_card_light
                }
                val buttonBackground = when {
                    themeChoice == ThemeChoice.SEPIA -> R.drawable.widget_button_sepia
                    palette.isDark -> R.drawable.widget_button_dark
                    else -> R.drawable.widget_button_light
                }
                val header = when (language) {
                    "ur" -> "سخن · آج کا شعر"
                    "fa" -> "سخن · شعر امروز"
                    else -> "SUKHAN · DAILY VERSE"
                }
                val shareLabel = when (language) {
                    "ur" -> "تصویر شیئر کریں"
                    "fa" -> "اشتراک تصویر"
                    else -> "Share as image"
                }
                val poem = verse?.let { corpus.poemForVerse(it.poemId) }
                val source = poem?.let { "${it.title}  ·  ${it.poetName}" } ?: "Sukhan"

                appWidgetIds.forEach { widgetId ->
                    val views = RemoteViews(app.packageName, R.layout.widget_daily_quote)
                    views.setInt(R.id.widgetCard, "setBackgroundResource", cardBackground)
                    views.setInt(R.id.widgetShare, "setBackgroundResource", buttonBackground)
                    views.setTextColor(R.id.widgetLabel, palette.muted)
                    views.setTextColor(R.id.widgetQuote, palette.ink)
                    views.setTextColor(R.id.widgetSource, palette.muted)
                    views.setTextColor(R.id.widgetShare, palette.ink)
                    views.setTextViewText(R.id.widgetLabel, header)
                    views.setTextViewText(R.id.widgetQuote, verse?.persian ?: "سخن")
                    views.setTextViewText(R.id.widgetSource, source)
                    views.setTextViewText(R.id.widgetShare, shareLabel)
                    views.setInt(R.id.widgetQuote, "setTextDirection", android.view.View.TEXT_DIRECTION_RTL)
                    views.setInt(R.id.widgetLabel, "setTextDirection", if (language == "ur" || language == "fa") android.view.View.TEXT_DIRECTION_RTL else android.view.View.TEXT_DIRECTION_LTR)

                    val openIntent = Intent(app, MainActivity::class.java).apply {
                        action = "com.sukhan.poetry.action.OPEN_DAILY"
                        data = Uri.parse("sukhan://daily/$widgetId")
                        putExtra(OPEN_DAILY_EXTRA, true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    val openPending = PendingIntent.getActivity(app, widgetId, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    views.setOnClickPendingIntent(R.id.widgetCard, openPending)

                    val shareIntent = Intent(app, ShareDailyQuoteActivity::class.java).apply {
                        action = "com.sukhan.poetry.action.SHARE_DAILY"
                        data = Uri.parse("sukhan://share-daily/$widgetId")
                    }
                    val sharePending = PendingIntent.getActivity(app, 10000 + widgetId, shareIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    views.setOnClickPendingIntent(R.id.widgetShare, sharePending)
                    manager.updateAppWidget(widgetId, views)
                }
            } catch (_: Exception) {
                // Keep the last displayed widget state if local data is temporarily unavailable.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val executor = Executors.newSingleThreadExecutor()

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DailyQuoteWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val update = Intent(context, DailyQuoteWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(update)
        }
    }
}

class ShareDailyQuoteActivity : android.app.Activity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val corpus = CorpusStore(this)
            if (!corpus.checkIntegrity()) return
            val local = LocalStore(this)
            val verse = corpus.dailyVerse() ?: return
            val poem = corpus.poemForVerse(verse.poemId)
            val palette = AppTheme.resolve(this, ThemeChoice.from(local.getSetting("theme", "system")))
            val arabicTypeface = android.graphics.Typeface.createFromAsset(assets, "assets/fonts/JameelNooriNastaleeq.ttf")
            val serifTypeface = android.graphics.Typeface.createFromAsset(assets, "assets/fonts/InstrumentSerif-Regular.ttf")
            val chooserTitle = when (local.getSetting("language", "en")) {
                "ur" -> "روز کا شعر شیئر کریں"
                "fa" -> "اشتراک شعر روز"
                else -> "Share daily verse"
            }
            DailyQuoteShare.share(this, verse, poem, palette, arabicTypeface, serifTypeface, chooserTitle)
        } catch (_: Exception) {
            android.widget.Toast.makeText(this, "Could not create the quote image.", android.widget.Toast.LENGTH_SHORT).show()
        } finally {
            finish()
        }
    }
}
