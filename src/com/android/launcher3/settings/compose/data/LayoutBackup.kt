/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.settings.compose.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.android.launcher3.Utilities
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherFiles
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backs up the home screen layout and the launcher settings to one zip, and restores it:
 * every shared prefs file of the launcher (settings, custom names and icons, the grid) as JSON,
 * and a snapshot of the current grid's database.
 */
object LayoutBackup {

    private const val VERSION = 1
    const val REQUEST_BACKUP = 4101
    const val REQUEST_RESTORE = 4102
    private const val ENTRY_META = "meta.json"
    private const val ENTRY_PREFS = "prefs.json"
    private const val ENTRY_DB = "launcher.db"
    private const val MAX_PREFS_BYTES = 8L shl 20
    private const val MAX_DB_BYTES = 128L shl 20
    private val SAFE_NAME = Regex("[A-Za-z0-9_.\\-]{1,128}")

    // Device state (restore and migration markers), not settings
    private val EXCLUDED_PREFS = setOf(LauncherFiles.DEVICE_PREFERENCES_KEY)

    private fun prefFileNames(context: Context): List<String> {
        val dir = File(context.dataDir, "shared_prefs")
        val names = ArrayList<String>()
        for (file in dir.listFiles() ?: emptyArray()) {
            val name = file.name
            if (!name.endsWith(".xml")) continue
            val prefName = name.substring(0, name.length - 4)
            if (prefName !in EXCLUDED_PREFS) names.add(prefName)
        }
        return names
    }

    @Throws(IOException::class)
    fun export(context: Context, out: OutputStream) {
        val dbFile = InvariantDeviceProfile.INSTANCE.get(context).dbFile
        val prefs = JSONObject()
        for (name in prefFileNames(context)) {
            prefs.put(name, prefsToJson(context.getSharedPreferences(name, Context.MODE_PRIVATE).all))
        }
        // VACUUM INTO takes a consistent copy, WAL included, while the launcher keeps it open
        val snapshot = File(context.cacheDir, "layout-backup.db")
        snapshot.delete()
        val db = SQLiteDatabase.openDatabase(
            context.getDatabasePath(dbFile).path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            db.execSQL("VACUUM INTO ?", arrayOf<Any>(snapshot.path))
        } finally {
            db.close()
        }
        val meta = JSONObject()
            .put("version", VERSION)
            .put("dbFile", dbFile)
            .put("created", System.currentTimeMillis())
        val zip = ZipOutputStream(out)
        try {
            zip.putNextEntry(ZipEntry(ENTRY_META))
            zip.write(meta.toString().toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(ENTRY_PREFS))
            zip.write(prefs.toString().toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(ENTRY_DB))
            snapshot.inputStream().use { copyLimited(it, zip, Long.MAX_VALUE) }
            zip.closeEntry()
        } finally {
            zip.close()
            snapshot.delete()
        }
    }

