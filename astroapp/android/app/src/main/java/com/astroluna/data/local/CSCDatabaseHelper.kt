package com.astroluna.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class CSCDatabaseHelper(private val context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "csc.db"
        const val DB_VERSION = 1
        private var DB_PATH = ""
    }

    init {
        DB_PATH = context.applicationInfo.dataDir + "/databases/"
        copyDataBase()
    }

    private fun checkDataBase(): Boolean {
        val dbFile = File(DB_PATH + DB_NAME)
        return dbFile.exists()
    }

    private fun copyDataBase() {
        if (!checkDataBase()) {
            this.readableDatabase // Create empty DB structure first
            this.close()
            try {
                copyDBFile()
            } catch (e: IOException) {
                android.util.Log.e("CSCDatabaseHelper", "Error copying database: ${e.message}")
                e.printStackTrace()
                // Create empty tables as fallback
                createEmptyTables()
            }
        }
    }

    @Throws(IOException::class)
    private fun copyDBFile() {
        try {
            val mInput: InputStream = context.assets.open(DB_NAME)
            val outFile = File(DB_PATH + DB_NAME)
            val mOutput: OutputStream = FileOutputStream(outFile)
            val mBuffer = ByteArray(1024)
            var mLength: Int
            while (mInput.read(mBuffer).also { mLength = it } > 0) {
                mOutput.write(mBuffer, 0, mLength)
            }
            mOutput.flush()
            mOutput.close()
            mInput.close()
        } catch (e: Exception) {
            android.util.Log.e("CSCDatabaseHelper", "Database file not found in assets, using fallback data")
            throw IOException("Database file not found: $DB_NAME")
        }
    }

    private fun createEmptyTables() {
        val db = this.writableDatabase
        try {
            // Create tables with basic structure
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS countries (
                    id TEXT PRIMARY KEY,
                    name TEXT,
                    iso2 TEXT
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS states (
                    id TEXT PRIMARY KEY,
                    name TEXT,
                    country_id TEXT
                )
            """)

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS cities (
                    id TEXT PRIMARY KEY,
                    name TEXT,
                    state_id TEXT,
                    latitude TEXT,
                    longitude TEXT,
                    timezone TEXT
                )
            """)

            // Insert default data for India
            db.execSQL("INSERT OR IGNORE INTO countries VALUES ('101', 'India', 'IN')")
            db.execSQL("INSERT OR IGNORE INTO states VALUES ('4035', 'Tamil Nadu', '101')")
            db.execSQL("INSERT OR IGNORE INTO cities VALUES ('133647', 'Chennai', '4035', '13.0827', '80.2707', 'Asia/Kolkata')")
            db.execSQL("INSERT OR IGNORE INTO cities VALUES ('133648', 'Coimbatore', '4035', '11.0168', '76.9558', 'Asia/Kolkata')")
            db.execSQL("INSERT OR IGNORE INTO cities VALUES ('133649', 'Madurai', '4035', '9.9252', '78.1198', 'Asia/Kolkata')")

            android.util.Log.i("CSCDatabaseHelper", "Created fallback database with default data")
        } catch (e: Exception) {
            android.util.Log.e("CSCDatabaseHelper", "Error creating fallback tables: ${e.message}")
        } finally {
            db.close()
        }
    }

    override fun onCreate(db: SQLiteDatabase?) {}
    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {}

    fun getCountries(): List<Map<String, String>> {
        val list = mutableListOf<Map<String, String>>()
        try {
            val db = this.readableDatabase
            val cursor = db.rawQuery("SELECT * FROM countries ORDER BY name", null)
            try {
                if (cursor.moveToFirst()) {
                    do {
                        val map = mutableMapOf<String, String>()
                        map["id"] = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                        map["name"] = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                        map["iso2"] = cursor.getString(cursor.getColumnIndexOrThrow("iso2"))
                        list.add(map)
                    } while (cursor.moveToNext())
                }
            } catch (e: Exception) {
                android.util.Log.e("CSCDatabaseHelper", "Error reading countries: ${e.message}")
                e.printStackTrace()
            }
            finally { cursor.close() }
        } catch (e: Exception) {
            android.util.Log.e("CSCDatabaseHelper", "Database error: ${e.message}")
        }
        return list
    }

    fun getStates(countryId: String): List<Map<String, String>> {
        val list = mutableListOf<Map<String, String>>()
        try {
            val db = this.readableDatabase
            val cursor = db.rawQuery("SELECT * FROM states WHERE country_id = ? ORDER BY name", arrayOf(countryId))
            try {
                if (cursor.moveToFirst()) {
                    do {
                        val map = mutableMapOf<String, String>()
                        map["id"] = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                        map["name"] = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                        list.add(map)
                    } while (cursor.moveToNext())
                }
            } catch (e: Exception) {
                android.util.Log.e("CSCDatabaseHelper", "Error reading states: ${e.message}")
                e.printStackTrace()
            }
            finally { cursor.close() }
        } catch (e: Exception) {
            android.util.Log.e("CSCDatabaseHelper", "Database error: ${e.message}")
        }
        return list
    }

    fun getCities(stateId: String): List<Map<String, String>> {
        val list = mutableListOf<Map<String, String>>()
        try {
            val db = this.readableDatabase
            val cursor = db.rawQuery("SELECT * FROM cities WHERE state_id = ? ORDER BY name", arrayOf(stateId))
            try {
                if (cursor.moveToFirst()) {
                    do {
                        val map = mutableMapOf<String, String>()
                        map["id"] = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                        map["name"] = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                        map["latitude"] = cursor.getString(cursor.getColumnIndexOrThrow("latitude"))
                        map["longitude"] = cursor.getString(cursor.getColumnIndexOrThrow("longitude"))
                        // Safely get timezone if column exists
                        val tzIndex = cursor.getColumnIndex("timezone")
                        if (tzIndex != -1) {
                            map["timezone"] = cursor.getString(tzIndex) ?: "Asia/Kolkata"
                        } else {
                            map["timezone"] = "Asia/Kolkata" // Default timezone
                        }
                        list.add(map)
                    } while (cursor.moveToNext())
                }
            } catch (e: Exception) {
                android.util.Log.e("CSCDatabaseHelper", "Error reading cities: ${e.message}")
                e.printStackTrace()
            }
            finally { cursor.close() }
        } catch (e: Exception) {
            android.util.Log.e("CSCDatabaseHelper", "Database error: ${e.message}")
        }
        return list
    }
}
