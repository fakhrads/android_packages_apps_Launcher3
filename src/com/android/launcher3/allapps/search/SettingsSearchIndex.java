/*
 * Copyright (C) 2026 ZeroLauncher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.allapps.search;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.android.launcher3.allapps.search.PixelSearchAdapterProvider.SearchRow;

import org.xmlpull.v1.XmlPullParser;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Settings entries for the drawer search, read the way Settings Intelligence reads them: every
 * system app's SEARCH_INDEXABLES_PROVIDER (XML preference screens plus raw rows), minus the keys
 * each provider marks non-indexable. Opening one goes through Settings' search result trampoline,
 * which allows this launcher package. Without READ_SEARCH_INDEXABLES it falls back to a fixed list
 * of public Settings screens.
 */
final class SettingsSearchIndex {

    private static final String TAG = "PixelSearch";

    private static final String PROVIDER_INTERFACE =
            "android.content.action.SEARCH_INDEXABLES_PROVIDER";
    private static final String READ_SEARCH_INDEXABLES =
            "android.permission.READ_SEARCH_INDEXABLES";
    private static final String SETTINGS_PACKAGE = "com.android.settings";
    private static final String TRAMPOLINE_ACTION = "com.android.settings.SEARCH_RESULT_TRAMPOLINE";
    private static final String EXTRA_SHOW_FRAGMENT = ":settings:show_fragment";
    private static final String EXTRA_SHOW_FRAGMENT_TITLE = ":settings:show_fragment_title";
    private static final String EXTRA_SOURCE_METRICS_CATEGORY = ":settings:source_metrics";
    private static final String EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key";
    private static final int DASHBOARD_SEARCH_RESULTS = 34;

    private static final String NS_ANDROID = "http://schemas.android.com/apk/res/android";
    private static final String NS_APP = "http://schemas.android.com/apk/res-auto";

    private static final long MAX_AGE_MS = 6 * 60 * 60 * 1000L;
    private static final int MAX_RESULTS = 5;

    /** One searchable setting. */
    static final class Entry {
        String title;
        String summary;
        String keywords;
        String screenTitle;
        String key;
        String className;
        String action;
        String targetPackage;
        String targetClass;
        String packageName;
        // Normalized forms for matching
        String nTitle;
        String cTitle;
        String nKeywords;
        String nSummary;

        void prepare() {
            nTitle = SettingsSearchIndex.normalize(title);
            cTitle = nTitle.replace(" ", "");
            nKeywords = SettingsSearchIndex.normalize(keywords);
            nSummary = SettingsSearchIndex.normalize(summary);
        }

        Intent intent() {
            if (TextUtils.isEmpty(action)) {
                // A screen inside Settings: open it through the search result trampoline
                if (!SETTINGS_PACKAGE.equals(packageName) || TextUtils.isEmpty(className)) {
                    return null;
                }
                return new Intent(TRAMPOLINE_ACTION)
                        .setPackage(SETTINGS_PACKAGE)
                        .putExtra(EXTRA_SHOW_FRAGMENT, className)
                        .putExtra(EXTRA_SHOW_FRAGMENT_TITLE, screenTitle)
                        .putExtra(EXTRA_SOURCE_METRICS_CATEGORY, DASHBOARD_SEARCH_RESULTS)
                        .putExtra(EXTRA_FRAGMENT_ARG_KEY, key);
            }
            Intent intent = new Intent(action).putExtra(EXTRA_FRAGMENT_ARG_KEY, key);
            if (!TextUtils.isEmpty(targetPackage) && !TextUtils.isEmpty(targetClass)) {
                intent.setComponent(new ComponentName(targetPackage, targetClass));
            }
            return intent;
        }

        boolean usesTrampoline() {
            return TextUtils.isEmpty(action);
        }
    }

    private static SettingsSearchIndex sInstance;

    static synchronized SettingsSearchIndex get() {
        if (sInstance == null) sInstance = new SettingsSearchIndex();
        return sInstance;
    }

    private volatile List<Entry> mEntries;
    private volatile boolean mFromProviders;
    private Locale mLocale;
    private long mBuiltAt;
    private boolean mBuilding;

    private SettingsSearchIndex() {}

