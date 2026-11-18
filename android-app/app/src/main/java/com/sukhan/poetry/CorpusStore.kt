package com.sukhan.poetry

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Calendar
import java.util.Locale

internal data class Poet(
    val id: Int,
    val slug: String,
    val name: String,
    val nickname: String,
    val englishName: String,
    val birthYear: Int?,
    val deathYear: Int?,
    val poemCount: Int,
    val verseCount: Int,
    val nonemptyVerseCount: Int,
    val englishTranslationCount: Int,
    val urduTranslationCount: Int,
    val packFile: String,
    val packSizeBytes: Long,
    val sha256: String,
    val bundledInApp: Boolean,
    val downloadUrl: String,
    val downloaded: Boolean = false
)

internal data class PoetCategory(
    val id: Int,
    val poetId: Int,
    val parentId: Int?,
    val title: String,
    val categoryType: String,
    val childCategoryCount: Int,
    val poemCount: Int
)

internal data class Poem(
    val id: Int,
    val poetId: Int,
    val categoryId: Int,
    val title: String,
    val fullTitle: String,
    val verseCount: Int,
    val poetName: String,
    val englishTranslationCount: Int = 0,
    val urduTranslationCount: Int = 0,
    val humanReviewStatus: String = ""
)

internal data class Verse(
    val poemId: Int,
    val order: Int,
    val position: String,
    val persian: String,
    val coupletIndex: Int,
    val sectionIndex: Int,
    val english: String?,
    val urdu: String?,
    val englishStatus: String = "",
    val urduStatus: String = "",
    val humanReviewStatus: String = ""
)

internal data class SearchHit(
    val kind: String,
    val id: Int,
    val title: String,
    val subtitle: String,
    val verseOrder: Int = -1
)

/**
 * Offline per-poet SQLite store. The app ships only the Rumi and Saadi packs;
 * optional catalog entries can be fetched over HTTPS and are queryable once installed.
 */
internal class CorpusStore(private val context: Context) : AutoCloseable {
    private val packDirectory: File by lazy {
        File(context.filesDir, "poet-packs").apply { mkdirs() }
    }
    private val dbLock = Any()
    private val openDatabases = mutableMapOf<Int, SQLiteDatabase>()
    @Volatile private var catalogCache: List<Poet>? = null
    private var dailyCacheKey: String = ""
    private var dailyCache: Verse? = null

