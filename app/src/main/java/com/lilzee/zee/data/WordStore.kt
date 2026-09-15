package com.lilzee.zee.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class WordEntry(
    val id: Long,
    val word: String,
    val phonetic: String?,
    val meaning: String,
    val exampleEn: String?,
    val exampleZh: String?,
    val sourceSentence: String?,
    val createdAt: Long,
)

class WordStore private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "zee.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE words(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                word TEXT NOT NULL,
                norm TEXT NOT NULL,
                phonetic TEXT,
                meaning TEXT NOT NULL,
                example_en TEXT,
                example_zh TEXT,
                source_sentence TEXT,
                created_at INTEGER NOT NULL
            )"""
        )
        db.execSQL("CREATE UNIQUE INDEX index_words_norm ON words(norm)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    /** 同一个词只存一条；@return true 表示新插入，false 表示已存在 */
    fun insert(
        word: String,
        norm: String,
        phonetic: String?,
        meaning: String,
        exampleEn: String?,
        exampleZh: String?,
        sourceSentence: String?,
    ): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.query("words", arrayOf("id"), "norm = ?", arrayOf(norm), null, null, null, "1")
                .use { c ->
                    if (c.moveToFirst()) {
                        db.setTransactionSuccessful()
                        return false
                    }
                }
            db.insert(
                "words", null,
                ContentValues().apply {
                    put("word", word)
                    put("norm", norm)
                    put("phonetic", phonetic)
                    put("meaning", meaning)
                    put("example_en", exampleEn)
                    put("example_zh", exampleZh)
                    put("source_sentence", sourceSentence)
                    put("created_at", System.currentTimeMillis())
                }
            )
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    fun all(): List<WordEntry> {
        val list = mutableListOf<WordEntry>()
        readableDatabase.query("words", null, null, null, null, null, "created_at DESC").use { c ->
            while (c.moveToNext()) {
                list += WordEntry(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    word = c.getString(c.getColumnIndexOrThrow("word")) ?: continue,
                    phonetic = c.getString(c.getColumnIndexOrThrow("phonetic")),
                    meaning = c.getString(c.getColumnIndexOrThrow("meaning")) ?: continue,
                    exampleEn = c.getString(c.getColumnIndexOrThrow("example_en")),
                    exampleZh = c.getString(c.getColumnIndexOrThrow("example_zh")),
                    sourceSentence = c.getString(c.getColumnIndexOrThrow("source_sentence")),
                    createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
                )
            }
        }
        return list
    }

    fun delete(id: Long) {
        writableDatabase.delete("words", "id = ?", arrayOf(id.toString()))
    }

    /** 每日入库数（设备时区日期 yyyy-MM-dd），一条聚合查询，热力图数据源 */
    fun dailyCounts(): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        readableDatabase.rawQuery(
            "SELECT strftime('%Y-%m-%d', created_at/1000, 'unixepoch', 'localtime') AS d, COUNT(*)" +
                " FROM words GROUP BY d",
            null,
        ).use { c ->
            while (c.moveToNext()) counts[c.getString(0)] = c.getInt(1)
        }
        return counts
    }

    companion object {
        @Volatile
        private var instance: WordStore? = null

        fun get(context: Context): WordStore =
            instance ?: synchronized(this) {
                instance ?: WordStore(context).also { instance = it }
            }
    }
}
