package com.sukhan.poetry

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream

internal object DailyQuoteShare {
    fun share(
        activity: Activity,
        verse: Verse,
        poem: Poem?,
        palette: Palette,
        arabicTypeface: Typeface,
        serifTypeface: Typeface,
        chooserTitle: String
    ) {
        val bitmap = render(verse, poem, palette, arabicTypeface, serifTypeface)
        val dir = File(activity.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "sukhan_daily_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Could not encode quote image" }
        }
        bitmap.recycle()
        val uri = Uri.parse("content://${activity.packageName}.share/${file.name}")
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(activity.contentResolver, "Sukhan daily verse", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(send, chooserTitle))
    }

    private fun render(verse: Verse, poem: Poem?, palette: Palette, arabicTypeface: Typeface, serifTypeface: Typeface): Bitmap {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(palette.surface)

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.raised }
        val cardRect = RectF(26f, 26f, width - 26f, height - 26f)
        canvas.drawRoundRect(cardRect, 42f, 42f, cardPaint)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.line
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(cardRect, 42f, 42f, borderPaint)

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.ink
            textSize = 33f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        canvas.drawText("S U K H A N", width / 2f, 120f, brandPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.muted
            textSize = 23f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        canvas.drawText("DAILY VERSE", width / 2f, 174f, titlePaint)

        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent; strokeWidth = 3f }
        canvas.drawLine(width * 0.35f, 218f, width * 0.65f, 218f, accentPaint)

        val quoteWidth = width - 210
        var quoteSize = 78f
        var quoteLayout = makeLayout(verse.persian, arabicTypeface, palette.ink, quoteSize, quoteWidth)
        while (quoteLayout.height > 520 && quoteSize > 50f) {
            quoteSize -= 4f
            quoteLayout = makeLayout(verse.persian, arabicTypeface, palette.ink, quoteSize, quoteWidth)
        }
        val quoteAreaTop = 305f
        val quoteAreaHeight = 575f
        val quoteTop = quoteAreaTop + ((quoteAreaHeight - quoteLayout.height) / 2f).coerceAtLeast(0f)
        canvas.save()
        canvas.translate((width - quoteWidth) / 2f, quoteTop)
        quoteLayout.draw(canvas)
        canvas.restore()

        val source = listOfNotNull(poem?.title?.takeIf { it.isNotBlank() }, poem?.poetName?.takeIf { it.isNotBlank() })
            .joinToString("  ·  ")
        if (source.isNotBlank()) {
            val sourceLayout = makeLayout(source, if (containsArabic(source)) arabicTypeface else serifTypeface, palette.muted, 33f, width - 180)
            canvas.save()
            canvas.translate(90f, 1000f)
            sourceLayout.draw(canvas)
            canvas.restore()
        }

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.muted
            textSize = 25f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        canvas.drawText("Sukhan  •  سخن", width / 2f, 1218f, footerPaint)
        canvas.drawLine(width * 0.39f, 1260f, width * 0.61f, 1260f, borderPaint)
        return bitmap
    }

    private fun makeLayout(value: String, typeface: Typeface, color: Int, textSize: Float, width: Int): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = textSize
            this.typeface = typeface
        }
        return StaticLayout.Builder.obtain(value, 0, value.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setLineSpacing(8f, 1.08f)
            .setIncludePad(false)
            .build()
    }

    private fun containsArabic(value: String): Boolean = value.any { it.code in 0x0600..0x08FF }
}
