package com.sukhan.poetry

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.text.Editable
import android.text.TextWatcher
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

class MainActivity : Activity() {
    private enum class Screen { SPLASH, WELCOME, HOME, BOOKS, POEMS, SEARCH, FAVORITES, DAILY, BOOK_POEMS, CATEGORY, POEM_DETAIL }
    private enum class SearchFilter { ALL, BOOKS, POEMS, LINES }

    private lateinit var root: FrameLayout
    private lateinit var corpus: CorpusStore
    private lateinit var local: LocalStore
    private lateinit var palette: Palette
    private lateinit var body: LinearLayout
    private lateinit var persianTypeface: Typeface
    private lateinit var serifTypeface: Typeface
    private var themeChoice = ThemeChoice.SYSTEM
    private var language = "en"
    private var screen = Screen.SPLASH
    private val backStack = java.util.ArrayDeque<Screen>()
    private var selectedPoet: Poet? = null
    private var selectedPoem: Poem? = null
    private val categoryPath = mutableListOf<PoetCategory>()
    private var focusedVerseOrder = -1
    private var fontScale = 1.0f
    private var searchQuery = ""
    private var searchFilter = SearchFilter.ALL
    private var favoritesTab = 0
    private var welcomePage = 0
    private var splashQuote = ""
    private var splashSource = ""
    private val mainHandler = Handler(Looper.getMainLooper())
    private val coverCache = HashMap<Int, Bitmap>()
    private var searchResultsContainer: LinearLayout? = null
    private var searchSaveRunnable: Runnable? = null
    private var activeTtsButton: TextView? = null
    private var libraryReady = false
    private var libraryPreparing = false
    private var librarySetupError = false
    private var pendingDailyOpen = false
    private var installingPoetId: Int? = null
    private var speechSpeed = 1.0f
    private var speechPitch = 1.0f
    private var loadedPoemOffset = 0
    private var loadMorePoemsButton: View? = null
    private val poemPageSize = 40

