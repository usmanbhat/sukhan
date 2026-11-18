package com.sukhan.poetry

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.TextPaint
import android.text.TextDirectionHeuristics
import android.text.StaticLayout
import android.view.Gravity
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

internal class ShareActions(
    private val activity: Activity,
    private val persianTypeface: Typeface
) {
    private var chosenOrders = linkedSetOf<Int>()

    fun show(poem: Poem, verses: List<Verse>, palette: Palette) {
        if (verses.isEmpty()) {
            Toast.makeText(activity, "Nothing to share.", Toast.LENGTH_SHORT).show()
            return
        }
        val options = arrayOf("Share text", "Share as image", "Create PDF", "Copy text", "Choose lines")
        AlertDialog.Builder(activity)
            .setTitle(poem.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> shareText(poem, verses)
                    1 -> shareImage(poem, verses, palette)
                    2 -> sharePdf(poem, verses)
                    3 -> copyText(poem, verses)
                    4 -> chooseLines(poem, verses, palette)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun chooseLines(poem: Poem, verses: List<Verse>, palette: Palette) {
        val all = verses.map { it.order }
        val selected = (if (chosenOrders.isEmpty()) all else chosenOrders.toList()).toMutableSet()
        val labels = verses.mapIndexed { i, line -> "${i + 1}. ${line.persian.take(70)}" }.toTypedArray()
        val checked = BooleanArray(verses.size) { selected.contains(verses[it].order) }
        AlertDialog.Builder(activity)
            .setTitle("Choose lines to share")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val order = verses.getOrNull(which)?.order ?: return@setMultiChoiceItems
                if (isChecked) selected.add(order) else selected.remove(order)
            }
            .setPositiveButton("Done") { _, _ ->
                chosenOrders = selected.toCollection(linkedSetOf())
                show(poem, verses, palette)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun selected(verses: List<Verse>): List<Verse> = if (chosenOrders.isEmpty()) verses else verses.filter { chosenOrders.contains(it.order) }.ifEmpty { verses }

    private fun content(poem: Poem, verses: List<Verse>): String = buildString {
        append(poem.title)
        append("\n")
        append(poem.poetName)
        append("\n\n")
        selected(verses).forEach { append(it.persian); append("\n") }
    }

    private fun shareText(poem: Poem, verses: List<Verse>) {
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, content(poem, verses)) }
        try { activity.startActivity(Intent.createChooser(intent, "Share poem")) }
        catch (_: Exception) { Toast.makeText(activity, "No sharing app is available.", Toast.LENGTH_SHORT).show() }
    }

    private fun copyText(poem: Poem, verses: List<Verse>) {
        val clipboard = activity.getSystemService(Activity.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(poem.title, content(poem, verses)))
        Toast.makeText(activity, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun shareImage(poem: Poem, verses: List<Verse>, palette: Palette) {
        try {
            val bitmap = renderImage(poem, selected(verses), palette)
            val dir = File(activity.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, "poem_${poem.id}_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            shareFile(file, "image/png", poem.title)
        } catch (_: Exception) {
            Toast.makeText(activity, "Could not create the share image.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderImage(poem: Poem, verses: List<Verse>, palette: Palette): Bitmap {
        val width = 1080
        val height = 1440
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paper = palette.surface
        val ink = palette.ink
        canvas.drawColor(paper)
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.line; style = Paint.Style.STROKE; strokeWidth = 3f }
        canvas.drawRoundRect(RectF(34f, 34f, width - 34f, height - 34f), 30f, 30f, border)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 44f; typeface = Typeface.create("serif", Typeface.BOLD) }
        val title = StaticLayout.Builder.obtain(poem.title, 0, poem.title.length, titlePaint, 900)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL).setIncludePad(true).build()
        canvas.save(); canvas.translate(90f, 110f); title.draw(canvas); canvas.restore()
        val authorPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.muted; textSize = 26f; typeface = persianTypeface }
        val author = StaticLayout.Builder.obtain(poem.poetName, 0, poem.poetName.length, authorPaint, 900)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL).setIncludePad(true).build()
        canvas.save(); canvas.translate(90f, 125f + title.height); author.draw(canvas); canvas.restore()

        val verseText = verses.joinToString("\n\n") { it.persian }
        val availableHeight = height - title.height - author.height - 350
        var textSize = 43f
        var body: StaticLayout
        do {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = textSize; typeface = persianTypeface }
            body = StaticLayout.Builder.obtain(verseText, 0, verseText.length, paint, 900)
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setTextDirection(TextDirectionHeuristics.RTL)
                .setLineSpacing(10f, 1.18f).setIncludePad(true).build()
            if (body.height <= availableHeight || textSize <= 27f) break
            textSize -= 2f
        } while (true)
        val top = (height - body.height) / 2f + 25f
        canvas.save(); canvas.translate(90f, max(top, 360f)); body.draw(canvas); canvas.restore()
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.muted; textSize = 24f; textAlign = Paint.Align.CENTER; typeface = Typeface.create("serif", Typeface.NORMAL) }
        canvas.drawText("SUKHAN  ·  POETRY", width / 2f, height - 90f, footerPaint)
        return bitmap
    }

    private fun sharePdf(poem: Poem, verses: List<Verse>) {
        val document = PdfDocument()
        try {
            val pageWidth = 595
            val pageHeight = 842
            val margin = 42f
            val text = content(poem, verses)
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; textSize = 19f; typeface = persianTypeface }
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, (pageWidth - margin * 2).toInt())
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setLineSpacing(8f, 1.12f)
                .setIncludePad(true)
                .build()
            val contentHeight = pageHeight - 2 * margin
            val pages = max(1, ceil(layout.height / contentHeight).toInt())
            repeat(pages) { index ->
                val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                val page = document.startPage(info)
                page.canvas.drawColor(android.graphics.Color.WHITE)
                page.canvas.save()
                page.canvas.translate(margin, margin - index * contentHeight)
                page.canvas.clipRect(0f, index * contentHeight, pageWidth - margin * 2, (index + 1) * contentHeight)
                layout.draw(page.canvas)
                page.canvas.restore()
                document.finishPage(page)
            }
            val dir = File(activity.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, "poem_${poem.id}_${System.currentTimeMillis()}.pdf")
            FileOutputStream(file).use { document.writeTo(it) }
            shareFile(file, "application/pdf", poem.title)
        } catch (_: Exception) {
            Toast.makeText(activity, "Could not create the PDF.", Toast.LENGTH_SHORT).show()
        } finally {
            document.close()
        }
    }

    private fun shareFile(file: File, mime: String, title: String) {
        val uri = Uri.Builder().scheme("content").authority("${activity.packageName}.share").appendPath(file.name).build()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            clipData = ClipData.newUri(activity.contentResolver, title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { activity.startActivity(Intent.createChooser(intent, "Share poem")) }
        catch (_: Exception) { Toast.makeText(activity, "No sharing app is available.", Toast.LENGTH_SHORT).show() }
    }
}