    /** True when the index is fresh; otherwise starts a rebuild on {@code worker}. */
    synchronized boolean ensureFresh(Context context, java.util.concurrent.Executor worker,
            Runnable onBuilt) {
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        boolean fresh = mEntries != null && locale.equals(mLocale)
                && System.currentTimeMillis() - mBuiltAt < MAX_AGE_MS;
        if (fresh || mBuilding) return mEntries != null;
        mBuilding = true;
        Context app = context.getApplicationContext();
        worker.execute(() -> {
            List<Entry> entries = null;
            boolean fromProviders = false;
            long start = System.currentTimeMillis();
            try {
                entries = buildFromProviders(app);
                fromProviders = entries != null;
            } catch (RuntimeException e) {
                Log.w(TAG, "settings index failed", e);
            }
            if (entries == null || entries.isEmpty()) {
                entries = buildFallback(app);
                fromProviders = false;
            }
            Log.d(TAG, "settings index: " + entries.size() + " entries, "
                    + (fromProviders ? "providers" : "fallback") + ", "
                    + (System.currentTimeMillis() - start) + " ms");
            synchronized (SettingsSearchIndex.this) {
                mEntries = entries;
                mFromProviders = fromProviders;
                mLocale = locale;
                mBuiltAt = System.currentTimeMillis();
                mBuilding = false;
            }
            onBuilt.run();
        });
        return mEntries != null;
    }

    /** Best matches for {@code query}, as rows. */
    List<SearchRow> search(Context context, String query) {
        List<Entry> entries = mEntries;
        List<SearchRow> rows = new ArrayList<>();
        String q = normalize(query);
        if (entries == null || q.length() < 2) return rows;
        String cq = q.replace(" ", "");
        List<Scored> scored = new ArrayList<>();
        for (Entry e : entries) {
            int s = score(e, q, cq);
            if (s > 0) scored.add(new Scored(e, s));
        }
        Collections.sort(scored, (a, b) -> a.score != b.score ? b.score - a.score
                : a.entry.title.length() - b.entry.title.length());
        Set<String> seen = new HashSet<>();
        PackageManager pm = context.getPackageManager();
        for (Scored sc : scored) {
            if (rows.size() >= MAX_RESULTS) break;
            Entry e = sc.entry;
            Intent intent = e.intent();
            if (intent == null) continue;
            String dedupe = e.nTitle + "|" + normalize(e.screenTitle);
            if (!seen.add(dedupe)) continue;
            if (!e.usesTrampoline() && intent.resolveActivity(pm) == null) continue;
            String subtitle = !TextUtils.isEmpty(e.screenTitle)
                    && !TextUtils.equals(e.screenTitle, e.title) ? e.screenTitle : e.summary;
            String pkg = e.packageName;
            rows.add(SearchRow.item("set:" + e.packageName + "/" + e.className + "/" + e.key
                            + "/" + e.title,
                    e.title, subtitle,
                    c -> appIcon(c, pkg),
                    (view, launcher) -> e.usesTrampoline()
                            ? PixelSearchAdapterProvider.startForResult(view, launcher, intent)
                            : PixelSearchAdapterProvider.start(view, launcher,
                                    new Intent(intent)))
                    .dedupeBy(e.title, targetPackage(e, intent)));
        }
        return rows;
    }

    /** The app that opens: the component's package, else the provider's. */
    private static String targetPackage(Entry e, Intent intent) {
        if (intent.getComponent() != null) return intent.getComponent().getPackageName();
        if (intent.getPackage() != null) return intent.getPackage();
        return e.packageName;
    }

    boolean isFromProviders() {
        return mFromProviders;
    }

    private static final class Scored {
        final Entry entry;
        final int score;

        Scored(Entry entry, int score) {
            this.entry = entry;
            this.score = score;
        }
    }

    private static int score(Entry e, String q, String cq) {
        if (e.cTitle.startsWith(cq)) return 100;
        if (wordStarts(e.nTitle, q)) return 90;
        if (cq.length() >= 3 && e.cTitle.contains(cq)) return 70;
        if (wordStarts(e.nKeywords, q)) return 50;
        if (cq.length() >= 3 && e.nKeywords.replace(" ", "").contains(cq)) return 40;
        if (q.length() >= 4 && wordStarts(e.nSummary, q)) return 20;
        return 0;
    }

    private static boolean wordStarts(String text, String q) {
        if (text.isEmpty()) return false;
        if (text.startsWith(q)) return true;
        return text.contains(" " + q);
    }

    /** Lowercase, no accents, every run of non letters/digits becomes one space. */
    static String normalize(CharSequence s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(n.length());
        boolean space = true;
        for (int i = 0; i < n.length(); i++) {
            char ch = n.charAt(i);
            if (Character.getType(ch) == Character.NON_SPACING_MARK) continue;
            if (Character.isLetterOrDigit(ch)) {
                sb.append(ch);
                space = false;
            } else if (!space) {
                sb.append(' ');
                space = true;
            }
        }
        int len = sb.length();
        if (len > 0 && sb.charAt(len - 1) == ' ') sb.setLength(len - 1);
        return sb.toString();
    }

