/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.settings.compose.data

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.Item
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherFiles
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherSettings
import com.android.launcher3.R
import com.android.launcher3.SessionCommitReceiver
import com.android.launcher3.Utilities
import com.android.launcher3.customization.IconDatabase
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.model.DatabaseHelper
import com.android.launcher3.model.ModelDbController
import com.android.launcher3.qsb.QsbLayout
import com.android.launcher3.util.CustomAppNameStore
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Backs up the home screen layout and the launcher settings to one zip, and restores it:
 * every shared prefs file of the launcher (settings, custom names and icons, the grid) as JSON,
 * and a snapshot of the current grid's database.
 */
object LayoutBackup {

    private const val TAG = "LayoutBackup"
    private const val VERSION = 1
    const val REQUEST_BACKUP = 4101
    const val REQUEST_RESTORE = 4102
    private const val ENTRY_META = "meta.json"
    private const val ENTRY_PREFS = "prefs.json"
    private const val ENTRY_DB = "launcher.db"
    private const val MAX_PREFS_BYTES = 8L shl 20
    private const val MAX_DB_BYTES = 128L shl 20

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
        val dbFile = liveDbFile(context)
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

    /**
     * Restores a backup. The launcher has to restart afterwards to load it.
     *
     * Everything is read and checked first: the database (schema version, integrity, a home
     * screen table) and every setting against [FILE_SCHEMAS]. Only then the layout is written,
     * then the settings, so a bad file changes nothing.
     */
    @Throws(IOException::class)
    fun restore(context: Context, input: InputStream) {
        var meta: JSONObject? = null
        var prefs: JSONObject? = null
        val staged = File(context.cacheDir, "layout-restore.db")
        staged.delete()
        try {
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
            } catch (e: JSONException) {
                throw BackupException(R.string.zl_backup_error_damaged, cause = e)
            } catch (e: ZipException) {
                throw BackupException(R.string.zl_backup_error_not_backup, cause = e)
            }
            if (meta == null || prefs == null || !staged.exists()) {
                throw BackupException(R.string.zl_backup_error_not_backup)
            }
            val version = meta.optInt("version", 0)
            if (version > VERSION) throw BackupException(R.string.zl_backup_error_newer)
            if (version < 1) throw BackupException(R.string.zl_backup_error_not_backup)
            val dbFile = meta.optString("dbFile")
            if (dbFile !in LauncherFiles.GRID_DB_FILES) {
                throw BackupException(R.string.zl_backup_error_damaged)
            }
            checkDatabase(staged)
            val plan = parsePrefs(prefs)

            writeDatabase(context, staged, dbFile)
            writePrefs(context, plan)
        } finally {
            staged.delete()
        }
    }

    /** A restore or backup problem, told to the user by [reason]. */
    class BackupException(
        @StringRes val reason: Int,
        val arg: String? = null,
        cause: Throwable? = null,
    ) : IOException("backup error $reason ${arg.orEmpty()}", cause)

    /** What one setting may hold: its type tag in the backup and a check of its value. */
    private class PrefSpec(val type: String, val valid: (Any) -> Boolean = { true })

    /**
     * What a prefs file restores. Keys not covered are device state (restore markers,
     * statistics, tips) and stay as they are on this phone.
     */
    private class FileSchema(
        private val keys: Map<String, PrefSpec>,
        private val anyKey: PrefSpec? = null,
    ) {
        fun spec(key: String): PrefSpec? =
            keys[key] ?: anyKey?.takeIf { key.isNotEmpty() && key.length <= MAX_KEY_LENGTH }
    }

    private const val MAX_KEY_LENGTH = 1024
    private const val MAX_STRING_LENGTH = 4096
    private const val MAX_TABS_LENGTH = 256 * 1024
    private const val MAX_SET_SIZE = 4096
    private val GRID_NAME_PATTERN = Regex("[A-Za-z0-9_ .\\-]{1,64}")

    private fun shortString(value: Any) = (value as String).length <= MAX_STRING_LENGTH

    private fun typeTag(item: Item): String = when {
        item.type == String::class.java || item.type.isEnum -> "s"
        item.type == Boolean::class.java || item.type == java.lang.Boolean::class.java -> "b"
        item.type == Int::class.java || item.type == java.lang.Integer::class.java -> "i"
        item.type == Long::class.java || item.type == java.lang.Long::class.java -> "l"
        item.type == Float::class.java || item.type == java.lang.Float::class.java -> "f"
        Set::class.java.isAssignableFrom(item.type) -> "set"
        else -> throw IllegalArgumentException("unsupported pref type ${item.type}")
    }

    /** The launcher settings and the home screen grid state that matches the database. */
    private fun mainSchema(): Map<String, PrefSpec> {
        val items = listOf(
            // Grid state of the backed up database
            LauncherPrefs.GRID_NAME, LauncherPrefs.NON_FIXED_LANDSCAPE_GRID_NAME,
            LauncherPrefs.WORKSPACE_SIZE, LauncherPrefs.HOTSEAT_COUNT, LauncherPrefs.DEVICE_TYPE,
            LauncherPrefs.DB_FILE, LauncherPrefs.GRID_TYPE, LauncherPrefs.FIXED_LANDSCAPE_MODE,
            // Settings
            LauncherPrefs.ENABLE_TWOLINE_ALLAPPS_TOGGLE, LauncherPrefs.WALLPAPER_CAROUSEL,
            LauncherPrefs.WALLPAPER_CAROUSEL_LOCKSCREEN, LauncherPrefs.WALLPAPER_SCROLLING,
            LauncherPrefs.WORKSPACE_LOCK, LauncherPrefs.ALL_APPS_DARK_TEXT,
            LauncherPrefs.ALL_APPS_SEARCH_PLACEMENT, LauncherPrefs.ALLAPPS_THEMED_ICONS,
            LauncherPrefs.ALLAPPS_ICON_CUSTOMIZATION, LauncherPrefs.ALLOW_WALLPAPER_ZOOMING,
            LauncherPrefs.APP_DRAWER_CUSTOM_COLOR_ENABLED, LauncherPrefs.APP_DRAWER_CUSTOM_COLOR_LIGHT,
            LauncherPrefs.APP_DRAWER_CUSTOM_COLOR_DARK, LauncherPrefs.APP_DRAWER_OPACITY,
            LauncherPrefs.APP_DRAWER_SORT_MODE, LauncherPrefs.APP_DRAWER_STYLE,
            LauncherPrefs.AUTO_HIDE_DOTS, LauncherPrefs.BLUR_DEPTH, LauncherPrefs.DARK_STATUS_BAR,
            LauncherPrefs.GRAYSCALE_ICON_MAP_LAWN, LauncherPrefs.DOCK_MUSIC_SEARCH,
            LauncherPrefs.DOCK_SEARCH, LauncherPrefs.DOCK_THEME, LauncherPrefs.QSB_OUTER_OPACITY,
            LauncherPrefs.DRAWER_OPEN_KEYBOARD, LauncherPrefs.DRAWER_SCROLLBAR,
            LauncherPrefs.DRAWER_SEARCH, LauncherPrefs.DRAWER_LIST, LauncherPrefs.FONT_SIZE,
            LauncherPrefs.FREEFORM_GESTURE, LauncherPrefs.LPNH_FULL_WIDTH,
            LauncherPrefs.CORNER_SWIPE_ACTION, LauncherPrefs.MOTION_STYLE,
            LauncherPrefs.MOTION_RESPONSE, LauncherPrefs.SURFACE_TRANSLUCENCY,
            LauncherPrefs.BOUNCY_ANIMATIONS, LauncherPrefs.BOUNCY_STRENGTH,
            LauncherPrefs.BOUNCY_SPEED, LauncherPrefs.ICON_TAP_HAPTIC, LauncherPrefs.DRAWER_OPEN_HAPTIC,
            LauncherPrefs.OVERVIEW_GESTURE_ACTION, LauncherPrefs.FREEFORM_GESTURE_PROGRESS,
            LauncherPrefs.SWIPE_DOWN_TO_LOCK, LauncherPrefs.HOTSEAT_OPACITY,
            LauncherPrefs.HOTSEAT_QSB_OPACITY, LauncherPrefs.HOTSEAT_QSB_OUTER_OPACITY,
            LauncherPrefs.HOTSEAT_QSB_STROKE_WIDTH, LauncherPrefs.HOTSEAT_QSB_SPACE,
            LauncherPrefs.SEARCH_RADIUS_SIZE, LauncherPrefs.ICON_SIZE,
            LauncherPrefs.RECENTS_CLEAR_ALL, LauncherPrefs.RECENTS_CLEAR_ALL_PRESERVE_ACTIVE,
            LauncherPrefs.RECENTS_LENS, LauncherPrefs.RECENTS_SELECT, LauncherPrefs.RECENTS_MEMINFO,
            LauncherPrefs.RECENTS_MEMINFO_ZRAM, LauncherPrefs.RECENTS_STYLE,
            LauncherPrefs.RECENTS_OPACITY, LauncherPrefs.RECENTS_SCREENSHOT,
            LauncherPrefs.RECENTS_SCROLL_VIBRATE, LauncherPrefs.ROW_HEIGHT,
            LauncherPrefs.SHORT_PARALLAX, LauncherPrefs.SHOW_DESKTOP_LABELS,
            LauncherPrefs.SHOW_DRAWER_LABELS, LauncherPrefs.SHOW_HOTSEAT_BG,
            LauncherPrefs.SHOW_QUICKSPACE, LauncherPrefs.SHOW_QUICKSPACE_ALT,
            LauncherPrefs.SHOW_QUICKSPACE_CLOCK, LauncherPrefs.QUICKSPACE_CLOCK_COLOR,
            LauncherPrefs.SHOW_QUICKSPACE_PSONALITY, LauncherPrefs.SHOW_QUICKSPACE_NOWPLAYING,
            LauncherPrefs.SHOW_QUICKSPACE_GOOGLE, LauncherPrefs.HIDDEN_APPS,
            LauncherPrefs.DRAWER_TABS, LauncherPrefs.DRAWER_TABS_AUTO,
            LauncherPrefs.DRAWER_CUSTOM_TABS, LauncherPrefs.SHOW_QUICKSPACE_WEATHER,
            LauncherPrefs.SHOW_QUICKSPACE_WEATHER_CITY, LauncherPrefs.SHOW_QUICKSPACE_WEATHER_TEXT,
            LauncherPrefs.SHOW_STATUS_BAR, LauncherPrefs.SHOW_TOP_SHADOW,
            LauncherPrefs.SINGLE_PAGE_CENTER, LauncherPrefs.SLEEP_GESTURE,
            LauncherPrefs.SLEEP_GESTURE_HAPTIC, LauncherPrefs.DOUBLE_TAP_ACTION,
            LauncherPrefs.PINCH_ACTION, LauncherPrefs.NAVBAR_BURN_IN_PROTECTION,
            LauncherPrefs.NAVBAR_BURN_IN_INTERVAL, LauncherPrefs.WORKSPACE_ITEMS_LABEL_HIDDEN,
            LauncherPrefs.EXPANDED_POPUP_MENU_SECTION, ThemeManager.PREF_ICON_SHAPE,
        )
        // The ranges of the settings sliders
        val ranges = mapOf(
            LauncherPrefs.APP_DRAWER_OPACITY to 0..100,
            LauncherPrefs.ROW_HEIGHT to 75..150,
            LauncherPrefs.ICON_SIZE to 50..150,
            LauncherPrefs.FONT_SIZE to 50..150,
            LauncherPrefs.BLUR_DEPTH to 0..175,
            LauncherPrefs.SURFACE_TRANSLUCENCY to 0..150,
            LauncherPrefs.MOTION_RESPONSE to 50..200,
            LauncherPrefs.BOUNCY_STRENGTH to 25..200,
            LauncherPrefs.BOUNCY_SPEED to 50..200,
            LauncherPrefs.NAVBAR_BURN_IN_INTERVAL to 15..300,
            LauncherPrefs.RECENTS_OPACITY to 0..100,
            LauncherPrefs.FREEFORM_GESTURE_PROGRESS to 10..50,
            LauncherPrefs.QSB_OUTER_OPACITY to 0..100,
            LauncherPrefs.HOTSEAT_QSB_SPACE to 10..100,
            LauncherPrefs.HOTSEAT_OPACITY to 0..100,
            LauncherPrefs.HOTSEAT_QSB_OPACITY to 0..100,
            LauncherPrefs.HOTSEAT_QSB_OUTER_OPACITY to 0..100,
        ).mapKeys { it.key.sharedPrefKey }
        val specs = HashMap<String, PrefSpec>()
        for (item in items) {
            val key = item.sharedPrefKey
            val tag = typeTag(item)
            val range = ranges[key]
            specs[key] = when {
                range != null -> PrefSpec(tag) { it as Int in range }
                item.type.isEnum -> PrefSpec(tag) { value ->
                    item.type.enumConstants?.any { (it as Enum<*>).name == value } == true
                }
                tag == "s" -> PrefSpec(tag) { shortString(it) }
                tag == "set" -> PrefSpec(tag) { value ->
                    val set = value as Set<*>
                    set.size <= MAX_SET_SIZE && set.all { (it as String).length <= MAX_KEY_LENGTH }
                }
                else -> PrefSpec(tag)
            }
        }
        val gridName = PrefSpec("s") { GRID_NAME_PATTERN.matches(it as String) }
        specs[LauncherPrefs.GRID_NAME.sharedPrefKey] = gridName
        specs[LauncherPrefs.NON_FIXED_LANDSCAPE_GRID_NAME.sharedPrefKey] = gridName
        specs[LauncherPrefs.DB_FILE.sharedPrefKey] =
            PrefSpec("s") { it == "" || it in LauncherFiles.GRID_DB_FILES }
        specs[LauncherPrefs.DRAWER_CUSTOM_TABS.sharedPrefKey] = PrefSpec("s") {
            val text = it as String
            text.length <= MAX_TABS_LENGTH && runCatching { JSONArray(text) }.isSuccess
        }
        // Settings stored under plain keys, without a LauncherPrefs item
        val bool = PrefSpec("b")
        specs[QsbLayout.PREF_DOCK_SEARCH_LAUNCHER] = bool
        specs[SessionCommitReceiver.ADD_ICON_PREFERENCE_KEY] = bool
        specs["pref_enable_minus_one"] = bool
        specs["themed_icons"] = bool
        specs[IconDatabase.KEY_ICON_PACK] = PrefSpec("s") { shortString(it) }
        specs["icon_theme_id"] = PrefSpec("s") { shortString(it) }
        return specs
    }

    /** The prefs files a backup restores, by name. */
    private val FILE_SCHEMAS: Map<String, FileSchema> by lazy {
        val componentString = PrefSpec("s") { shortString(it) }
        mapOf(
            LauncherFiles.SHARED_PREFERENCES_KEY to FileSchema(mainSchema()),
            // Custom names and icons per app, keyed by component
            CustomAppNameStore.CUSTOM_NAMES_PREFS to FileSchema(emptyMap(), componentString),
            IconDatabase.PREF_FILE_NAME to FileSchema(emptyMap(), componentString),
        )
    }

    /** Decodes one backed up value, or null when it is not what its type tag says. */
    private fun decode(entry: JSONObject): Pair<String, Any>? {
        val tag = entry.optString("t")
        val v = entry.opt("v") ?: return null
        val value: Any? = when (tag) {
            "b" -> v as? Boolean
            "i" -> v as? Int
            "l" -> when (v) { is Int -> v.toLong(); is Long -> v; else -> null }
            "f" -> (v as? Number)?.toFloat()?.takeIf { it.isFinite() }
            "s" -> v as? String
            "set" -> (v as? JSONArray)?.let { array ->
                val set = HashSet<String>()
                for (i in 0 until array.length()) set.add(array.opt(i) as? String ?: return null)
                set
            }
            else -> null
        }
        return value?.let { tag to it }
    }

    /** Checks every setting in the backup and returns what to write, per prefs file. */
    private fun parsePrefs(json: JSONObject): Map<String, Map<String, Any>> {
        if (json.optJSONObject(LauncherFiles.SHARED_PREFERENCES_KEY) == null) {
            throw BackupException(R.string.zl_backup_error_damaged)
        }
        val plan = HashMap<String, Map<String, Any>>()
        for ((name, schema) in FILE_SCHEMAS) {
            val values = HashMap<String, Any>()
            // A file the backup does not have had nothing in it, so it restores to empty
            val entries = json.optJSONObject(name)
            if (entries != null) {
                val keys = entries.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val spec = schema.spec(key) ?: continue
                    val entry = entries.optJSONObject(key)
                        ?: throw BackupException(R.string.zl_backup_error_bad_value, key)
                    val (tag, value) = decode(entry)
                        ?: throw BackupException(R.string.zl_backup_error_bad_value, key)
                    if (tag != spec.type || !spec.valid(value)) {
                        throw BackupException(R.string.zl_backup_error_bad_value, key)
                    }
                    values[key] = value
                }
            }
            plan[name] = values
        }
        return plan
    }

    /** The database file the model has open, see ModelDbController. */
    private fun liveDbFile(context: Context): String =
        LauncherPrefs.get(context).get(LauncherPrefs.DB_FILE)
            .ifEmpty { InvariantDeviceProfile.INSTANCE.get(context).dbFile }

    private fun writeDatabase(context: Context, staged: File, dbFile: String) {
        if (dbFile == liveDbFile(context)) {
            // The model keeps this file open, with its WAL: swapping the files under it would mix
            // the old WAL into the new database. Replace the rows through its own connection.
            val controller = LauncherAppState.getInstance(context).model.modelDbController
            try {
                MODEL_EXECUTOR.submit(Callable { copyFavorites(staged, controller) }).get()
            } catch (e: ExecutionException) {
                throw e.cause ?: e
            }
        } else {
            // Nothing has this grid's file open, so it can be replaced as a whole
            val target = context.getDatabasePath(dbFile)
            val temp = File(target.path + ".restore")
            Files.copy(staged.toPath(), temp.toPath(), StandardCopyOption.REPLACE_EXISTING)
            for (suffix in arrayOf("-wal", "-shm", "-journal")) File(target.path + suffix).delete()
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE)
        }
    }

    /** Replaces the home screen items of the live database with the backup's, in one transaction. */
    private fun copyFavorites(staged: File, controller: ModelDbController) {
        val table = LauncherSettings.Favorites.TABLE_NAME
        val source = SQLiteDatabase.openDatabase(staged.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            controller.newTransaction().use { transaction ->
                val db = transaction.db
                val liveColumns = tableColumns(db, table)
                val columns = tableColumns(source, table).filter { it in liveColumns }
                db.delete(table, null, null)
                source.query(table, columns.toTypedArray(), null, null, null, null, null).use { c ->
                    val values = ContentValues()
                    while (c.moveToNext()) {
                        values.clear()
                        for (i in columns.indices) {
                            val column = columns[i]
                            when (c.getType(i)) {
                                Cursor.FIELD_TYPE_NULL -> values.putNull(column)
                                Cursor.FIELD_TYPE_INTEGER -> values.put(column, c.getLong(i))
                                Cursor.FIELD_TYPE_FLOAT -> values.put(column, c.getDouble(i))
                                Cursor.FIELD_TYPE_BLOB -> values.put(column, c.getBlob(i))
                                else -> values.put(column, c.getString(i))
                            }
                        }
                        db.insertOrThrow(table, null, values)
                    }
                }
                transaction.commit()
            }
        } finally {
            source.close()
        }
    }

    private fun tableColumns(db: SQLiteDatabase, table: String): List<String> {
        val columns = ArrayList<String>()
        db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            val name = c.getColumnIndexOrThrow("name")
            while (c.moveToNext()) columns.add(c.getString(name))
        }
        return columns
    }

    private fun writePrefs(context: Context, plan: Map<String, Map<String, Any>>) {
        for ((name, values) in plan) {
            val schema = FILE_SCHEMAS.getValue(name)
            val sp = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val editor = sp.edit()
            // Not clear(): listeners get a null key for it. Only keys the backup covers go, a
            // setting missing from it was at its default.
            for (key in sp.all.keys) {
                if (key !in values && schema.spec(key) != null) editor.remove(key)
            }
            for ((key, value) in values) {
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is String -> editor.putString(key, value)
                    is Set<*> -> editor.putStringSet(key, value.mapTo(HashSet()) { it as String })
                }
            }
            if (!editor.commit()) throw IOException("Could not write $name")
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
                        ?: throw BackupException(R.string.zl_backup_error_open)
                    out.use { export(app, it) }
                    main.post { Toast.makeText(app, R.string.zl_backup_saved, Toast.LENGTH_LONG).show() }
                } else {
                    val input = app.contentResolver.openInputStream(uri)
                        ?: throw BackupException(R.string.zl_backup_error_open)
                    input.use { restore(app, it) }
                    main.post {
                        Toast.makeText(app, R.string.restarting_launcher, Toast.LENGTH_SHORT).show()
                        Utilities.restart()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "backup request $requestCode failed", e)
                val reason = when {
                    e is BackupException && e.arg != null -> app.getString(e.reason, e.arg)
                    e is BackupException -> app.getString(e.reason)
                    else -> e.message ?: e.javaClass.simpleName
                }
                val title = if (requestCode == REQUEST_BACKUP) R.string.zl_backup_failed
                    else R.string.zl_restore_failed
                main.post {
                    Toast.makeText(app, app.getString(title, reason), Toast.LENGTH_LONG).show()
                }
            }
        }.start()
        return true
    }

    private fun checkDatabase(file: File) {
        val db = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            throw BackupException(R.string.zl_backup_error_damaged, cause = e)
        }
        try {
            // A newer schema would be downgraded by wiping it, so refuse it here
            val version = DatabaseUtils.longForQuery(db, "PRAGMA user_version", null)
            if (version > DatabaseHelper.SCHEMA_VERSION) {
                throw BackupException(R.string.zl_backup_error_newer)
            }
            if (version < 1) throw BackupException(R.string.zl_backup_error_damaged)
            if (DatabaseUtils.stringForQuery(db, "PRAGMA quick_check", null) != "ok") {
                throw BackupException(R.string.zl_backup_error_damaged)
            }
            db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                arrayOf(LauncherSettings.Favorites.TABLE_NAME)
            ).use { if (!it.moveToFirst()) throw BackupException(R.string.zl_backup_error_no_layout) }
        } catch (e: SQLiteException) {
            throw BackupException(R.string.zl_backup_error_damaged, cause = e)
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
            if (total > limit) throw BackupException(R.string.zl_backup_error_too_large)
            out.write(buffer, 0, n)
        }
    }
}