    private val labels = mapOf(
        "home" to listOf("Home", "گھر", "خانه"),
        "books" to listOf("Books", "کتابیں", "کتاب‌ها"),
        "poems" to listOf("Poems", "اشعار", "شعرها"),
        "search" to listOf("Search", "تلاش", "جستجو"),
        "favorites" to listOf("Favorites", "پسندیدہ", "علاقه‌مندی‌ها"),
        "daily" to listOf("Daily Verse", "آج کا شعر", "شعر روز"),
        "settings" to listOf("Settings", "ترتیبات", "تنظیمات"),
        "today" to listOf("Today", "آج", "امروز"),
        "welcome" to listOf("Welcome", "خوش آمدید", "خوش آمدید"),
        "welcomeTitle" to listOf("A quiet home for poetry", "شاعری کے لیے ایک پُرسکون گھر", "خانه‌ای آرام برای شعر"),
        "welcomeSub" to listOf("Explore the words, worlds, and wisdom of Persian literature.", "فارسی ادب کے الفاظ، دنیا اور حکمت دریافت کریں۔", "واژه‌ها، جهان‌ها و حکمت ادبیات فارسی را کشف کنید."),
        "welcomeLibrary" to listOf("A library to return to", "ایک ایسی لائبریری جہاں بار بار آئیں", "کتابخانه‌ای برای بازگشت"),
        "welcomeLibrarySub" to listOf("Browse 245 poets. Rumi and Saadi are ready offline; download any other poet’s pack when connected.", "۲۴۵ شعرا دیکھیں۔ رومی اور سعدی آف لائن دستیاب ہیں؛ باقی شعرا کے مجموعے انٹرنیٹ سے ڈاؤن لوڈ کریں۔", "۲۴۵ شاعر را ببینید. رومی و سعدی آفلاین آماده‌اند؛ بستهٔ هر شاعر دیگر را هنگام اتصال دانلود کنید."),
        "welcomeRead" to listOf("Read without a connection", "بغیر انٹرنیٹ کے مطالعہ کریں", "بدون اینترنت بخوانید"),
        "welcomeReadSub" to listOf("Rumi and Saadi are stored on this device. Rumi’s English and Urdu translations are AI drafts awaiting review.", "رومی اور سعدی اسی آلے پر ہیں۔ رومی کے انگریزی اور اردو تراجم AI مسودے ہیں اور جائزے کے منتظر ہیں۔", "رومی و سعدی روی این دستگاه‌اند. ترجمه‌های انگلیسی و اردو رومی پیش‌نویس هوش مصنوعی و در انتظار بازبینی‌اند."),
        "welcomeInsight" to listOf("AI insights, when connected", "AI بصیرت، منسلک ہونے پر", "بینش هوش مصنوعی، پس از اتصال"),
        "welcomeInsightSub" to listOf("AI analysis is an optional integration point, not enabled in this offline demo.", "AI تجزیہ اختیاری ہے اور اس آف لائن نمونے میں فعال نہیں۔", "تحلیل هوش مصنوعی اختیاری است و در این نمونهٔ آفلاین فعال نیست."),
        "next" to listOf("Next", "اگلا", "بعدی"), "skip" to listOf("Skip", "چھوڑیں", "رد کردن"),
        "begin" to listOf("Begin reading", "مطالعہ شروع کریں", "شروع به خواندن"),
        "explore" to listOf("Explore", "دریافت کریں", "کاوش"),
        "featured" to listOf("A verse for today", "آج کے لیے ایک شعر", "شعری برای امروز"),
        "allPoems" to listOf("All poems", "تمام اشعار", "همهٔ شعرها"),
        "poemCount" to listOf("poems", "اشعار", "شعر"), "verseCount" to listOf("lines", "مصرعے", "مصراع"),
        "noPoems" to listOf("No poems in this collection yet.", "اس مجموعے میں ابھی اشعار نہیں۔", "هنوز شعری در این مجموعه نیست."),
        "loadMore" to listOf("Load more poems", "مزید اشعار دکھائیں", "نمایش شعرهای بیشتر"),
        "emptyFavorites" to listOf("Nothing saved yet.", "ابھی کچھ محفوظ نہیں۔", "هنوز چیزی ذخیره نشده است."),
        "recentSearches" to listOf("Recent searches", "حالیہ تلاشیں", "جستجوهای اخیر"),
        "noRecentSearches" to listOf("No recent searches yet.", "ابھی کوئی حالیہ تلاش نہیں۔", "هنوز جستجویی ثبت نشده است."),
        "clear" to listOf("Clear", "صاف کریں", "پاک کردن"), "all" to listOf("All", "سب", "همه"),
        "lines" to listOf("Lines", "مصرعے", "مصراع‌ها"), "typeSearch" to listOf("Search books, poems, or verses", "کتاب، شعر یا مصرع تلاش کریں", "کتاب، شعر یا مصراع را جستجو کنید"),
        "read" to listOf("Read", "پڑھیں", "خواندن"), "share" to listOf("Share", "شیئر کریں", "اشتراک"),
        "copy" to listOf("Copy", "کاپی کریں", "کپی"), "copied" to listOf("Copied", "کاپی ہوگیا", "کپی شد"),
        "notes" to listOf("Notes", "نوٹس", "یادداشت‌ها"), "addNote" to listOf("Add note", "نوٹ شامل کریں", "افزودن یادداشت"),
        "editNote" to listOf("Edit note", "نوٹ میں ترمیم", "ویرایش یادداشت"), "save" to listOf("Save", "محفوظ کریں", "ذخیره"),
        "cancel" to listOf("Cancel", "منسوخ", "لغو"), "analyze" to listOf("AI insights", "AI بصیرت", "بینش هوش مصنوعی"),
        "context" to listOf("Historical context", "تاریخی پس منظر", "زمینهٔ تاریخی"),
        "aiUnavailable" to listOf("AI is not connected", "AI منسلک نہیں", "هوش مصنوعی متصل نیست"),
        "aiExplain" to listOf("This source-app feature needs an AI provider. The demo stays offline; the integration settings are prepared in the code.", "اس خصوصیت کے لیے AI فراہم کنندہ درکار ہے۔ یہ نمونہ آف لائن ہے؛ کوڈ میں انضمام کی جگہ موجود ہے۔", "این قابلیت به ارائه‌دهندهٔ هوش مصنوعی نیاز دارد. نمونه آفلاین می‌ماند؛ محل اتصال در کد آماده است."),
        "appearance" to listOf("Appearance", "ظاہری شکل", "نمایش"), "system" to listOf("System", "سسٹم", "سیستم"),
        "light" to listOf("Light", "روشن", "روشن"), "dark" to listOf("Dark", "تاریک", "تیره"), "sepia" to listOf("Sepia", "سیپیا", "سپیا"),
        "language" to listOf("Language", "زبان", "زبان"), "english" to listOf("English", "انگریزی", "انگلیسی"),
        "urdu" to listOf("Urdu", "اردو", "اردو"), "persian" to listOf("Persian", "فارسی", "فارسی"),
        "storage" to listOf("Storage", "اسٹوریج", "ذخیره‌سازی"), "cache" to listOf("Cache", "کیش", "حافظهٔ موقت"),
        "clearCache" to listOf("Clear temporary files", "عارضی فائلیں صاف کریں", "پاک کردن فایل‌های موقت"),
        "cacheCleared" to listOf("Temporary files cleared.", "عارضی فائلیں صاف ہوگئیں۔", "فایل‌های موقت پاک شد."),
        "about" to listOf("About", "تعارف", "درباره"), "offlineInfo" to listOf("Offline library · Rumi and Saadi preloaded", "آف لائن لائبریری · رومی اور سعدی پہلے سے شامل", "کتابخانهٔ آفلاین · رومی و سعدی از پیش بارگذاری شده"),
        "dailyIntro" to listOf("A line selected from the downloaded offline library, changing by date.", "ڈاؤن لوڈ شدہ آف لائن لائبریری سے تاریخ کے مطابق منتخب شعر۔", "مصراعی از کتابخانهٔ آفلاین که با تاریخ تغییر می‌کند."),
        "bookOf" to listOf("Collection", "مجموعہ", "مجموعه"), "collections" to listOf("Books & collections", "کتابیں اور مجموعے", "کتاب‌ها و مجموعه‌ها"),
        "chapters" to listOf("chapters", "ابواب", "بخش‌ها"), "emptyCollection" to listOf("No books, chapters, or poems are listed here yet.", "یہاں ابھی کتاب، باب یا شعر درج نہیں۔", "هنوز کتاب، بخش یا شعری در اینجا نیست."),
        "noResults" to listOf("No matches found.", "کوئی نتیجہ نہیں ملا۔", "نتیجه‌ای پیدا نشد."),
        "chooseLines" to listOf("Choose lines", "مصرع منتخب کریں", "انتخاب مصراع‌ها"), "shareText" to listOf("Share text", "متن شیئر کریں", "اشتراک متن"),
        "shareImage" to listOf("Share as image", "تصویر کے طور پر شیئر کریں", "اشتراک به‌صورت تصویر"), "sharePdf" to listOf("Create PDF", "PDF بنائیں", "ساخت PDF"),
        "listen" to listOf("Listen", "سنیں", "گوش دادن"),
        "preparingVoice" to listOf("Preparing voice…", "آواز تیار ہو رہی ہے…", "آماده‌سازی صدا…"),
        "stopReading" to listOf("Stop reading", "پڑھنا روکیں", "توقف خواندن"),
        "ttsError" to listOf("Persian voice could not start. Please try again.", "فارسی آواز شروع نہیں ہو سکی۔ دوبارہ کوشش کریں۔", "صدای فارسی شروع نشد؛ دوباره تلاش کنید."),
        "noNotes" to listOf("No notes for this poem.", "اس شعر کے لیے کوئی نوٹ نہیں۔", "یادداشتی برای این شعر نیست."),
        "poemActions" to listOf("Poem options", "شعر کے اختیارات", "گزینه‌های شعر"), "bookActions" to listOf("Collection options", "مجموعے کے اختیارات", "گزینه‌های مجموعه"),
        "removeFavorite" to listOf("Remove from favorites", "پسندیدہ سے ہٹائیں", "حذف از علاقه‌مندی‌ها"),
        "addFavorite" to listOf("Add to favorites", "پسندیدہ میں شامل کریں", "افزودن به علاقه‌مندی‌ها"),
        "fontSize" to listOf("Reading size", "مطالعے کا سائز", "اندازهٔ متن"),
        "showAll" to listOf("View all", "سب دیکھیں", "نمایش همه"),
        "preparingLibrary" to listOf("Preparing your offline library…", "آف لائن لائبریری تیار ہو رہی ہے…", "کتابخانهٔ آفلاین آماده می‌شود…"),
        "librarySetupError" to listOf("The offline books could not be prepared. Please retry.", "آف لائن کتابیں تیار نہیں ہو سکیں۔ دوبارہ کوشش کریں۔", "آماده‌سازی کتابخانهٔ آفلاین ناموفق بود. دوباره تلاش کنید."),
        "retry" to listOf("Retry", "دوبارہ کوشش", "تلاش دوباره"),
        "download" to listOf("Download", "ڈاؤن لوڈ", "دریافت"),
        "downloaded" to listOf("Downloaded", "ڈاؤن لوڈ شدہ", "دریافت‌شده"),
        "notDownloaded" to listOf("Not downloaded", "ڈاؤن لوڈ نہیں", "دریافت نشده"),
        "downloadFailed" to listOf("Could not download this poet pack. Check your internet connection and try again.", "شاعر کا مجموعہ ڈاؤن لوڈ نہیں ہو سکا۔ انٹرنیٹ کنکشن دیکھ کر دوبارہ کوشش کریں۔", "دانلود بستهٔ شاعر ناموفق بود. اتصال اینترنت را بررسی کنید و دوباره تلاش کنید."),
        "packStorage" to listOf("Pack size", "مجموعے کا سائز", "اندازهٔ بسته"),
        "readyOffline" to listOf("Available offline", "آف لائن دستیاب", "آفلاین در دسترس"),
        "aiDraft" to listOf("AI draft", "AI مسودہ", "پیش‌نویس هوش مصنوعی"),
        "reviewPending" to listOf("Human review pending", "انسانی جائزہ باقی ہے", "در انتظار بازبینی انسانی"),
        "translated" to listOf("Translated", "ترجمہ شدہ", "ترجمه‌شده"),
        "englishTranslation" to listOf("English translation", "انگریزی ترجمہ", "ترجمهٔ انگلیسی"),
        "urduTranslation" to listOf("Urdu translation", "اردو ترجمہ", "ترجمهٔ اردو"),
        "speech" to listOf("Persian speech", "فارسی تقریر", "گفتار فارسی"),
        "voice" to listOf("Voice", "آواز", "صدا"),
        "manaVoice" to listOf("Mana · Persian · offline", "مانا · فارسی · آف لائن", "مانا · فارسی · آفلاین"),
        "singleVoiceOnly" to listOf("One Persian voice is currently bundled.", "فی الحال ایک فارسی آواز شامل ہے۔", "فعلاً یک صدای فارسی در بسته است."),
        "speed" to listOf("Speed", "رفتار", "سرعت"),
        "pitch" to listOf("Pitch / tone", "زیر و بم", "زیر و بمی"),
        "speedHint" to listOf("Slower ↔ faster", "آہستہ ↔ تیز", "کندتر ↔ تندتر"),
        "pitchHint" to listOf("Lower ↔ higher", "بھاری ↔ باریک", "بم‌تر ↔ زیرتر")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        local = LocalStore(this)
        corpus = CorpusStore(this)
        themeChoice = ThemeChoice.from(local.getSetting("theme", "system"))
        language = local.getSetting("language", "en")
        speechSpeed = local.getSetting("speech_speed", "1.0").toFloatOrNull()?.coerceIn(0.75f, 1.25f) ?: 1.0f
        speechPitch = local.getSetting("speech_pitch", "1.0").toFloatOrNull()?.coerceIn(0.75f, 1.25f) ?: 1.0f
        palette = AppTheme.resolve(this, themeChoice)
        persianTypeface = Typeface.createFromAsset(assets, "assets/fonts/JameelNooriNastaleeq.ttf")
        serifTypeface = Typeface.createFromAsset(assets, "assets/fonts/InstrumentSerif-Regular.ttf")
        root = FrameLayout(this).apply {
            setBackgroundColor(palette.surface)
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                insets
            }
        }
        setContentView(root)
        root.requestApplyInsets()
        updateSystemBars()
        loadSplashQuote()
        startLibraryPreparation()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        stopPersianSpeech()
        if (intent.getBooleanExtra(OPEN_DAILY_EXTRA, false)) {
            pendingDailyOpen = true
            if (libraryReady) {
                pendingDailyOpen = false
                backStack.clear()
                screen = Screen.DAILY
                render()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        stopPersianSpeech()
        palette = AppTheme.resolve(this, themeChoice)
        updateSystemBars()
        render()
    }

    override fun onBackPressed() {
        stopPersianSpeech()
        if (screen == Screen.SEARCH) commitSearchQuery()
        when (screen) {
            Screen.SPLASH -> finishSplash()
            Screen.WELCOME -> finishWelcome()
            Screen.HOME -> super.onBackPressed()
            Screen.CATEGORY -> {
                if (categoryPath.size > 1) {
                    categoryPath.removeAt(categoryPath.lastIndex)
                    render()
                } else {
                    categoryPath.clear()
                    screen = if (backStack.isNotEmpty()) backStack.removeLast() else Screen.BOOK_POEMS
                    render()
                }
            }
            else -> {
                if (backStack.isNotEmpty()) screen = backStack.removeLast() else screen = Screen.HOME
                render()
            }
        }
    }

    override fun onDestroy() {
        stopPersianSpeech()
        if (::corpus.isInitialized) corpus.close()
        super.onDestroy()
    }

    private fun installPoetPack(poet: Poet) {
        if (poet.downloaded || installingPoetId == poet.id) return
        installingPoetId = poet.id
        render()
        Thread({
            val installed = try { corpus.downloadPackFromUrl(poet.id) } catch (_: Exception) { false }
            mainHandler.post {
                installingPoetId = null
                if (isFinishing || isDestroyed) return@post
                render()
                if (installed) Toast.makeText(this, s("downloaded"), Toast.LENGTH_SHORT).show()
                else AlertDialog.Builder(this).setTitle(poet.englishName).setMessage(s("downloadFailed"))
                    .setPositiveButton("OK", null).show()
            }
        }, "sukhan-poet-pack-install").start()
    }

    private fun startLibraryPreparation() {
        if (libraryPreparing) return
        libraryPreparing = true
        librarySetupError = false
        screen = Screen.SPLASH
        render()
        Thread({
            val ready = try { corpus.prepareBundledPacks() } catch (_: Exception) { false }
            mainHandler.post {
                libraryPreparing = false
                libraryReady = ready
                librarySetupError = !ready
                if (isFinishing || isDestroyed) return@post
                if (ready) chooseLaunchScreen() else render()
            }
        }, "sukhan-library-setup").start()
    }

    private fun chooseLaunchScreen() {
        if (!libraryReady) {
            screen = Screen.SPLASH
        } else if (pendingDailyOpen || intent.getBooleanExtra(OPEN_DAILY_EXTRA, false)) {
            pendingDailyOpen = false
            screen = Screen.DAILY
        } else {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val savedDay = local.getSetting("splash_day")
            val count = if (today == savedDay) local.getSetting("splash_count", "0").toIntOrNull() ?: 0 else 0
            if (count < 3) {
                local.setSetting("splash_day", today)
                local.setSetting("splash_count", (count + 1).toString())
                screen = Screen.SPLASH
            } else {
                screen = if (local.getSetting("welcome_done") == "1") Screen.HOME else Screen.WELCOME
            }
        }
        render()
        if (screen == Screen.SPLASH) mainHandler.postDelayed({ finishSplash() }, 4000L)
    }

    private fun loadSplashQuote() {
        try {
            val json = assets.open("assets/data/splash_quotes.json").bufferedReader().use { it.readText() }
            val quotes = JSONObject(json).getJSONArray("quotes")
            if (quotes.length() > 0) {
                val first = quotes.getJSONObject(0)
                splashQuote = first.optString("urdu", "")
                splashSource = first.optString("source", "")
            }
        } catch (_: Exception) {
            splashQuote = "خودی کو کر بلند اتنا"
            splashSource = "بانگِ درا"
        }
    }

    private fun finishSplash() {
        if (screen != Screen.SPLASH || !libraryReady) return
        screen = if (local.getSetting("welcome_done") == "1") Screen.HOME else Screen.WELCOME
        render()
    }

    private fun finishWelcome() {
        local.setSetting("welcome_done", "1")
        screen = Screen.HOME
        backStack.clear()
        render()
    }

    private fun s(key: String): String {
        val values = labels[key] ?: return key
        val index = when (language) { "ur" -> 1; "fa" -> 2; else -> 0 }
        return values[index]
    }

    private fun scheduleSearchSave(value: String) {
        searchSaveRunnable?.let(mainHandler::removeCallbacks)
        searchSaveRunnable = null
        val query = value.trim()
        if (query.isEmpty()) return
        val pending = Runnable {
            if (screen == Screen.SEARCH && searchQuery.trim() == query) local.addRecentSearch(query)
        }
        searchSaveRunnable = pending
        mainHandler.postDelayed(pending, 650L)
    }

    private fun commitSearchQuery() {
        searchSaveRunnable?.let(mainHandler::removeCallbacks)
        searchSaveRunnable = null
        searchQuery.trim().takeIf { it.isNotEmpty() }?.let(local::addRecentSearch)
    }

    private fun go(screen: Screen, keepBack: Boolean = true) {
        if (this.screen != screen && (this.screen == Screen.POEM_DETAIL || this.screen == Screen.DAILY)) stopPersianSpeech()
        if (this.screen == Screen.SEARCH && screen != Screen.SEARCH) commitSearchQuery()
        if (keepBack && this.screen != Screen.SPLASH && this.screen != Screen.WELCOME && this.screen != screen) backStack.addLast(this.screen)
        this.screen = screen
        render()
    }

    private fun goTop(screen: Screen) {
        if (this.screen != screen && (this.screen == Screen.POEM_DETAIL || this.screen == Screen.DAILY)) stopPersianSpeech()
        if (this.screen == Screen.SEARCH && screen != Screen.SEARCH) commitSearchQuery()
        backStack.clear()
        this.screen = screen
        render()
    }

    private fun render() {
        if (!::root.isInitialized) return
        palette = AppTheme.resolve(this, themeChoice)
        updateSystemBars()
        when (screen) {
            Screen.SPLASH -> renderSplash()
            Screen.WELCOME -> renderWelcome()
            Screen.HOME -> renderHome()
            Screen.BOOKS -> renderBooks()
            Screen.POEMS -> renderPoems(null)
            Screen.SEARCH -> renderSearch()
            Screen.FAVORITES -> renderFavorites()
            Screen.DAILY -> renderDaily()
            Screen.BOOK_POEMS -> renderPoems(selectedPoet)
            Screen.CATEGORY -> renderCategoryContents()
            Screen.POEM_DETAIL -> renderPoemDetail()
        }
    }

    private fun updateSystemBars() {
        if (!::palette.isInitialized) return
        window.statusBarColor = palette.surface
        window.navigationBarColor = palette.surface
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (palette.isDark) 0 else (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
    }

    private fun renderSplash() {
        root.removeAllViews()
        root.setBackgroundColor(palette.surface)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(36), dp(32), dp(28))
            setOnClickListener { finishSplash() }
        }
        val logo = ImageView(this).apply {
            setImageBitmap(loadBitmap(splashLogoPath()))
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(170), dp(320)).apply { bottomMargin = dp(12) }
        }
        content.addView(logo)
        content.addView(text("P E R S I A N  P O E T R Y", 10f, palette.muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(2), 0, dp(24)) })
        content.addView(text(splashQuote, 23f, palette.ink, arabic = true).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; setPadding(0, dp(10), 0, dp(14)) })
        if (splashSource.isNotBlank()) content.addView(text(splashSource, 13f, palette.muted).apply { gravity = Gravity.CENTER })
        when {
            libraryPreparing -> {
                content.addView(ProgressBar(this).apply { isIndeterminate = true; indeterminateTintList = ColorStateList.valueOf(palette.ink) }, LinearLayout.LayoutParams(dp(34), dp(34)).apply { topMargin = dp(24) })
                content.addView(text(s("preparingLibrary"), 13f, palette.muted).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; setPadding(0, dp(12), 0, 0) })
            }
            librarySetupError -> {
                content.addView(text(s("librarySetupError"), 13f, palette.muted).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; setPadding(0, dp(24), 0, dp(12)) })
                content.addView(button(s("retry"), true) { startLibraryPreparation() }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })
            }
            else -> content.addView(text("Tap to continue", 13f, palette.muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(48), 0, 0) })
        }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
    }

    private fun splashLogoPath(): String = when (themeChoice) {
        ThemeChoice.DARK -> "assets/images/brand/sukhan-logo-dark.png"
        ThemeChoice.SEPIA -> "assets/images/brand/sukhan-logo-sepia.png"
        else -> "assets/images/brand/sukhan-logo-light.png"
    }

    private fun wordmarkLogoPath(): String = when (themeChoice) {
        ThemeChoice.DARK -> "assets/images/brand/sukhan-wordmark-dark.png"
        ThemeChoice.SEPIA -> "assets/images/brand/sukhan-wordmark-sepia.png"
        else -> "assets/images/brand/sukhan-wordmark-light.png"
    }

    private fun renderWelcome() {
        root.removeAllViews()
        root.setBackgroundColor(palette.surface)
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(20), dp(24), dp(24)) }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(ImageView(this).apply { setImageBitmap(loadBitmap(wordmarkLogoPath())); scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(dp(78), dp(84)))
        top.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        top.addView(text(if (welcomePage < 2) s("skip") else "", 14f, palette.muted).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(9), dp(14), dp(9))
            if (welcomePage < 2) setOnClickListener { welcomePage = 2; renderWelcome() }
        })
        outer.addView(top)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, dp(12), 0, dp(12)) }
        val imageName = when (welcomePage) { 0 -> "hero_book.webp"; 1 -> "hero_library.webp"; else -> "hero_quill.webp" }
        val hero = ImageView(this).apply {
            setImageBitmap(loadBitmap("assets/images/welcome/$imageName"))
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(-1, dp(280)).apply { bottomMargin = dp(18) }
            if (palette.isDark) colorFilter = invertFilter()
        }
        content.addView(hero)
        val title = when (welcomePage) { 0 -> s("welcomeTitle"); 1 -> s("welcomeLibrary"); else -> s("featured") }
        content.addView(text(title, 28f, palette.ink, serif = true).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; setPadding(0, dp(8), 0, dp(10)) })
        when (welcomePage) {
            0 -> content.addView(text(s("welcomeSub"), 16f, palette.muted).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; setPadding(dp(8), 0, dp(8), dp(18)) })
            1 -> {
                content.addView(text(s("welcomeLibrarySub"), 16f, palette.muted).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; setPadding(dp(8), 0, dp(8), dp(14)) })
                addWelcomeBullet(content, "welcomeRead", "welcomeReadSub")
                addWelcomeBullet(content, "welcomeInsight", "welcomeInsightSub")
            }
            else -> {
                content.addView(text("خودی کو کر بلند اتنا کہ ہر تقدیر سے پہلے\nخدا بندے سے خود پوچھے بتا تیری رضا کیا ہے", 23f, palette.ink, arabic = true).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; setPadding(0, dp(4), 0, dp(18)) })
                val languages = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
                listOf("en" to "EN", "ur" to "اردو", "fa" to "فارسی").forEach { (code, name) ->
                    languages.addView(chip(name, language == code) { language = code; local.setSetting("language", code); renderWelcome() }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
                }
                content.addView(languages)
            }
        }
        scroll.addView(content)
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val dots = text("${if (welcomePage == 0) "●" else "○"}   ${if (welcomePage == 1) "●" else "○"}   ${if (welcomePage == 2) "●" else "○"}", 14f, palette.muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(12)) }
        outer.addView(dots)
        outer.addView(button(if (welcomePage < 2) s("next") else s("begin"), true) {
            if (welcomePage < 2) { welcomePage++; renderWelcome() } else finishWelcome()
        })
        root.addView(outer, FrameLayout.LayoutParams(-1, -1))
    }

    private fun addWelcomeBullet(parent: LinearLayout, titleKey: String, subKey: String) {
        val row = card()
        row.orientation = LinearLayout.VERTICAL
        row.addView(text(s(titleKey), 16f, palette.ink, bold = true))
        row.addView(text(s(subKey), 14f, palette.muted).apply { setPadding(0, dp(4), 0, 0) })
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
    }

    private fun page(title: String, bottom: Boolean = false, back: Boolean = false): LinearLayout {
        root.removeAllViews()
        root.setBackgroundColor(palette.surface)
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(palette.surface) }
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), 0, dp(12), 0) }
        val leading = text(if (back) "‹" else "☰", 27f, palette.ink).apply {
            gravity = Gravity.CENTER
            setOnClickListener { if (back) onBackPressed() else showDrawer() }
        }
        toolbar.addView(leading, LinearLayout.LayoutParams(dp(48), dp(58)))
        toolbar.addView(text(title, 20f, palette.ink, serif = true).apply { gravity = Gravity.CENTER_VERTICAL; maxLines = 1 }, LinearLayout.LayoutParams(0, dp(58), 1f))
        val settings = text("⚙", 19f, palette.muted).apply { gravity = Gravity.CENTER; contentDescription = s("settings"); setOnClickListener { showSettingsSheet() } }
        toolbar.addView(settings, LinearLayout.LayoutParams(dp(42), dp(58)))
        outer.addView(toolbar)
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(10), dp(18), dp(22)); layoutDirection = layoutDirectionForLanguage() }
        scroll.addView(body)
        outer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        if (bottom) outer.addView(bottomBar())
        root.addView(outer, FrameLayout.LayoutParams(-1, -1))
        return outer
    }

    private fun bottomBar(): View {
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(dp(8), dp(5), dp(8), dp(8)); setBackgroundColor(palette.raised) }
        val items = listOf(Triple(Screen.HOME, "⌂", "home"), Triple(Screen.BOOKS, "▤", "books"), Triple(Screen.POEMS, "≡", "poems"), Triple(Screen.SEARCH, "⌕", "search"))
        items.forEach { (dest, glyph, key) ->
            val selected = screen == dest
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(0, dp(3), 0, dp(2)) }
            col.addView(text(glyph, 20f, if (selected) palette.ink else palette.muted).apply { gravity = Gravity.CENTER })
            col.addView(text(s(key), 11f, if (selected) palette.ink else palette.muted, bold = selected).apply { gravity = Gravity.CENTER })
            col.setOnClickListener { goTop(dest) }
            bar.addView(col, LinearLayout.LayoutParams(0, dp(53), 1f))
        }
        return bar
    }

    private fun renderHome() {
        page(s("today"), bottom = true)
        val date = SimpleDateFormat("EEEE, MMM d", Locale(language)).format(Date())
        body.addView(text(date, 13f, palette.muted).apply { setPadding(0, dp(4), 0, dp(14)) })
        body.addView(sectionTitle(s("featured"), s("showAll")) { go(Screen.DAILY) })
        addDailyCard(body, corpus.dailyVerse(), compact = true)
        body.addView(sectionTitle(s("books"), s("showAll")) { goTop(Screen.BOOKS) }.apply { setPadding(0, dp(20), 0, dp(8)) })
        val shelf = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        corpus.poets().take(12).forEachIndexed { index, poet ->
            row.addView(bookCard(poet, index, compact = true), LinearLayout.LayoutParams(dp(148), -2).apply { marginEnd = dp(12) })
        }
        shelf.addView(row)
        body.addView(shelf)
        body.addView(sectionTitle(s("explore"), null, null).apply { setPadding(0, dp(22), 0, dp(8)) })
        listOf("allPoems" to Screen.POEMS, "search" to Screen.SEARCH, "favorites" to Screen.FAVORITES).forEach { (key, dest) ->
            body.addView(actionRow("›", s(key)) { goTop(dest) }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
        }
    }

    private fun renderBooks() {
        page(s("books"), bottom = true)
        val poets = corpus.poets()
        body.addView(text("${poets.size} ${s("bookOf")}", 13f, palette.muted).apply { setPadding(0, 0, 0, dp(12)) })
        val columns = when { resources.configuration.screenWidthDp >= 900 -> 4; resources.configuration.screenWidthDp >= 600 -> 3; else -> 2 }
        for (start in poets.indices step columns) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
            for (col in 0 until columns) {
                val idx = start + col
                if (idx < poets.size) row.addView(bookCard(poets[idx], idx, false), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(5); marginEnd = dp(5); bottomMargin = dp(16) })
                else row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            }
            body.addView(row)
        }
    }

    private fun bookCard(poet: Poet, index: Int, compact: Boolean): View {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(4)) }
        val frame = FrameLayout(this).apply {
            background = rounded(palette.raised2, 12f)
            clipToOutline = true
        }
        val image = ImageView(this).apply {
            setImageBitmap(coverBitmap(index))
            scaleType = ImageView.ScaleType.CENTER_CROP
            if (!poet.downloaded) alpha = 0.48f
            if (palette.isDark) colorFilter = invertFilter()
        }
        frame.addView(image, FrameLayout.LayoutParams(-1, if (compact) dp(180) else dp(210)))
        if (!poet.downloaded) {
            if (installingPoetId == poet.id) {
                val progress = ProgressBar(this).apply { isIndeterminate = true }
                frame.addView(progress, FrameLayout.LayoutParams(dp(38), dp(38), Gravity.TOP or Gravity.START).apply { setMargins(dp(7), dp(7), 0, 0) })
            } else {
                val download = iconButton(R.drawable.ic_download_24, s("download")) { installPoetPack(poet) }
                frame.addView(download, FrameLayout.LayoutParams(dp(38), dp(38), Gravity.TOP or Gravity.START).apply { setMargins(dp(7), dp(7), 0, 0) })
            }
        }
        val favorite = text(if (local.isFavorite("book", poet.id)) "♥" else "♡", 18f, if (local.isFavorite("book", poet.id)) palette.ink else palette.muted).apply {
            gravity = Gravity.CENTER
            background = rounded(palette.surface, 18f)
            elevation = dp(2).toFloat()
        }
        val heartLp = FrameLayout.LayoutParams(dp(34), dp(34), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(7), dp(7), 0) }
        favorite.setOnClickListener { local.toggleFavorite("book", poet.id); render() }
        frame.addView(favorite, heartLp)
        wrap.addView(frame)
        wrap.addView(text(poet.name, if (compact) 14f else 15f, if (poet.downloaded) palette.ink else palette.muted, bold = true).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            maxLines = 2
            setPadding(dp(2), dp(7), dp(2), 0)
        })
        val size = if (poet.packSizeBytes > 0L) String.format(Locale.ROOT, "%.1f MB", poet.packSizeBytes / (1024f * 1024f)) else ""
        val subtitle = listOf(
            "${poet.poemCount} ${s("poemCount")}",
            if (poet.downloaded) s("readyOffline") else s("notDownloaded"),
            if (!poet.downloaded) size else ""
        ).filter(String::isNotBlank).joinToString(" · ")
        wrap.addView(text(subtitle, 12f, palette.muted).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; setPadding(0, dp(3), 0, 0) })
        wrap.setOnClickListener {
            if (poet.downloaded) {
                selectedPoet = poet
                categoryPath.clear()
                go(Screen.BOOK_POEMS)
            } else {
                installPoetPack(poet)
            }
        }
        wrap.setOnLongClickListener { showBookOptions(poet, index); true }
        return wrap
    }

    private fun coverBitmap(index: Int): Bitmap? {
        val coverId = when (index) { 0 -> 1; 1 -> 2; 2 -> 3; 3 -> 4; else -> 5 }
        return coverCache.getOrPut(coverId) { loadBitmap("assets/images/covers/book_$coverId.png") ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) }
    }

    private fun renderPoems(poet: Poet?) {
        loadedPoemOffset = 0
        loadMorePoemsButton = null
        if (poet != null) {
            renderPoetCollections(poet)
            return
        }
        page(s("poems"), bottom = true)
        body.addView(actionRow("⌕", s("typeSearch")) { goTop(Screen.SEARCH) }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val totalPoems = corpus.poets().filter { it.downloaded }.sumOf { it.poemCount }
        appendPoemPage(null, totalPoems)
    }

    private fun renderPoetCollections(poet: Poet) {
        page(poet.name, back = true)
        body.addView(text("${s("collections")} · ${poet.poemCount} ${s("poemCount")}", 13f, palette.muted).apply { setPadding(0, 0, 0, dp(12)) })
        val categories = corpus.rootCategories(poet.id)
        categories.forEachIndexed { index, category ->
            body.addView(categoryRow(category, index), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }

        val root = corpus.rootCategory(poet.id)
        if (root != null && categories.none { it.id == root.id } && root.poemCount > 0) {
            if (categories.isNotEmpty()) body.addView(sectionTitle(s("poems"), null, null).apply { setPadding(0, dp(14), 0, dp(8)) })
            appendPoemPage(poet, root.poemCount, root.id)
        } else if (categories.isEmpty()) {
            appendPoemPage(poet, poet.poemCount)
        }
    }

    private fun renderCategoryContents() {
        val poet = selectedPoet ?: run { goTop(Screen.BOOKS); return }
        val category = categoryPath.lastOrNull() ?: run { screen = Screen.BOOK_POEMS; render(); return }
        page(category.title, back = true)
        val breadcrumb = (listOf(poet.name) + categoryPath.map { it.title }).joinToString("  ›  ")
        body.addView(text(breadcrumb, 12f, palette.muted).apply { setPadding(0, 0, 0, dp(12)) })

        loadedPoemOffset = 0
        loadMorePoemsButton = null
        val children = corpus.childCategories(poet.id, category.id)
        val summary = buildList {
            if (category.childCategoryCount > 0) add("${category.childCategoryCount} ${s("chapters")}")
            if (category.poemCount > 0) add("${category.poemCount} ${s("poemCount")}")
        }
        if (summary.isNotEmpty()) body.addView(text(summary.joinToString(" · "), 13f, palette.muted).apply { setPadding(0, 0, 0, dp(12)) })
        children.forEachIndexed { index, child ->
            body.addView(categoryRow(child, index), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        if (category.poemCount > 0) {
            if (children.isNotEmpty()) body.addView(sectionTitle(s("poems"), null, null).apply { setPadding(0, dp(14), 0, dp(8)) })
            appendPoemPage(poet, category.poemCount, category.id)
        } else if (children.isEmpty()) {
            body.addView(text(s("emptyCollection"), 15f, palette.muted).apply { setPadding(0, dp(18), 0, 0) })
        }
    }

    private fun appendPoemPage(poet: Poet?, totalPoems: Int, categoryId: Int? = null) {
        loadMorePoemsButton?.let { body.removeView(it) }
        loadMorePoemsButton = null
        val poems = if (categoryId != null && poet != null) {
            corpus.poemsInCategory(poet.id, categoryId, poemPageSize, loadedPoemOffset)
        } else {
            corpus.poems(poet?.id, limit = poemPageSize, offset = loadedPoemOffset)
        }
        if (poems.isEmpty()) {
            if (loadedPoemOffset == 0 && totalPoems > 0) body.addView(text(s("noPoems"), 16f, palette.muted).apply { setPadding(0, dp(20), 0, 0) })
            return
        }
        poems.forEachIndexed { index, poem ->
            body.addView(poemRow(poem, loadedPoemOffset + index + 1), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        loadedPoemOffset += poems.size
        if (loadedPoemOffset < totalPoems) {
            val more = button(s("loadMore"), false) { appendPoemPage(poet, totalPoems, categoryId) }
            loadMorePoemsButton = more
            body.addView(more, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8); bottomMargin = dp(16) })
        }
    }

    private fun categoryRow(category: PoetCategory, index: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(palette.raised, 12f, palette.line)
        }
        val glyph = if (category.categoryType.equals("Book", ignoreCase = true)) "▤" else "▥"
        row.addView(text(glyph, 22f, palette.ink).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(38), dp(38)))
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(text(category.title, 17f, palette.ink, bold = category.categoryType.equals("Book", ignoreCase = true), arabic = true))
        val details = buildList {
            if (category.childCategoryCount > 0) add("${category.childCategoryCount} ${s("chapters")}")
            if (category.poemCount > 0) add("${category.poemCount} ${s("poemCount")}")
        }.joinToString(" · ")
        if (details.isNotEmpty()) info.addView(text(details, 12f, palette.muted).apply { setPadding(0, dp(4), 0, 0) })
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) })
        row.addView(text("›", 22f, palette.muted).apply { gravity = Gravity.CENTER })
        row.setOnClickListener {
            categoryPath.add(category)
            if (screen == Screen.BOOK_POEMS) go(Screen.CATEGORY) else render()
        }
        return row
    }

    private fun poemRow(poem: Poem, number: Int): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(13), dp(14), dp(13)); background = rounded(palette.raised, 12f, palette.line) }
        row.addView(text(number.toString().padStart(2, '0'), 13f, palette.muted, serif = true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(34), -2))
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(text(poem.title, 17f, palette.ink, arabic = true))
        info.addView(text("${poem.poetName} · ${poem.verseCount} ${s("verseCount")}", 12f, palette.muted).apply { setPadding(0, dp(3), 0, 0) })
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(9) })
        row.addView(text("›", 22f, palette.muted).apply { gravity = Gravity.CENTER })
        row.setOnClickListener { openPoem(poem) }
        row.setOnLongClickListener { showPoemOptions(poem); true }
        return row
    }

    private fun openPoem(poem: Poem, verseOrder: Int = -1) {
        selectedPoem = poem
        focusedVerseOrder = verseOrder
        go(Screen.POEM_DETAIL)
    }

    private fun renderPoemDetail() {
        val poem = selectedPoem ?: run { goTop(Screen.POEMS); return }
        page(poem.poetName, back = true)
        body.addView(text(poem.title, 25f, palette.ink, serif = true).apply { textAlignment = View.TEXT_ALIGNMENT_CENTER; gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(3)) })
        body.addView(text("${poem.poetName} · ${poem.verseCount} ${s("verseCount")}", 12f, palette.muted).apply { textAlignment = View.TEXT_ALIGNMENT_CENTER; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(14)) })
        if (poem.englishTranslationCount > 0 || poem.urduTranslationCount > 0) {
            val translationSummary = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(12)) }
            if (poem.englishTranslationCount > 0) translationSummary.addView(translationBadge("EN · ${poem.englishTranslationCount}"), LinearLayout.LayoutParams(-2, dp(28)).apply { marginEnd = dp(5) })
            if (poem.urduTranslationCount > 0) translationSummary.addView(translationBadge("UR · ${poem.urduTranslationCount}"), LinearLayout.LayoutParams(-2, dp(28)).apply { marginEnd = dp(5) })
            if (poem.humanReviewStatus == "not_human_reviewed") {
                translationSummary.addView(translationBadge("${s("aiDraft")} · ${s("reviewPending")}", draft = true), LinearLayout.LayoutParams(-2, dp(28)))
            }
            body.addView(translationSummary, LinearLayout.LayoutParams(-1, -2))
        }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(13)) }
        val fav = if (local.isFavorite("poem", poem.id)) "♥" else "♡"
        val actionViews: List<View> = listOf(
            button(fav, false) { local.toggleFavorite("poem", poem.id); render() },
            button("A−", false) { fontScale = (fontScale - 0.08f).coerceAtLeast(0.8f); render() },
            button("A+", false) { fontScale = (fontScale + 0.08f).coerceAtMost(1.5f); render() },
            iconButton(R.drawable.ic_share_24, s("share")) { ShareActions(this, persianTypeface).show(poem, corpus.verses(poem.id), palette) },
            button("⋯", false) { showPoemOptions(poem) }
        )
        actionViews.forEach { action -> actions.addView(action, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(3); marginEnd = dp(3) }) }
        body.addView(actions)
        val verses = corpus.verses(poem.id)
        val listenButton = button(s("listen"), false) {}
        listenButton.setOnClickListener { togglePersianSpeech(verses.joinToString("\n") { it.persian }, listenButton) }
        listenButton.isEnabled = verses.isNotEmpty()
        body.addView(listenButton, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(8) })
        if (verses.isEmpty()) body.addView(text(s("noPoems"), 15f, palette.muted))
        verses.forEach { verse ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(17), dp(10), dp(17))
                background = rounded(if (verse.order == focusedVerseOrder) palette.raised2 else palette.surface, 10f)
            }
            item.addView(text(verse.persian, 25f * fontScale, palette.ink, arabic = true).apply {
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                gravity = Gravity.CENTER
                setLineSpacing(dp(6).toFloat(), 1.1f)
                layoutDirection = View.LAYOUT_DIRECTION_RTL
            })
            verse.english?.takeIf { it.isNotBlank() }?.let {
                val draft = verse.englishStatus == "ai_generated_draft" || verse.humanReviewStatus == "not_human_reviewed"
                item.addView(translationBadge(if (draft) "EN · ${s("aiDraft")}" else "EN · ✓", draft))
                item.addView(text(it, 15f, palette.muted).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; setPadding(dp(4), dp(5), dp(4), 0) })
            }
            verse.urdu?.takeIf { it.isNotBlank() }?.let {
                val draft = verse.urduStatus == "ai_generated_draft" || verse.humanReviewStatus == "not_human_reviewed"
                item.addView(translationBadge(if (draft) "UR · ${s("aiDraft")}" else "UR · ✓", draft))
                item.addView(text(it, 18f, palette.muted, arabic = true).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; setPadding(dp(4), dp(5), dp(4), 0) })
            }
            item.setOnClickListener { showAiUnavailable(s("analyze")) }
            item.setOnLongClickListener { showNoteEditor(poem.id, verse.order); true }
            body.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(5) })
        }
        body.addView(aiCard(), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        val noteButton = button("${s("notes")} (${local.notesForPoem(poem.id).size})", false) { showNotes(poem) }
        body.addView(noteButton, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(10) })
    }

    private fun renderSearch() {
        page(s("search"), bottom = true)
        val input = EditText(this).apply {
            setText(searchQuery)
            setTextColor(palette.ink)
            setHintTextColor(palette.muted)
            hint = s("typeSearch")
            textSize = 16f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            background = rounded(palette.raised, 12f, palette.line)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            textDirection = if (language == "en") View.TEXT_DIRECTION_FIRST_STRONG else View.TEXT_DIRECTION_RTL
        }
        body.addView(input, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(10) })
        val filters = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        SearchFilter.entries.forEach { f ->
            val key = when (f) { SearchFilter.ALL -> "all"; SearchFilter.BOOKS -> "books"; SearchFilter.POEMS -> "poems"; SearchFilter.LINES -> "lines" }
            filters.addView(chip(s(key), searchFilter == f) { searchFilter = f; renderSearch() }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(5) })
        }
        body.addView(filters)
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(12), 0, 0) }
        body.addView(results)
        searchResultsContainer = results
        renderSearchResults(results)
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty()
                scheduleSearchSave(searchQuery)
                searchResultsContainer?.let { renderSearchResults(it) }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH && searchQuery.isNotBlank()) { commitSearchQuery(); true } else false
        }
    }

    private fun renderSearchResults(target: LinearLayout) {
        target.removeAllViews()
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            head.addView(text(s("recentSearches"), 16f, palette.ink, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
            val recent = local.recentSearches()
            if (recent.isNotEmpty()) head.addView(text(s("clear"), 13f, palette.muted).apply {
                setOnClickListener {
                    searchSaveRunnable?.let(mainHandler::removeCallbacks)
                    searchSaveRunnable = null
                    local.clearRecentSearches()
                    renderSearchResults(target)
                }
            })
            target.addView(head)
            if (recent.isEmpty()) target.addView(text(s("noRecentSearches"), 14f, palette.muted).apply { setPadding(0, dp(10), 0, 0) })
            recent.forEach { term -> target.addView(chip(term, false) { searchQuery = term; commitSearchQuery(); renderSearch() }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) }) }
            return
        }
        val hits = when (searchFilter) {
            SearchFilter.BOOKS -> corpus.searchBooks(query)
            SearchFilter.POEMS -> corpus.searchPoems(query)
            SearchFilter.LINES -> corpus.searchLines(query)
            SearchFilter.ALL -> corpus.searchBooks(query, 10) + corpus.searchPoems(query, 20) + corpus.searchLines(query, 20)
        }
        if (hits.isEmpty()) target.addView(text(s("noResults"), 15f, palette.muted).apply { setPadding(0, dp(14), 0, 0) })
        hits.take(50).forEach { hit ->
            val row = actionRow(when (hit.kind) { "book" -> "▤"; "poem" -> "≡"; else -> "⌕" }, hit.title, hit.subtitle) {}
            row.setOnClickListener {
                local.addRecentSearch(query)
                when (hit.kind) {
                    "book" -> { selectedPoet = corpus.poet(hit.id); categoryPath.clear(); go(Screen.BOOK_POEMS) }
                    "poem" -> corpus.poem(hit.id)?.let { openPoem(it) }
                    else -> corpus.poem(hit.id)?.let { openPoem(it, hit.verseOrder) }
                }
            }
            target.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
        }
    }

    private fun renderFavorites() {
        page(s("favorites"), back = true)
        val switch = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        switch.addView(chip(s("books"), favoritesTab == 0) { favoritesTab = 0; renderFavorites() }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(5) })
        switch.addView(chip(s("poems"), favoritesTab == 1) { favoritesTab = 1; renderFavorites() }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(5) })
        body.addView(switch, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        if (favoritesTab == 0) {
            val saved = corpus.poets().filter { local.isFavorite("book", it.id) }
            if (saved.isEmpty()) body.addView(text(s("emptyFavorites"), 15f, palette.muted).apply { setPadding(0, dp(18), 0, 0) })
            saved.forEachIndexed { index, poet -> body.addView(bookCard(poet, index, false), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }) }
        } else {
            val ids = local.favoriteIds("poem")
            val saved = corpus.poems().filter { ids.contains(it.id) }
            if (saved.isEmpty()) body.addView(text(s("emptyFavorites"), 15f, palette.muted).apply { setPadding(0, dp(18), 0, 0) })
            saved.forEachIndexed { i, poem -> body.addView(poemRow(poem, i + 1), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) }) }
        }
    }

    private fun renderDaily() {
        page(s("daily"), back = true)
        body.addView(text(s("dailyIntro"), 14f, palette.muted).apply { setPadding(0, 0, 0, dp(12)) })
        val verse = corpus.dailyVerse()
        addDailyCard(body, verse, compact = false)
        val poem = verse?.let { corpus.poemForVerse(it.poemId) }
        if (poem != null) body.addView(button(s("read"), true) { openPoem(poem, verse.order) }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(10) })
        body.addView(aiCard(), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        verse?.let { v ->
            val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            controls.addView(button(s("copy"), false) { copyText(v.persian) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(5) })
            controls.addView(button(s("share"), false) { shareText(v.persian) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(5) })
            body.addView(controls, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            val listenButton = button(s("listen"), false) {}
            listenButton.setOnClickListener { togglePersianSpeech(v.persian, listenButton) }
            body.addView(listenButton, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(7) })
            body.addView(button(s("shareImage"), false) {
                DailyQuoteShare.share(this, v, corpus.poemForVerse(v.poemId), palette, persianTypeface, serifTypeface, s("shareImage"))
            }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(7) })
        }
    }

    private fun togglePersianSpeech(value: String, button: TextView) {
        if (PersianTts.isActive && activeTtsButton === button) {
            stopPersianSpeech()
            return
        }
        if (PersianTts.isActive) stopPersianSpeech()
        activeTtsButton = button
        button.text = s("preparingVoice")
        PersianTts.speak(this, value, speechSpeed, speechPitch, object : PersianTts.Listener {
            override fun onPreparing() { if (activeTtsButton === button) button.text = s("preparingVoice") }
            override fun onStarted() { if (activeTtsButton === button) button.text = s("stopReading") }
            override fun onFinished() {
                if (activeTtsButton === button) {
                    button.text = s("listen")
                    activeTtsButton = null
                }
            }
            override fun onError() {
                if (activeTtsButton === button) {
                    button.text = s("listen")
                    activeTtsButton = null
                    Toast.makeText(this@MainActivity, s("ttsError"), Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun stopPersianSpeech() {
        if (!PersianTts.isActive && activeTtsButton == null) return
        PersianTts.stop()
        activeTtsButton?.text = s("listen")
        activeTtsButton = null
    }

    private fun showSettingsSheet() {
        SettingsSheet(
            activity = this,
            palette = palette,
            themeChoice = themeChoice,
            language = language,
            label = ::s,
            arabicTypeface = persianTypeface,
            serifTypeface = serifTypeface,
            cacheSize = cacheSizeLabel(),
            speechSpeed = speechSpeed,
            speechPitch = speechPitch,
            onThemeChange = { choice ->
                themeChoice = choice
                local.setSetting("theme", choice.key)
                render()
                DailyQuoteWidgetProvider.updateAll(this)
            },
            onLanguageChange = { code ->
                language = code
                local.setSetting("language", code)
                render()
                DailyQuoteWidgetProvider.updateAll(this)
            },
            onSpeechSpeedChange = { value ->
                speechSpeed = value.coerceIn(0.75f, 1.25f)
                local.setSetting("speech_speed", String.format(Locale.ROOT, "%.3f", speechSpeed))
            },
            onSpeechPitchChange = { value ->
                speechPitch = value.coerceIn(0.80f, 1.20f)
                local.setSetting("speech_pitch", String.format(Locale.ROOT, "%.3f", speechPitch))
            },
            onClearCache = {
                clearTemporaryFiles()
                root.post { showSettingsSheet() }
            },
            onAbout = ::showAboutDialog
        ).show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Sukhan · سخن")
            .setMessage("${s("offlineInfo")}\n\n245 poets · 2 bundled offline · 243 optional individual packs\nLocal SQLite · optional HTTPS pack downloads\nPersian offline voice included (~64 MB). GPL-3.0 phonemizer notices are bundled; review before public distribution.\nAI provider hook prepared for later integration.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun addDailyCard(parent: LinearLayout, verse: Verse?, compact: Boolean) {
        if (verse == null) return
        val card = card()
        card.orientation = LinearLayout.VERTICAL
        card.addView(text(s("daily"), 12f, palette.muted, bold = true))
        card.addView(text(verse.persian, if (compact) 22f else 27f, palette.ink, arabic = true).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setLineSpacing(dp(5).toFloat(), 1.1f)
            setPadding(0, dp(13), 0, dp(10))
        })
        val poem = corpus.poemForVerse(verse.poemId)
        if (poem != null) card.addView(text("${poem.title} · ${poem.poetName}", 12f, palette.muted).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER })
        if (!compact) {
            verse.english?.takeIf { it.isNotBlank() }?.let { card.addView(text(it, 15f, palette.muted).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; setPadding(0, dp(12), 0, 0) }) }
            verse.urdu?.takeIf { it.isNotBlank() }?.let { card.addView(text(it, 18f, palette.muted, arabic = true).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; setPadding(0, dp(12), 0, 0) }) }
        }
        parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }

    private fun aiCard(): View {
        val card = card()
        card.orientation = LinearLayout.VERTICAL
        card.addView(text(s("analyze"), 15f, palette.ink, bold = true))
        card.addView(text(if (AiIntegration.isConfigured) AiIntegration.unavailableMessage() else s("aiExplain"), 13f, palette.muted).apply { setPadding(0, dp(5), 0, dp(9)) })
        card.addView(button(s("analyze"), false) { showAiUnavailable(s("analyze")) })
        return card
    }

    private fun sectionTitle(title: String, action: String?, click: (() -> Unit)?): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(4), 0, dp(9)) }
        row.addView(text(title, 18f, palette.ink, serif = true), LinearLayout.LayoutParams(0, -2, 1f))
        if (action != null && click != null) row.addView(text(action, 13f, palette.muted).apply { setOnClickListener { click() } })
        return row
    }

    private fun actionRow(icon: String, title: String, subtitle: String? = null, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(13), dp(12), dp(13), dp(12)); background = rounded(palette.raised, 12f, palette.line) }
        row.addView(text(icon, 20f, palette.muted).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(36), dp(36)))
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(text(title, 15f, palette.ink, bold = true))
        subtitle?.let { info.addView(text(it, 12f, palette.muted).apply { setPadding(0, dp(3), 0, 0) }) }
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) })
        row.addView(text("›", 21f, palette.muted))
        row.setOnClickListener { onClick() }
        return row
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(14), dp(15), dp(14))
        background = rounded(palette.raised, 14f, palette.line)
    }

    private fun chip(label: String, selected: Boolean, onClick: () -> Unit): TextView = text(label, 13f, if (selected) palette.surface else palette.ink, bold = selected).apply {
        gravity = Gravity.CENTER
        textAlignment = View.TEXT_ALIGNMENT_CENTER
        background = rounded(if (selected) palette.ink else palette.raised, 22f, if (selected) palette.ink else palette.line)
        setPadding(dp(8), 0, dp(8), 0)
        setOnClickListener { onClick() }
    }

    private fun translationBadge(value: String, draft: Boolean = false): TextView = text(value, 10.5f, palette.ink, bold = true).apply {
        gravity = Gravity.CENTER
        textAlignment = View.TEXT_ALIGNMENT_CENTER
        background = rounded(if (draft) palette.raised2 else palette.raised, 10f, palette.line)
        setPadding(dp(7), dp(4), dp(7), dp(4))
    }

    private fun button(label: String, primary: Boolean, onClick: () -> Unit): TextView = text(label, 14f, if (primary) palette.surface else palette.ink, bold = true).apply {
        gravity = Gravity.CENTER
        textAlignment = View.TEXT_ALIGNMENT_CENTER
        background = rounded(if (primary) palette.ink else palette.raised2, 12f, if (primary) palette.ink else palette.line)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        setOnClickListener { onClick() }
    }

    private fun iconButton(drawableId: Int, description: String, onClick: () -> Unit): View = FrameLayout(this).apply {
        background = rounded(palette.raised2, 12f, palette.line)
        isClickable = true
        isFocusable = true
        contentDescription = description
        val icon = ImageView(this@MainActivity).apply {
            setImageResource(drawableId)
            setColorFilter(palette.ink)
            contentDescription = description
        }
        addView(icon, FrameLayout.LayoutParams(dp(21), dp(21), Gravity.CENTER))
        setOnClickListener { onClick() }
    }

    private fun rounded(color: Int, radiusDp: Float, stroke: Int? = null): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        stroke?.let { setStroke(dp(1), it) }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false, serif: Boolean = false, arabic: Boolean = false): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        typeface = when { arabic || containsArabic(value) -> persianTypeface; serif -> if (bold) Typeface.create(serifTypeface, Typeface.BOLD) else serifTypeface; bold -> Typeface.create("sans-serif", Typeface.BOLD); else -> Typeface.create("sans-serif", Typeface.NORMAL) }
        includeFontPadding = false
        layoutDirection = if (containsArabic(value)) View.LAYOUT_DIRECTION_RTL else layoutDirectionForLanguage()
    }

    private fun containsArabic(value: String): Boolean = value.any { it.code in 0x0600..0x08FF }
    private fun layoutDirectionForLanguage(): Int = if (language == "ur" || language == "fa") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
    private fun dp(value: Float): Int = AppTheme.dp(this, value)
    private fun dp(value: Int): Int = AppTheme.dp(this, value.toFloat())

    private fun loadBitmap(path: String): Bitmap? = try { assets.open(path).use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null }

    private fun invertFilter(): ColorMatrixColorFilter {
        val matrix = ColorMatrix(floatArrayOf(-1f,0f,0f,0f,255f, 0f,-1f,0f,0f,255f, 0f,0f,-1f,0f,255f, 0f,0f,0f,1f,0f))
        return ColorMatrixColorFilter(matrix)
    }

    private fun showDrawer() {
        val dialog = Dialog(this)
        val overlay = FrameLayout(this).apply { setBackgroundColor(Color.TRANSPARENT) }
        val scrim = View(this).apply { setBackgroundColor(0x77000000.toInt()); setOnClickListener { dialog.dismiss() } }
        overlay.addView(scrim, FrameLayout.LayoutParams(-1, -1))
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(28), dp(18), dp(20)); setBackgroundColor(palette.surface); elevation = dp(12).toFloat() }
        val width = minOf(dp(330), resources.displayMetrics.widthPixels - dp(42))
        val panelLp = FrameLayout.LayoutParams(width, -1, if (language == "ur" || language == "fa") Gravity.END else Gravity.START)
        overlay.addView(panel, panelLp)
        panel.addView(text("سخن", 30f, palette.ink, arabic = true).apply { setPadding(0, 0, 0, dp(4)) })
        panel.addView(text("P E R S I A N  P O E T R Y", 10f, palette.muted).apply { setPadding(0, 0, 0, dp(18)) })
        val items = listOf("home" to Screen.HOME, "books" to Screen.BOOKS, "poems" to Screen.POEMS, "search" to Screen.SEARCH, "favorites" to Screen.FAVORITES, "daily" to Screen.DAILY)
        items.forEach { (key, dest) ->
            panel.addView(actionRow("·", s(key)) { dialog.dismiss(); goTop(dest) }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
        }
        panel.addView(actionRow("⚙", s("settings")) { dialog.dismiss(); root.post { showSettingsSheet() } }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        dialog.setContentView(overlay)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setDimAmount(0f)
        dialog.show()
        dialog.window?.setLayout(-1, -1)
    }

    private fun showBookOptions(poet: Poet, index: Int) {
        val favoriteLabel = if (local.isFavorite("book", poet.id)) s("removeFavorite") else s("addFavorite")
        AlertDialog.Builder(this).setTitle(s("bookActions")).setItems(arrayOf(favoriteLabel, s("share"), s("daily"))) { _, which ->
            when (which) {
                0 -> { local.toggleFavorite("book", poet.id); render() }
                1 -> shareText(poet.name)
                2 -> go(Screen.DAILY)
            }
        }.show()
    }

    private fun showPoemOptions(poem: Poem) {
        val favoriteLabel = if (local.isFavorite("poem", poem.id)) s("removeFavorite") else s("addFavorite")
        AlertDialog.Builder(this).setTitle(s("poemActions")).setItems(arrayOf(favoriteLabel, s("notes"), s("analyze"), s("context"), s("share"))) { _, which ->
            when (which) {
                0 -> { local.toggleFavorite("poem", poem.id); render() }
                1 -> showNotes(poem)
                2 -> showAiUnavailable(s("analyze"))
                3 -> showAiUnavailable(s("context"))
                4 -> ShareActions(this, persianTypeface).show(poem, corpus.verses(poem.id), palette)
            }
        }.show()
    }

    private fun showAiUnavailable(title: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(AiIntegration.unavailableMessage() + "\n\n" + s("aiExplain"))
            .setPositiveButton("OK", null).show()
    }

    private fun showNotes(poem: Poem) {
        val notes = local.notesForPoem(poem.id)
        val options = notes.map { note ->
            val verse = note.verseOrder?.let { " · ${s("lines")} ${it + 1}" } ?: ""
            "${poem.title}$verse\n${note.text}"
        }.toMutableList()
        options.add(s("addNote"))
        AlertDialog.Builder(this).setTitle(s("notes")).setItems(options.toTypedArray()) { _, which ->
            if (which == notes.size) showNoteEditor(poem.id, null)
            else notes.getOrNull(which)?.let { showNoteEditor(poem.id, it.verseOrder, it.text) }
        }.setNegativeButton(s("cancel"), null).show().also {
            if (notes.isEmpty()) Toast.makeText(this, s("noNotes"), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showNoteEditor(poemId: Int, verseOrder: Int?, existing: String = "") {
        val editor = EditText(this).apply {
            setText(existing)
            setTextColor(palette.ink)
            setHintTextColor(palette.muted)
            hint = s("addNote")
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(palette.raised, 10f, palette.line)
        }
        AlertDialog.Builder(this).setTitle(if (existing.isBlank()) s("addNote") else s("editNote"))
            .setView(editor)
            .setPositiveButton(s("save")) { _, _ ->
                val note = editor.text.toString().trim()
                if (note.isNotEmpty()) local.saveNote(poemId, verseOrder, note)
                Toast.makeText(this, s("save"), Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Delete") { _, _ -> local.notesForPoem(poemId).firstOrNull { it.verseOrder == verseOrder }?.let { local.deleteNote(it.id) } }
            .setNegativeButton(s("cancel"), null).show()
    }

    private fun clearTemporaryFiles() {
        val shared = File(cacheDir, "shared")
        shared.deleteRecursively()
        cacheDir.listFiles()?.filter { it.name != "shared" }?.forEach { it.deleteRecursively() }
        Toast.makeText(this, s("cacheCleared"), Toast.LENGTH_SHORT).show()
    }

    private fun cacheSizeLabel(): String {
        val bytes = cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return if (bytes < 1024L * 1024L) "${bytes / 1024L} KB" else String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
    }

    private fun copyText(value: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Sukhan", value))
        Toast.makeText(this, s("copied"), Toast.LENGTH_SHORT).show()
    }

    private fun shareText(value: String) {
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, value) }
        startActivity(Intent.createChooser(send, s("share")))
    }
}