    private fun catalog(): List<Poet> {
        catalogCache?.let { return it }
        synchronized(this) {
            catalogCache?.let { return it }
            val json = context.assets.open("assets/data/poet_catalog.json").bufferedReader().use { it.readText() }
            val array = JSONObject(json).getJSONArray("poets")
            val parsed = buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        Poet(
                            id = item.getInt("id"),
                            slug = item.getString("slug"),
                            name = item.optString("name", ""),
                            nickname = item.optString("nickname", ""),
                            englishName = item.optString("english_name", item.optString("slug", "")),
                            birthYear = item.takeUnless { it.isNull("birth_year_hijri") }?.optInt("birth_year_hijri"),
                            deathYear = item.takeUnless { it.isNull("death_year_hijri") }?.optInt("death_year_hijri"),
                            poemCount = item.optInt("poem_count", 0),
                            verseCount = item.optInt("verse_count", 0),
                            nonemptyVerseCount = item.optInt("nonempty_verse_count", item.optInt("verse_count", 0)),
                            englishTranslationCount = item.optInt("english_translation_count", 0),
                            urduTranslationCount = item.optInt("urdu_translation_count", 0),
                            packFile = item.optString("pack_file", "${item.optString("slug")}.sqlite"),
                            packSizeBytes = item.optLong("pack_size_bytes", 0L),
                            sha256 = item.optString("sha256", ""),
                            bundledInApp = item.optBoolean("bundled_in_app", false),
                            downloadUrl = item.optString("download_url", "")
                        )
                    )
                }
            }
            catalogCache = parsed
            return parsed
        }
    }

    fun poets(): List<Poet> = catalog().map { it.copy(downloaded = isInstalled(it)) }

    fun poet(id: Int): Poet? = catalog().firstOrNull { it.id == id }?.let { it.copy(downloaded = isInstalled(it)) }

    fun isInstalled(poet: Poet): Boolean {
        val file = packFile(poet)
        return file.isFile && poet.packSizeBytes > 0L && file.length() == poet.packSizeBytes
    }

    private fun packFile(poet: Poet): File = File(packDirectory, poet.packFile)

    /** Copy the two bundled packs to private storage, verifying size, SHA-256 and SQLite integrity. */
    fun prepareBundledPacks(): Boolean {
        val bundled = catalog().filter { it.bundledInApp }
        if (bundled.isEmpty()) return false
        packDirectory.mkdirs()
        for (poet in bundled) {
            val destination = packFile(poet)
            if (!destination.isFile || destination.length() != poet.packSizeBytes) {
                val temporary = File(packDirectory, "${poet.slug}.installing")
                temporary.delete()
                val digest = MessageDigest.getInstance("SHA-256")
                context.assets.open("assets/poet_packs/${poet.packFile}").use { input ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                }
                val actualHash = digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it) }
                if (temporary.length() != poet.packSizeBytes || !actualHash.equals(poet.sha256, ignoreCase = true)) {
                    temporary.delete()
                    return false
                }
                if (!quickCheck(temporary)) {
                    temporary.delete()
                    return false
                }
                synchronized(dbLock) { closeDatabaseLocked(poet.id) }
                if (destination.exists() && !destination.delete()) {
                    temporary.delete()
                    return false
                }
                if (!temporary.renameTo(destination)) {
                    temporary.delete()
                    return false
                }
            }
        }
        return bundled.all { isInstalled(it) && quickCheck(packFile(it)) }
    }

    /** Verify installed files without opening them for write. Called off the UI thread at startup. */
    fun checkIntegrity(): Boolean {
        val installed = catalog().filter { isInstalled(it) }
        return installed.isNotEmpty() && installed.all { quickCheck(packFile(it)) }
    }

    /** Install a hosted poet pack only after the catalog size, hash, and SQLite integrity match. */
    fun installPack(poetId: Int, input: java.io.InputStream): Boolean {
        val poet = catalog().firstOrNull { it.id == poetId } ?: return false
        val temporary = File(packDirectory, "${poet.slug}.download.tmp")
        packDirectory.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256")
        var bytesWritten = 0L
        try {
            input.use { source ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        if (bytesWritten + count > poet.packSizeBytes) throw IllegalStateException("Pack exceeds catalog size")
                        output.write(buffer, 0, count)
                        bytesWritten += count
                        digest.update(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            val actualHash = digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it) }
            if (temporary.length() != poet.packSizeBytes || !actualHash.equals(poet.sha256, ignoreCase = true) || !quickCheck(temporary)) {
                temporary.delete()
                return false
            }
            val destination = packFile(poet)
            synchronized(dbLock) {
                closeDatabaseLocked(poet.id)
                if (destination.exists() && !destination.delete()) return false
                if (!temporary.renameTo(destination)) return false
            }
            dailyCacheKey = ""
            return true
        } catch (_: Exception) {
            temporary.delete()
            return false
        }
    }

    /** Install a catalog pack bundled as an optional asset; never performs network access. */
    fun installPackFromAsset(poetId: Int): Boolean {
        val poet = catalog().firstOrNull { it.id == poetId } ?: return false
        val input = try {
            context.assets.open("assets/poet_packs/${poet.packFile}")
        } catch (_: Exception) {
            return false
        }
        return installPack(poetId, input)
    }

    /** Download an optional pack over HTTPS, retry transient failures, and verify before install. */
    fun downloadPackFromUrl(poetId: Int): Boolean {
        val poet = catalog().firstOrNull { it.id == poetId } ?: return false
        if (poet.bundledInApp || poet.downloadUrl.isBlank()) return false

        val sources = mutableListOf(poet.downloadUrl)
        val primary = try { URL(poet.downloadUrl) } catch (_: Exception) { return false }
        // jsDelivr serves the smaller repository files from a separate CDN. It rejects large
        // files, so keep the fallback below a conservative 45 MiB and never use it for releases.
        if (primary.protocol.equals("https", ignoreCase = true) &&
            primary.host.equals("raw.githubusercontent.com", ignoreCase = true) &&
            poet.packSizeBytes <= 45L * 1024L * 1024L
        ) {
            sources += "https://cdn.jsdelivr.net/gh/usmanbhat/sukhan@main/${poet.packFile}"
        }
        return sources.any { source -> downloadFromSource(poetId, poet, source) }
    }

    private fun downloadFromSource(poetId: Int, poet: Poet, source: String): Boolean {
        val url = try { URL(source) } catch (_: Exception) { return false }
        if (!url.protocol.equals("https", ignoreCase = true)) return false

        repeat(2) { attempt ->
            var connection: HttpURLConnection? = null
            try {
                connection = url.openConnection() as? HttpURLConnection ?: return false
                connection.connectTimeout = 30_000
                connection.readTimeout = 120_000
                connection.instanceFollowRedirects = true
                connection.useCaches = false
                connection.setRequestProperty("User-Agent", "Sukhan-Android/1.0")
                connection.setRequestProperty("Accept", "application/octet-stream")
                connection.setRequestProperty("Accept-Encoding", "identity")
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK &&
                    connection.url.protocol.equals("https", ignoreCase = true)
                ) {
                    val advertisedLength = connection.contentLength
                    if (advertisedLength < 0 || advertisedLength.toLong() == poet.packSizeBytes) {
                        if (installPack(poetId, connection.inputStream)) return true
                    } else {
                        return false
                    }
                } else if (connection.responseCode !in setOf(408, 429, 500, 502, 503, 504)) {
                    return false
                }
            } catch (_: Exception) {
                // Retry once: mobile connections and GitHub's asset redirects can fail transiently.
            } finally {
                connection?.disconnect()
            }
            if (attempt == 0) {
                try { Thread.sleep(700) } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
        }
        return false
    }

    /** Remove only optional packs; the two preloaded packs are kept in the app library. */
    fun removePack(poetId: Int): Boolean {
        val poet = catalog().firstOrNull { it.id == poetId } ?: return false
        if (poet.bundledInApp) return false
        synchronized(dbLock) { closeDatabaseLocked(poet.id) }
        val removed = !packFile(poet).exists() || packFile(poet).delete()
        if (removed) dailyCacheKey = ""
        return removed
    }

    private fun quickCheck(file: File): Boolean {
        if (!file.isFile) return false
        return try {
            val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            try {
                db.rawQuery("PRAGMA quick_check(1)", null).use { it.moveToFirst() && it.getString(0) == "ok" }
            } finally {
                db.close()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun database(poet: Poet): SQLiteDatabase? {
        if (!isInstalled(poet)) return null
        synchronized(dbLock) {
            openDatabases[poet.id]?.let { if (it.isOpen) return it }
            val db = try {
                SQLiteDatabase.openDatabase(packFile(poet).absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            } catch (_: Exception) {
                return null
            }
            openDatabases[poet.id] = db
            return db
        }
    }

    private fun databaseForPoem(poemId: Int): Pair<Poet, SQLiteDatabase>? {
        for (poet in catalog().filter { isInstalled(it) }) {
            val db = database(poet) ?: continue
            val found = db.rawQuery("SELECT 1 FROM ganjoor_poem WHERE id=? LIMIT 1", arrayOf(poemId.toString())).use { it.moveToFirst() }
            if (found) return poet to db
        }
        return null
    }

    fun rootCategory(poetId: Int): PoetCategory? {
        val poet = catalog().firstOrNull { it.id == poetId } ?: return null
        val db = database(poet) ?: return null
        return queryCategories(db, poetId, null).singleOrNull()
    }

    /** Hide the single poet-name root so opening a poet lands directly on books and collections. */
    fun rootCategories(poetId: Int): List<PoetCategory> {
        val roots = categoriesAt(poetId, null)
        if (roots.size == 1) {
            val children = categoriesAt(poetId, roots.single().id)
            if (children.isNotEmpty()) return children
        }
        return roots
    }

    fun childCategories(poetId: Int, parentId: Int): List<PoetCategory> = categoriesAt(poetId, parentId)

    private fun categoriesAt(poetId: Int, parentId: Int?): List<PoetCategory> {
        val poet = catalog().firstOrNull { it.id == poetId } ?: return emptyList()
        val db = database(poet) ?: return emptyList()
        return queryCategories(db, poetId, parentId)
    }

    private fun queryCategories(db: SQLiteDatabase, poetId: Int, parentId: Int?): List<PoetCategory> {
        val parentClause = if (parentId == null) "(c.parent_id IS NULL OR c.parent_id=0)" else "c.parent_id=?"
        val args = if (parentId == null) arrayOf(poetId.toString()) else arrayOf(poetId.toString(), parentId.toString())
        val sql = """
            SELECT c.id,c.poet_id,c.parent_id,COALESCE(c.title,''),COALESCE(c.category_type,''),
                   (SELECT COUNT(*) FROM ganjoor_category child WHERE child.parent_id=c.id),
                   (SELECT COUNT(*) FROM ganjoor_poem p WHERE p.poet_id=c.poet_id AND p.category_id=c.id)
            FROM ganjoor_category c
            WHERE c.poet_id=? AND $parentClause
            ORDER BY c.id
        """.trimIndent()
        return try {
            db.rawQuery(sql, args).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(
                        PoetCategory(
                            id = cursor.getInt(0),
                            poetId = cursor.getInt(1),
                            parentId = if (cursor.isNull(2)) null else cursor.getInt(2),
                            title = cursor.getString(3) ?: "",
                            categoryType = cursor.getString(4) ?: "",
                            childCategoryCount = cursor.getInt(5),
                            poemCount = cursor.getInt(6)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun poemsInCategory(poetId: Int, categoryId: Int, limit: Int, offset: Int = 0): List<Poem> {
        val poet = catalog().firstOrNull { it.id == poetId } ?: return emptyList()
        if (!isInstalled(poet)) return emptyList()
        return queryPoems(poet, limit.coerceAtLeast(0), offset.coerceAtLeast(0), categoryId)
    }

    fun poems(poetId: Int? = null, limit: Int? = null, offset: Int = 0): List<Poem> {
        val available = catalog().filter { isInstalled(it) && (poetId == null || it.id == poetId) }
        if (limit == null) return available.flatMap { queryPoems(it, null, 0) }
        var remainingOffset = offset.coerceAtLeast(0)
        var remainingLimit = limit.coerceAtLeast(0)
        return buildList {
            for (poet in available) {
                if (remainingLimit == 0) break
                val db = database(poet) ?: continue
                val count = db.rawQuery("SELECT COUNT(*) FROM ganjoor_poem WHERE poet_id=?", arrayOf(poet.id.toString())).use {
                    if (it.moveToFirst()) it.getInt(0) else 0
                }
                if (remainingOffset >= count) {
                    remainingOffset -= count
                    continue
                }
                val page = queryPoems(poet, remainingLimit, remainingOffset)
                addAll(page)
                remainingLimit -= page.size
                remainingOffset = 0
            }
        }
    }

    private fun queryPoems(poet: Poet, limit: Int?, offset: Int, categoryId: Int? = null): List<Poem> {
        val db = database(poet) ?: return emptyList()
        val pageClause = if (limit == null) "" else "LIMIT ? OFFSET ?"
        val categoryClause = if (categoryId == null) "" else "AND p.category_id=?"
        val args = buildList {
            add(poet.id.toString())
            if (categoryId != null) add(categoryId.toString())
            if (limit != null) {
                add(limit.toString())
                add(offset.coerceAtLeast(0).toString())
            }
        }.toTypedArray()
        val sql = """
            SELECT p.id,p.poet_id,COALESCE(p.category_id,0),p.title,COALESCE(p.full_title,p.title),
                   COUNT(v.vorder),a.name,
                   SUM(CASE WHEN TRIM(COALESCE(v.translation_en,''))<>'' THEN 1 ELSE 0 END),
                   SUM(CASE WHEN TRIM(COALESCE(v.translation_ur,''))<>'' THEN 1 ELSE 0 END),
                   COALESCE(tp.human_review_status,'')
            FROM ganjoor_poem p
            JOIN ganjoor_poet a ON a.id=p.poet_id
            LEFT JOIN ganjoor_verse v ON v.poem_id=p.id
            LEFT JOIN translation_provenance tp ON tp.poem_id=p.id
            WHERE p.poet_id=? $categoryClause
            GROUP BY p.id,p.poet_id,p.category_id,p.title,p.full_title,a.name,tp.human_review_status
            ORDER BY p.id $pageClause
        """.trimIndent()
        return db.rawQuery(sql, args).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toPoem()) } }
    }

    fun poem(id: Int): Poem? {
        val found = databaseForPoem(id) ?: return null
        val db = found.second
        val sql = """
            SELECT p.id,p.poet_id,COALESCE(p.category_id,0),p.title,COALESCE(p.full_title,p.title),
                   COUNT(v.vorder),a.name,
                   SUM(CASE WHEN TRIM(COALESCE(v.translation_en,''))<>'' THEN 1 ELSE 0 END),
                   SUM(CASE WHEN TRIM(COALESCE(v.translation_ur,''))<>'' THEN 1 ELSE 0 END),
                   COALESCE(tp.human_review_status,'')
            FROM ganjoor_poem p JOIN ganjoor_poet a ON a.id=p.poet_id
            LEFT JOIN ganjoor_verse v ON v.poem_id=p.id
            LEFT JOIN translation_provenance tp ON tp.poem_id=p.id
            WHERE p.id=?
            GROUP BY p.id,p.poet_id,p.category_id,p.title,p.full_title,a.name,tp.human_review_status
        """.trimIndent()
        return db.rawQuery(sql, arrayOf(id.toString())).use { if (it.moveToFirst()) it.toPoem() else null }
    }

    fun verses(poemId: Int): List<Verse> {
        val found = databaseForPoem(poemId) ?: return emptyList()
        val db = found.second
        val sql = """
            SELECT v.poem_id,v.vorder,COALESCE(v.position,''),COALESCE(v.text_persian,''),
                   COALESCE(v.couplet_index,-1),COALESCE(v.section_index,-1),v.translation_en,v.translation_ur,
                   COALESCE(tp.english_status,''),COALESCE(tp.urdu_status,''),COALESCE(tp.human_review_status,'')
            FROM ganjoor_verse v LEFT JOIN translation_provenance tp ON tp.poem_id=v.poem_id
            WHERE v.poem_id=? ORDER BY v.vorder
        """.trimIndent()
        return db.rawQuery(sql, arrayOf(poemId.toString())).use { c -> buildList { while (c.moveToNext()) add(c.toVerse()) } }
    }

    fun dailyVerse(day: Calendar = Calendar.getInstance()): Verse? {
        val active = catalog().filter { isInstalled(it) && it.nonemptyVerseCount > 0 }
        val total = active.sumOf { it.nonemptyVerseCount }
        if (total <= 0) return null
        val key = "${day.get(Calendar.YEAR)}-${day.get(Calendar.DAY_OF_YEAR)}:${active.joinToString { it.slug }}"
        if (key == dailyCacheKey) return dailyCache
        val dayKey = day.get(Calendar.YEAR) * 400L + day.get(Calendar.DAY_OF_YEAR)
        var offset = (dayKey % total.toLong()).toInt()
        for (poet in active) {
            if (offset >= poet.nonemptyVerseCount) {
                offset -= poet.nonemptyVerseCount
                continue
            }
            val db = database(poet) ?: return null
            val sql = """
                SELECT v.poem_id,v.vorder,COALESCE(v.position,''),COALESCE(v.text_persian,''),
                       COALESCE(v.couplet_index,-1),COALESCE(v.section_index,-1),v.translation_en,v.translation_ur,
                       COALESCE(tp.english_status,''),COALESCE(tp.urdu_status,''),COALESCE(tp.human_review_status,'')
                FROM ganjoor_verse v LEFT JOIN translation_provenance tp ON tp.poem_id=v.poem_id
                WHERE TRIM(COALESCE(v.text_persian,''))<>'' ORDER BY v.poem_id,v.vorder LIMIT 1 OFFSET ?
            """.trimIndent()
            val result = db.rawQuery(sql, arrayOf(offset.toString())).use { if (it.moveToFirst()) it.toVerse() else null }
            dailyCacheKey = key
            dailyCache = result
            return result
        }
        dailyCacheKey = key
        dailyCache = null
        return null
    }

    fun poemForVerse(poemId: Int): Poem? = poem(poemId)

    fun searchBooks(query: String, limit: Int = 40): List<SearchHit> {
        val needle = query.trim().lowercase(Locale.ROOT)
        return catalog().asSequence()
            .filter { needle.isEmpty() || listOf(it.name, it.nickname, it.englishName, it.slug).any { value -> value.lowercase(Locale.ROOT).contains(needle) } }
            .take(limit.coerceAtLeast(0))
            .map { poet ->
                val state = if (isInstalled(poet)) "Downloaded" else "Not downloaded"
                SearchHit("book", poet.id, poet.name, "${poet.englishName} · $state · ${poet.poemCount} poems")
            }.toList()
    }

    fun searchPoems(query: String, limit: Int = 40): List<SearchHit> {
        val like = "%$query%"
        val output = mutableListOf<SearchHit>()
        for (poet in catalog().filter { isInstalled(it) }) {
            val db = database(poet) ?: continue
            val remaining = (limit - output.size).coerceAtLeast(0)
            if (remaining == 0) break
            val sql = """
                SELECT p.id,p.title,a.name FROM ganjoor_poem p JOIN ganjoor_poet a ON a.id=p.poet_id
                WHERE p.title LIKE ? OR p.full_title LIKE ? ORDER BY p.id LIMIT ?
            """.trimIndent()
            db.rawQuery(sql, arrayOf(like, like, remaining.toString())).use { c ->
                while (c.moveToNext()) output.add(SearchHit("poem", c.getInt(0), c.getString(1) ?: "", c.getString(2) ?: ""))
            }
        }
        return output
    }

    fun searchLines(query: String, limit: Int = 60): List<SearchHit> {
        val like = "%$query%"
        val output = mutableListOf<SearchHit>()
        for (poet in catalog().filter { isInstalled(it) }) {
            val db = database(poet) ?: continue
            val remaining = (limit - output.size).coerceAtLeast(0)
            if (remaining == 0) break
            val sql = """
                SELECT v.poem_id,v.vorder,v.text_persian,p.title,a.name FROM ganjoor_verse v
                JOIN ganjoor_poem p ON p.id=v.poem_id JOIN ganjoor_poet a ON a.id=p.poet_id
                WHERE v.text_persian LIKE ? ORDER BY v.poem_id,v.vorder LIMIT ?
            """.trimIndent()
            db.rawQuery(sql, arrayOf(like, remaining.toString())).use { c ->
                while (c.moveToNext()) output.add(
                    SearchHit("line", c.getInt(0), c.getString(2) ?: "", "${c.getString(3) ?: ""} · ${c.getString(4) ?: ""}", c.getInt(1))
                )
            }
        }
        return output
    }

    private fun closeDatabaseLocked(poetId: Int) {
        try { openDatabases.remove(poetId)?.close() } catch (_: Exception) { }
    }

    override fun close() {
        synchronized(dbLock) {
            openDatabases.values.forEach { try { it.close() } catch (_: Exception) { } }
            openDatabases.clear()
        }
    }

    private fun Cursor.toPoem() = Poem(
        id = getInt(0), poetId = getInt(1), categoryId = getInt(2), title = getString(3) ?: "",
        fullTitle = getString(4) ?: "", verseCount = getInt(5), poetName = getString(6) ?: "",
        englishTranslationCount = getInt(7), urduTranslationCount = getInt(8), humanReviewStatus = getString(9) ?: ""
    )

    private fun Cursor.toVerse() = Verse(
        poemId = getInt(0), order = getInt(1), position = getString(2) ?: "", persian = getString(3) ?: "",
        coupletIndex = getInt(4), sectionIndex = getInt(5),
        english = if (isNull(6)) null else getString(6), urdu = if (isNull(7)) null else getString(7),
        englishStatus = getString(8) ?: "", urduStatus = getString(9) ?: "", humanReviewStatus = getString(10) ?: ""
    )
}