    /** Restores a backup. The launcher has to restart afterwards to load it. */
    @Throws(IOException::class)
    fun restore(context: Context, input: InputStream) {
        var meta: JSONObject? = null
        var prefs: JSONObject? = null
        val staged = File(context.cacheDir, "layout-restore.db")
        staged.delete()
        try {
            val zip = ZipInputStream(input)
            while (true) {
                val entry = zip.nextEntry ?: break
                when (entry.name) {
                    ENTRY_META -> meta = JSONObject(readLimited(zip, MAX_PREFS_BYTES))
                    ENTRY_PREFS -> prefs = JSONObject(readLimited(zip, MAX_PREFS_BYTES))
                    ENTRY_DB -> FileOutputStream(staged).use { copyLimited(zip, it, MAX_DB_BYTES) }
                }
            }
            if (meta == null || prefs == null || !staged.exists()) {
                throw IOException("Not a ZeroLauncher backup")
            }
            if (meta.optInt("version") > VERSION) {
                throw IOException("This backup is from a newer ZeroLauncher")
            }
            val dbFile = meta.getString("dbFile")
            if (!SAFE_NAME.matches(dbFile) || !dbFile.endsWith(".db")) {
                throw IOException("Bad database name in the backup")
            }
            checkDatabase(staged)

            // The database first: a pref listener that reloads the model then reads the new one
            val target = context.getDatabasePath(dbFile)
            for (suffix in arrayOf("-wal", "-shm", "-journal")) File(target.path + suffix).delete()
            Files.copy(staged.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)

            val names = prefs.keys()
            while (names.hasNext()) {
                val name = names.next()
                if (!SAFE_NAME.matches(name) || name in EXCLUDED_PREFS) continue
                val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
                val values = prefs.getJSONObject(name)
                val keys = values.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = values.getJSONObject(key)
                    when (value.getString("t")) {
                        "b" -> editor.putBoolean(key, value.getBoolean("v"))
                        "i" -> editor.putInt(key, value.getInt("v"))
                        "l" -> editor.putLong(key, value.getLong("v"))
                        "f" -> editor.putFloat(key, value.getDouble("v").toFloat())
                        "s" -> editor.putString(key, value.getString("v"))
                        "set" -> {
                            val array = value.getJSONArray("v")
                            val set = HashSet<String>()
                            for (i in 0 until array.length()) set.add(array.getString(i))
                            editor.putStringSet(key, set)
                        }
                    }
                }
                editor.commit()
            }

        } finally {
            staged.delete()
        }
    }

    /** Asks for a file to save a backup to; the result comes back to [onActivityResult]. */
    fun pickBackupFile(activity: Activity) {
        val name = "ZeroLauncher-backup-" + java.text.SimpleDateFormat(
            "yyyyMMdd-HHmm", java.util.Locale.ROOT).format(java.util.Date()) + ".zip"
        activity.startActivityForResult(
            Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/zip")
                .putExtra(Intent.EXTRA_TITLE, name),
            REQUEST_BACKUP)
    }

    /** Asks for a backup to restore; the result comes back to [onActivityResult]. */
    fun pickRestoreFile(activity: Activity) {
        activity.startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/octet-stream")),
            REQUEST_RESTORE)
    }

    /** Handles the file pickers' results; false for any other request. */
    fun onActivityResult(context: Context, requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_BACKUP && requestCode != REQUEST_RESTORE) return false
        val uri: Uri = data?.data ?: return true
        if (resultCode != Activity.RESULT_OK) return true
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        Thread {
            try {
                if (requestCode == REQUEST_BACKUP) {
                    val out = app.contentResolver.openOutputStream(uri)
                        ?: throw IOException("Could not open the file")
                    out.use { export(app, it) }
                    main.post { Toast.makeText(app, "Backup saved", Toast.LENGTH_LONG).show() }
                } else {
                    val input = app.contentResolver.openInputStream(uri)
                        ?: throw IOException("Could not open the file")
                    input.use { restore(app, it) }
                    main.post { Utilities.restart() }
                }
            } catch (e: Exception) {
                val what = if (requestCode == REQUEST_BACKUP) "Backup" else "Restore"
                main.post {
                    Toast.makeText(app, "$what failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
        return true
    }

    private fun checkDatabase(file: File) {
        val db = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            throw IOException("The backup's database is damaged", e)
        }
        try {
            db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='favorites'", null
            ).use { if (!it.moveToFirst()) throw IOException("The backup has no home screen layout") }
        } finally {
            db.close()
        }
    }

    private fun prefsToJson(values: Map<String, *>): JSONObject {
        val json = JSONObject()
        for ((key, value) in values) {
            val entry = when (value) {
                is Boolean -> JSONObject().put("t", "b").put("v", value)
                is Int -> JSONObject().put("t", "i").put("v", value)
                is Long -> JSONObject().put("t", "l").put("v", value)
                is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
                is String -> JSONObject().put("t", "s").put("v", value)
                is Set<*> -> JSONObject().put("t", "set").put("v", JSONArray(value))
                else -> null
            }
            if (entry != null) json.put(key, entry)
        }
        return json
    }

    private fun readLimited(input: InputStream, limit: Long): String {
        val out = ByteArrayOutputStream()
        copyLimited(input, out, limit)
        return out.toString(Charsets.UTF_8.name())
    }

    private fun copyLimited(input: InputStream, out: OutputStream, limit: Long) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > limit) throw IOException("The backup is too large")
            out.write(buffer, 0, n)
        }
    }
}
