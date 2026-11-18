package com.sukhan.poetry

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal data class PoemNote(val id: Long, val poemId: Int, val verseOrder: Int?, val text: String, val updatedAt: Long)

internal class LocalStore(context: Context) : SQLiteOpenHelper(context, "iqbal_offline_state.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE favorites(kind TEXT NOT NULL,item_id INTEGER NOT NULL,created_at INTEGER NOT NULL,PRIMARY KEY(kind,item_id))")
        db.execSQL("CREATE TABLE notes(id INTEGER PRIMARY KEY AUTOINCREMENT,poem_id INTEGER NOT NULL,verse_order INTEGER NOT NULL DEFAULT -1,note TEXT NOT NULL,updated_at INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX notes_poem_verse ON notes(poem_id,verse_order)")
        db.execSQL("CREATE TABLE recent_search(query TEXT PRIMARY KEY,last_used INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY,value TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun getSetting(key: String, fallback: String = ""): String = readableDatabase.rawQuery("SELECT value FROM settings WHERE key=?", arrayOf(key)).use {
        if (it.moveToFirst()) it.getString(0) else fallback
    }

    fun setSetting(key: String, value: String) {
        val values = ContentValues().apply { put("key", key); put("value", value) }
        writableDatabase.insertWithOnConflict("settings", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun isFavorite(kind: String, id: Int): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM favorites WHERE kind=? AND item_id=? LIMIT 1", arrayOf(kind, id.toString())
    ).use { it.moveToFirst() }

    fun toggleFavorite(kind: String, id: Int): Boolean {
        val db = writableDatabase
        return if (isFavorite(kind, id)) {
            db.delete("favorites", "kind=? AND item_id=?", arrayOf(kind, id.toString()))
            false
        } else {
            val values = ContentValues().apply { put("kind", kind); put("item_id", id); put("created_at", System.currentTimeMillis()) }
            db.insertWithOnConflict("favorites", null, values, SQLiteDatabase.CONFLICT_IGNORE)
            true
        }
    }

    fun favoriteIds(kind: String): Set<Int> = readableDatabase.rawQuery(
        "SELECT item_id FROM favorites WHERE kind=? ORDER BY created_at DESC", arrayOf(kind)
    ).use { c -> buildSet { while (c.moveToNext()) add(c.getInt(0)) } }

    fun saveNote(poemId: Int, verseOrder: Int?, text: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("poem_id", poemId)
            put("verse_order", verseOrder ?: -1)
            put("note", text)
            put("updated_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("notes", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun notesForPoem(poemId: Int): List<PoemNote> = readableDatabase.rawQuery(
        "SELECT id,poem_id,verse_order,note,updated_at FROM notes WHERE poem_id=? ORDER BY updated_at DESC", arrayOf(poemId.toString())
    ).use { c -> buildList { while (c.moveToNext()) add(PoemNote(c.getLong(0), c.getInt(1), c.getInt(2).takeIf { it >= 0 }, c.getString(3), c.getLong(4))) } }

    fun deleteNote(id: Long) { writableDatabase.delete("notes", "id=?", arrayOf(id.toString())) }

    fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply { put("query", trimmed); put("last_used", System.currentTimeMillis()) }
            db.insertWithOnConflict("recent_search", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            db.execSQL("DELETE FROM recent_search WHERE query NOT IN (SELECT query FROM recent_search ORDER BY last_used DESC LIMIT 8)")
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun recentSearches(): List<String> = readableDatabase.rawQuery(
        "SELECT query FROM recent_search ORDER BY last_used DESC LIMIT 8", null
    ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    fun clearRecentSearches() { writableDatabase.delete("recent_search", null, null) }
}
