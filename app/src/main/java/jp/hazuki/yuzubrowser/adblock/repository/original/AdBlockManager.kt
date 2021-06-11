/*
 * Copyright (C) 2017-2019 Hazuki
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.hazuki.yuzubrowser.adblock.repository.original

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import jp.hazuki.yuzubrowser.adblock.filter.fastmatch.AdBlockDecoder
import jp.hazuki.yuzubrowser.adblock.filter.fastmatch.LegacyDecoder
import jp.hazuki.yuzubrowser.adblock.filter.unified.UnifiedFilter
import jp.hazuki.yuzubrowser.adblock.filter.unified.getFilterDir
import jp.hazuki.yuzubrowser.adblock.filter.unified.io.FilterReader
import jp.hazuki.yuzubrowser.adblock.filter.unified.io.FilterWriter
import jp.hazuki.yuzubrowser.adblock.filter.unified.writeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.util.*

class AdBlockManager internal constructor(private val context: Context) {

    private val mOpenHelper = MyOpenHelper(context)
    private val appContext = context.applicationContext

    private val updating = mutableMapOf<String, Boolean>()

    init {
        if (!context.getDatabasePath(DB_NAME).exists()) {
            initList(appContext)
        }
    }

    private fun update(table: String, adBlock: AdBlock): Boolean {
        val result = if (adBlock.id > -1) {
            updateInternal(table, adBlock)
        } else {
            insert(table, adBlock)
        }
        updateListTime(table)
        return result
    }

    private fun insert(table: String, adBlock: AdBlock): Boolean {
        val db = mOpenHelper.writableDatabase
        val values = ContentValues()
        values.put(COLUMN_MATCH, adBlock.match)
        values.put(COLUMN_ENABLE, if (adBlock.isEnable) 1 else 0)
        return try {
            val id = db.insert(table, null, values)
            adBlock.id = id.toInt()
            true
        } catch (e: SQLiteConstraintException) {
            false
        }

    }

    private fun updateInternal(table: String, adBlock: AdBlock): Boolean {
        val db = mOpenHelper.writableDatabase
        val values = ContentValues()
        values.put(COLUMN_MATCH, adBlock.match)
        values.put(COLUMN_ENABLE, if (adBlock.isEnable) 1 else 0)
        return try {
            db.update(table, values, "$COLUMN_ID = ?", arrayOf(Integer.toString(adBlock.id)))
            true
        } catch (e: SQLiteConstraintException) {
            false
        }

    }

    private fun delete(table: String, id: Int) {
        val db = mOpenHelper.writableDatabase
        db.delete(table, "$COLUMN_ID = ?", arrayOf(Integer.toString(id)))
        updateListTime(table)
    }

    private fun addAll(table: String, adBlocks: List<AdBlock>) {
        val db = mOpenHelper.writableDatabase
        db.beginTransaction()
        try {
            for (adBlock in adBlocks) {
                val values = ContentValues()
                values.put(COLUMN_MATCH, adBlock.match)
                values.put(COLUMN_ENABLE, if (adBlock.isEnable) 1 else 0)
                try {
                    db.insert(table, null, values)
                } catch (e: SQLiteConstraintException) {
                    e.printStackTrace()
                }

            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        updateListTime(table)
    }

    private fun getAllItems(table: String): ArrayList<AdBlock> {
        val db = mOpenHelper.readableDatabase
        db.query(table, null, null, null, null, null, null).use { c ->
            val id = c.getColumnIndex(COLUMN_ID)
            val match = c.getColumnIndex(COLUMN_MATCH)
            val enable = c.getColumnIndex(COLUMN_ENABLE)
            val adBlocks = ArrayList<AdBlock>()
            while (c.moveToNext()) {
                adBlocks.add(AdBlock(c.getInt(id), c.getString(match), c.getInt(enable) != 0))
            }
            return adBlocks
        }
    }

    private fun getEnableItems(table: String): ArrayList<AdBlock> {
        val db = mOpenHelper.readableDatabase
        db.query(table, null, "$COLUMN_ENABLE = 1", null, null, null, null).use { c ->
            val id = c.getColumnIndex(COLUMN_ID)
            val match = c.getColumnIndex(COLUMN_MATCH)
            val enable = c.getColumnIndex(COLUMN_ENABLE)
            val adBlocks = ArrayList<AdBlock>()
            while (c.moveToNext()) {
                adBlocks.add(AdBlock(c.getInt(id), c.getString(match), c.getInt(enable) != 0))
            }
            return adBlocks
        }
    }

    private fun deleteAll(table: String) {
        val db = mOpenHelper.writableDatabase
        db.delete(table, null, null)
    }

    fun getCachedMatcherList(table: String): Sequence<UnifiedFilter> {
        val listTime = getListUpdateTime(table)
        val cacheTime = getCacheUpdateTime(table)
        if (listTime <= cacheTime) {
            try {
                getFilterFile(table).inputStream().use {
                    val reader = FilterReader(it)
                    if (reader.checkHeader()) {
                        return reader.readAll()
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        val list = getMatcherList(table)
        GlobalScope.launch(Dispatchers.IO) {
            writeFilter(getFilterFile(table), list)
            updateCacheTime(table)
        }
        return list.asSequence()
    }

    private fun getMatcherList(table: String): List<UnifiedFilter> {
        val list = arrayListOf<UnifiedFilter>()
        val decoder = LegacyDecoder()
        val db = mOpenHelper.readableDatabase
        db.query(table, null, "$COLUMN_ENABLE = 1", null, null, null, null).use { c ->
            val match = c.getColumnIndex(COLUMN_MATCH)
            while (c.moveToNext()) {
                val matcher = decoder.singleDecode(c.getString(match))
                if (matcher != null) {
                    list += matcher
                }
            }
            return list
        }
    }

    private fun getListUpdateTime(table: String): Long {
        val db = mOpenHelper.readableDatabase
        db.query(INFO_TABLE_NAME, null, "$INFO_COLUMN_NAME = ?", arrayOf(table), null, null, null, "1").use { c ->
            var time: Long = -1
            if (c.moveToFirst())
                time = c.getLong(c.getColumnIndex(INFO_COLUMN_LAST_TIME))
            return time
        }
    }

    private fun updateListTime(table: String) {
        val db = mOpenHelper.writableDatabase
        val values = ContentValues()
        values.put(INFO_COLUMN_LAST_TIME, System.currentTimeMillis())
        db.update(INFO_TABLE_NAME, values, "$INFO_COLUMN_NAME = ?", arrayOf(table))
    }

    private fun getCacheUpdateTime(table: String): Long {
        val db = mOpenHelper.readableDatabase
        db.query(INFO_TABLE_NAME, null, "$INFO_COLUMN_NAME = ?", arrayOf("${table}_cache"), null, null, null, "1").use { c ->
            var time: Long = -1
            if (c.moveToFirst())
                time = c.getLong(c.getColumnIndex(INFO_COLUMN_LAST_TIME))
            return time
        }
    }

    private fun updateCacheTime(table: String) {
        val db = mOpenHelper.writableDatabase
        val values = ContentValues()
        values.put(INFO_COLUMN_LAST_TIME, System.currentTimeMillis())
        db.update(INFO_TABLE_NAME, values, "$INFO_COLUMN_NAME = ?", arrayOf("${table}_cache"))
    }

    private fun initList(context: Context) {
        try {
            BufferedInputStream(context.assets.open("adblock/whitelist.txt")).use {
                val adBlocks = AdBlockDecoder.decode(Scanner(it), false)
                addAll(ALLOW_TABLE_NAME, adBlocks)
            }
            GlobalScope.launch(Dispatchers.IO) {
                createCache(ALLOW_TABLE_NAME)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        try {
            BufferedInputStream(context.assets.open("adblock/whitepagelist.txt")).use {
                val adBlocks = AdBlockDecoder.decode(Scanner(it), false)
                addAll(ALLOW_PAGE_TABLE_NAME, adBlocks)
            }
            GlobalScope.launch(Dispatchers.IO) {
                createCache(ALLOW_PAGE_TABLE_NAME)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun createCache(table: String) {
        if (isUpdating(table)) return

        updating[table] = true
        try {
            getFilterFile(table).outputStream().use {
                val writer = FilterWriter()
                writer.write(it, getMatcherList(table))
            }
            updateCacheTime(table)
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            updating[table] = false
        }
    }

    fun isUpdating(table: String): Boolean {
        return updating[table] ?: false
    }

    private fun getFilterFile(table: String): File {
        return File(context.getFilterDir(), "cache_$table")
    }

    class AdBlockItemProvider constructor(context: Context, private val table: String) {

        private val manager: AdBlockManager = AdBlockManager(context)

        val allItems: ArrayList<AdBlock>
            get() = manager.getAllItems(table)

        val enableItems: ArrayList<AdBlock>
            get() = manager.getEnableItems(table)

        fun update(adBlock: AdBlock): Boolean = manager.update(table, adBlock)

        fun delete(id: Int) {
            manager.delete(table, id)
        }

        fun addAll(adBlocks: List<AdBlock>) {
            manager.addAll(table, adBlocks)
        }

        fun deleteAll() {
            manager.deleteAll(table)
        }

        fun updateCache() {
            manager.createCache(table)
        }
    }

    private class MyOpenHelper private constructor(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.beginTransaction()
            try {
                db.execSQL("CREATE TABLE " + INFO_TABLE_NAME + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY" +
                    ", " + INFO_COLUMN_NAME + " TEXT NOT NULL" +
                    ", " + INFO_COLUMN_LAST_TIME + " INTEGER DEFAULT 0" +
                    ")")
                db.execSQL("CREATE TABLE " + DENY_TABLE_NAME + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY" +
                    ", " + COLUMN_MATCH + " TEXT NOT NULL UNIQUE" +
                    ", " + COLUMN_ENABLE + " INTEGER DEFAULT 0" +
                    ")")
                db.execSQL("CREATE TABLE " + ALLOW_TABLE_NAME + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY" +
                    ", " + COLUMN_MATCH + " TEXT NOT NULL UNIQUE" +
                    ", " + COLUMN_ENABLE + " INTEGER DEFAULT 0" +
                    ")")
                db.execSQL("CREATE TABLE " + ALLOW_PAGE_TABLE_NAME + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY" +
                    ", " + COLUMN_MATCH + " TEXT NOT NULL UNIQUE" +
                    ", " + COLUMN_ENABLE + " INTEGER DEFAULT 0" +
                    ")")

                // init info table
                val values = ContentValues()
                values.put(INFO_COLUMN_LAST_TIME, System.currentTimeMillis())

                values.put(INFO_COLUMN_NAME, DENY_TABLE_NAME)
                db.insert(INFO_TABLE_NAME, null, values)

                values.put(INFO_COLUMN_NAME, ALLOW_TABLE_NAME)
                db.insert(INFO_TABLE_NAME, null, values)

                values.put(INFO_COLUMN_NAME, ALLOW_PAGE_TABLE_NAME)
                db.insert(INFO_TABLE_NAME, null, values)

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $DENY_TABLE_NAME")
            db.execSQL("DROP TABLE IF EXISTS $ALLOW_TABLE_NAME")
            db.execSQL("DROP TABLE IF EXISTS $ALLOW_PAGE_TABLE_NAME")
            db.execSQL("DROP TABLE IF EXISTS $INFO_TABLE_NAME")
            onCreate(db)
        }

        companion object {
            private var instance: MyOpenHelper? = null

            operator fun invoke(context: Context): MyOpenHelper {
                if (instance == null)
                    instance = MyOpenHelper(context)
                return instance!!
            }
        }
    }

    companion object {
        private const val DB_NAME = "adblock.db"
        private const val DB_VERSION = 1

        internal const val DENY_TABLE_NAME = "black"
        internal const val ALLOW_TABLE_NAME = "white"
        internal const val ALLOW_PAGE_TABLE_NAME = "white_page"

        const val TYPE_DENY_TABLE = 1
        const val TYPE_ALLOW_TABLE = 2
        const val TYPE_ALLOW_PAGE_TABLE = 3

        private const val INFO_TABLE_NAME = "info"

        private const val COLUMN_ID = "_id"
        private const val COLUMN_MATCH = "match"
        private const val COLUMN_ENABLE = "enable"

        private const val INFO_COLUMN_NAME = "name"
        private const val INFO_COLUMN_LAST_TIME = "time"

        @JvmStatic
        fun getProvider(context: Context, type: Int): AdBlockItemProvider = when (type) {
            TYPE_DENY_TABLE -> AdBlockItemProvider(context, DENY_TABLE_NAME)
            TYPE_ALLOW_TABLE -> AdBlockItemProvider(context, ALLOW_TABLE_NAME)
            TYPE_ALLOW_PAGE_TABLE -> AdBlockItemProvider(context, ALLOW_PAGE_TABLE_NAME)
            else -> throw IllegalArgumentException("unknown type")
        }
    }
}