    static Drawable appIcon(Context context, String pkg) {
        try {
            return context.getPackageManager().getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    // ---- Building from the providers ----

    private static List<Entry> buildFromProviders(Context context) {
        if (context.checkSelfPermission(READ_SEARCH_INDEXABLES)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "no " + READ_SEARCH_INDEXABLES + ", settings fallback list");
            return null;
        }
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> providers =
                pm.queryIntentContentProviders(new Intent(PROVIDER_INTERFACE), 0);
        List<Entry> entries = new ArrayList<>();
        for (ResolveInfo ri : providers) {
            ProviderInfo pi = ri.providerInfo;
            if (pi == null || pi.applicationInfo == null) continue;
            // Same trust rule as Settings Intelligence: system apps behind the permission only
            if ((pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) continue;
            if (!READ_SEARCH_INDEXABLES.equals(pi.readPermission)) continue;
            try {
                readProvider(context, pi, entries);
            } catch (Exception e) {
                Log.w(TAG, "settings provider " + pi.authority + " failed", e);
            }
        }
        for (Entry e : entries) e.prepare();
        return entries;
    }

    private static void readProvider(Context context, ProviderInfo pi, List<Entry> out)
            throws PackageManager.NameNotFoundException {
        String authority = pi.authority;
        String pkg = pi.packageName;
        Context pkgContext = context.createPackageContext(pkg, 0);
        Set<String> nonIndexable = new HashSet<>();
        try (Cursor c = query(context, authority, "settings/non_indexables_key")) {
            while (c != null && c.moveToNext()) {
                String key = c.getString(0);
                if (key != null) nonIndexable.add(key);
            }
        }
        try (Cursor c = query(context, authority, "settings/indexables_xml_res")) {
            while (c != null && c.moveToNext()) {
                int resId = c.getInt(1);
                if (resId == 0) continue;
                parseXml(pkgContext, pkg, resId, c.getString(2), c.getString(4), c.getString(5),
                        c.getString(6), nonIndexable, out);
            }
        }
        readRaw(context, authority, pkg, "settings/indexables_raw", nonIndexable, out);
        readRaw(context, authority, pkg, "settings/dynamic_indexables_raw", nonIndexable, out);
    }

    private static Cursor query(Context context, String authority, String path) {
        try {
            return context.getContentResolver().query(
                    new Uri.Builder().scheme("content").authority(authority).path(path).build(),
                    null, null, null, null);
        } catch (RuntimeException e) {
            Log.w(TAG, "query " + authority + "/" + path + " failed: " + e);
            return null;
        }
    }

    private static void readRaw(Context context, String authority, String pkg, String path,
            Set<String> nonIndexable, List<Entry> out) {
        try (Cursor c = query(context, authority, path)) {
            while (c != null && c.moveToNext()) {
                Entry e = new Entry();
                e.title = c.getString(1);
                e.summary = c.getString(2);
                e.keywords = c.getString(5);
                e.screenTitle = c.getString(6);
                e.className = c.getString(7);
                e.action = c.getString(9);
                e.targetPackage = c.getString(10);
                e.targetClass = c.getString(11);
                e.key = c.getString(12);
                e.packageName = pkg;
                if (TextUtils.isEmpty(e.title) || nonIndexable.contains(e.key)) continue;
                out.add(e);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "raw " + authority + "/" + path + " failed: " + e);
        }
    }

    private static void parseXml(Context pkgContext, String pkg, int resId, String className,
            String action, String targetPackage, String targetClass, Set<String> nonIndexable,
            List<Entry> out) {
        Resources res = pkgContext.getResources();
        try (XmlResourceParser parser = res.getXml(resId)) {
            String screenTitle = null;
            boolean root = true;
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (type != XmlPullParser.START_TAG) continue;
                String node = parser.getName();
                if ("intent".equals(node) || "extra".equals(node)) continue;
                String title = attr(parser, res, NS_ANDROID, "title");
                if (root) {
                    root = false;
                    screenTitle = title;
                }
                String key = attr(parser, res, NS_ANDROID, "key");
                if (TextUtils.isEmpty(title) || nonIndexable.contains(key)) continue;
                if ("false".equals(attr(parser, res, NS_APP, "searchable"))) continue;
                Entry e = new Entry();
                e.title = title;
                e.key = key;
                e.summary = attr(parser, res, NS_ANDROID, "summary");
                e.keywords = attr(parser, res, NS_APP, "keywords");
                e.screenTitle = screenTitle;
                e.className = className;
                e.action = action;
                e.targetPackage = targetPackage;
                e.targetClass = targetClass;
                e.packageName = pkg;
                out.add(e);
            }
        } catch (Exception e) {
            Log.w(TAG, "xml " + pkg + "/" + Integer.toHexString(resId) + " failed: " + e);
        }
    }

    /** An attribute's text, resolving references in the provider's own resources. */
    private static String attr(XmlResourceParser parser, Resources res, String ns, String name) {
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            if (!name.equals(parser.getAttributeName(i))) continue;
            String attrNs = parser.getAttributeNamespace(i);
            if (NS_ANDROID.equals(ns) ? !NS_ANDROID.equals(attrNs) : NS_ANDROID.equals(attrNs)) {
                continue;
            }
            int ref = parser.getAttributeResourceValue(i, 0);
            if (ref != 0) {
                try {
                    CharSequence text = res.getText(ref);
                    return text != null ? text.toString() : null;
                } catch (Resources.NotFoundException e) {
                    return null;
                }
            }
            String value = parser.getAttributeValue(i);
            return value != null && value.startsWith("?") ? null : value;
        }
        return null;
    }

    // ---- Fallback: public Settings screens ----

    private static final String[][] FALLBACK = {
            // title, keywords, action
            {"Wi‑Fi", "wifi wlan wireless internet network hotspot", Settings.ACTION_WIFI_SETTINGS},
            {"Internet", "network mobile data sim", Settings.ACTION_WIRELESS_SETTINGS},
            {"Bluetooth", "bt pair headphones", Settings.ACTION_BLUETOOTH_SETTINGS},
            {"Connected devices", "bluetooth cast nfc usb", "android.settings.CONNECTED_DEVICES_SETTINGS"},
            {"Hotspot & tethering", "hotspot tether", "android.settings.TETHER_SETTINGS"},
            {"Mobile network", "sim carrier roaming apn 5g lte", Settings.ACTION_NETWORK_OPERATOR_SETTINGS},
            {"Data usage", "data saver mobile data", Settings.ACTION_DATA_USAGE_SETTINGS},
            {"Airplane mode", "flight", Settings.ACTION_AIRPLANE_MODE_SETTINGS},
            {"VPN", "vpn private network", Settings.ACTION_VPN_SETTINGS},
            {"NFC", "nfc tap pay contactless", Settings.ACTION_NFC_SETTINGS},
            {"Display", "brightness screen timeout font size", Settings.ACTION_DISPLAY_SETTINGS},
            {"Dark theme", "dark mode night", "android.settings.DARK_THEME_SETTINGS"},
            {"Night Light", "blue light eye comfort", Settings.ACTION_NIGHT_DISPLAY_SETTINGS},
            {"Sound & vibration", "volume ringtone vibrate mute", Settings.ACTION_SOUND_SETTINGS},
            {"Do Not Disturb", "dnd silence priority", "android.settings.ZEN_MODE_SETTINGS"},
            {"Notifications", "notification alerts", "android.settings.NOTIFICATION_SETTINGS"},
            {"Battery", "battery power usage", "android.intent.action.POWER_USAGE_SUMMARY"},
            {"Battery Saver", "power saving", Settings.ACTION_BATTERY_SAVER_SETTINGS},
            {"Storage", "space memory files", Settings.ACTION_INTERNAL_STORAGE_SETTINGS},
            {"Apps", "applications app info", Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS},
            {"Default apps", "default browser launcher sms", Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS},
            {"Location", "gps", Settings.ACTION_LOCATION_SOURCE_SETTINGS},
            {"Security", "lock screen fingerprint face unlock password pin", Settings.ACTION_SECURITY_SETTINGS},
            {"Privacy", "permissions", Settings.ACTION_PRIVACY_SETTINGS},
            {"Accessibility", "talkback magnification", Settings.ACTION_ACCESSIBILITY_SETTINGS},
            {"Accounts", "sync google account", Settings.ACTION_SYNC_SETTINGS},
            {"Languages", "language locale", Settings.ACTION_LOCALE_SETTINGS},
            {"Keyboard", "input method ime gboard", Settings.ACTION_INPUT_METHOD_SETTINGS},
            {"Date & time", "clock timezone", Settings.ACTION_DATE_SETTINGS},
            {"Users", "multiple users guest", Settings.ACTION_USER_SETTINGS},
            {"Developer options", "developer usb debugging adb", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS},
            {"About phone", "device info model build android version", Settings.ACTION_DEVICE_INFO_SETTINGS},
            {"Cast", "screen mirror chromecast", Settings.ACTION_CAST_SETTINGS},
            {"Printing", "print", Settings.ACTION_PRINT_SETTINGS},
    };

    private static List<Entry> buildFallback(Context context) {
        List<Entry> entries = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        for (String[] f : FALLBACK) {
            Intent intent = new Intent(f[2]);
            ResolveInfo ri = pm.resolveActivity(intent, 0);
            if (ri == null || ri.activityInfo == null) continue;
            Entry e = new Entry();
            e.title = f[0];
            e.keywords = f[1];
            e.action = f[2];
            e.packageName = ri.activityInfo.packageName;
            e.prepare();
            entries.add(e);
        }
        return entries;
    }
}
