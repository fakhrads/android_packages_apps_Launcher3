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

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.launcher3.allapps.search.PixelSearchAdapterProvider.SearchRow;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** App shortcuts and contacts for the drawer search. Each source fails closed to no rows. */
final class DeviceSearchSources {

    private static final String TAG = "PixelSearch";

    private static final long SHORTCUT_CACHE_MS = 60_000L;
    private static final int MAX_SHORTCUTS = 5;
    private static final int MAX_TOP_APP_SHORTCUTS = 3;
    private static final int MAX_CONTACTS = 4;
    private static final int REQUEST_CODE_CONTACTS = 0x5a18;
    private static final String PREFS = "zl_pixel_search";
    private static final String PREF_CONTACTS_ASKED = "contacts_permission_asked";

    private static List<ShortcutInfo> sShortcuts;
    private static long sShortcutsAt;

    private DeviceSearchSources() {}

    // ---- Shortcuts ----

    /**
     * The top app's own shortcuts first, then any shortcut whose label matches, at most
     * {@link #MAX_SHORTCUTS}.
     */
    static List<SearchRow> shortcuts(Context context, String query, String topPackage,
            UserHandle topUser) {
        List<SearchRow> rows = new ArrayList<>();
        String q = SettingsSearchIndex.normalize(query);
        if (q.length() < 2) return rows;
        LauncherApps la = context.getSystemService(LauncherApps.class);
        if (la == null || !la.hasShortcutHostPermission()) return rows;
        List<ShortcutInfo> all = allShortcuts(context, la);
        String cq = q.replace(" ", "");
        List<ShortcutInfo> picked = new ArrayList<>();
        if (topPackage != null) {
            List<ShortcutInfo> own = new ArrayList<>();
            for (ShortcutInfo si : all) {
                if (topPackage.equals(si.getPackage()) && si.getUserHandle().equals(topUser)) {
                    own.add(si);
                }
            }
            Collections.sort(own, (a, b) -> a.getRank() - b.getRank());
            for (int i = 0; i < own.size() && i < MAX_TOP_APP_SHORTCUTS; i++) picked.add(own.get(i));
        }
        for (ShortcutInfo si : all) {
            if (picked.size() >= MAX_SHORTCUTS) break;
            if (picked.contains(si)) continue;
            String label = SettingsSearchIndex.normalize(label(si));
            if (label.replace(" ", "").startsWith(cq) || label.contains(" " + q)
                    || label.startsWith(q)) {
                picked.add(si);
            }
        }
        PackageManager pm = context.getPackageManager();
        for (ShortcutInfo si : picked) {
            CharSequence appLabel = null;
            try {
                appLabel = pm.getApplicationLabel(pm.getApplicationInfo(si.getPackage(), 0));
            } catch (PackageManager.NameNotFoundException ignored) {
                // Shown without the app name
            }
            int density = context.getResources().getDisplayMetrics().densityDpi;
            rows.add(SearchRow.item(shortcutId(si.getPackage(), si.getId()), label(si), appLabel,
                    c -> c.getSystemService(LauncherApps.class)
                            .getShortcutIconDrawable(si, density),
                    (view, launcher) -> {
                        view.getContext().getSystemService(LauncherApps.class)
                                .startShortcut(si, null, null);
                        return true;
                    }).dedupeBy(label(si), si.getPackage()));
        }
        return rows;
    }

    static String shortcutId(String pkg, String id) {
        return "sc:" + pkg + "/" + id;
    }

    private static CharSequence label(ShortcutInfo si) {
        CharSequence label = si.getShortLabel();
        return TextUtils.isEmpty(label) ? si.getLongLabel() : label;
    }

    private static synchronized List<ShortcutInfo> allShortcuts(Context context, LauncherApps la) {
        long now = System.currentTimeMillis();
        if (sShortcuts != null && now - sShortcutsAt < SHORTCUT_CACHE_MS) return sShortcuts;
        List<ShortcutInfo> out = new ArrayList<>();
        UserManager um = context.getSystemService(UserManager.class);
        for (UserHandle user : la.getProfiles()) {
            if (um != null && um.isQuietModeEnabled(user)) continue;
            try {
                List<ShortcutInfo> list = la.getShortcuts(new LauncherApps.ShortcutQuery()
                        .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                                | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST), user);
                if (list == null) continue;
                for (ShortcutInfo si : list) {
                    if (si.isEnabled() && !TextUtils.isEmpty(label(si))) out.add(si);
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "shortcuts for " + user + " failed: " + e);
            }
        }
        sShortcuts = out;
        sShortcutsAt = now;
        return out;
    }

    // ---- Contacts ----

    /** Matching contacts, or a one-time row asking for the contacts permission. */
    static List<SearchRow> contacts(Context context, String query) {
        List<SearchRow> rows = new ArrayList<>();
        String q = query.trim();
        if (q.length() < 2) return rows;
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            if (!prefs(context).getBoolean(PREF_CONTACTS_ASKED, false)) {
                rows.add(new SearchRow("contacts:permission", false, "Show contacts in search",
                        "Allow access to your contacts", c -> contactsIcon(c),
                        (view, launcher) -> askContacts(view.getContext(), launcher),
                        false, false));
            }
            return rows;
        }
        Uri uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI,
                Uri.encode(q));
        String[] projection = {
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
        };
        Bundle args = new Bundle();
        args.putInt(ContentResolver.QUERY_ARG_LIMIT, MAX_CONTACTS * 2);
        Set<String> seen = new HashSet<>();
        try (Cursor c = context.getContentResolver().query(uri, projection, args, null)) {
            while (c != null && c.moveToNext() && rows.size() < MAX_CONTACTS) {
                long id = c.getLong(0);
                String lookup = c.getString(1);
                String name = c.getString(2);
                String photo = c.getString(3);
                if (TextUtils.isEmpty(name) || !seen.add(lookup != null ? lookup : name)) continue;
                Uri contact = ContactsContract.Contacts.getLookupUri(id, lookup);
                rows.add(new SearchRow("contact:" + (lookup != null ? lookup : id), false, name,
                        null, ctx -> contactPhoto(ctx, photo, name),
                        (view, launcher) -> PixelSearchAdapterProvider.start(view, launcher,
                                new Intent(Intent.ACTION_VIEW, contact)),
                        true, true));
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "contacts query failed: " + e);
        }
        return rows;
    }

    private static boolean askContacts(Context context, Object launcher) {
        prefs(context).edit().putBoolean(PREF_CONTACTS_ASKED, true).apply();
        if (launcher instanceof Activity activity) {
            activity.requestPermissions(new String[] {Manifest.permission.READ_CONTACTS},
                    REQUEST_CODE_CONTACTS);
            return true;
        }
        return false;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Drawable contactsIcon(Context context) {
        Intent intent = new Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI);
        try {
            return context.getPackageManager().getActivityIcon(intent);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private static Drawable contactPhoto(Context context, String photo, String name) {
        if (photo != null) {
            try (InputStream in = context.getContentResolver().openInputStream(Uri.parse(photo))) {
                Drawable d = in != null
                        ? BitmapDrawable.createFromStream(in, name) : null;
                if (d != null) return d;
            } catch (Exception e) {
                // Letter tile below
            }
        }
        return new LetterTile(name);
    }

    /** First letter on a colored circle, for contacts without a photo. */
    private static final class LetterTile extends Drawable {
        private static final int[] COLORS = {
                0xFF5E97F6, 0xFF4DB6AC, 0xFFF06292, 0xFFFFB74D, 0xFF9575CD, 0xFF81C784};
        private final Paint mBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String mLetter;

        LetterTile(String name) {
            String n = name == null ? "" : name.trim();
            mLetter = n.isEmpty() ? "?" : n.substring(0, n.offsetByCodePoints(0, 1)).toUpperCase();
            mBg.setColor(COLORS[Math.abs(n.hashCode()) % COLORS.length]);
            mText.setColor(0xFFFFFFFF);
            mText.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect b = getBounds();
            float r = Math.min(b.width(), b.height()) / 2f;
            canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), r, mBg);
            mText.setTextSize(r);
            float y = b.exactCenterY() - (mText.descent() + mText.ascent()) / 2f;
            canvas.drawText(mLetter, b.exactCenterX(), y, mText);
        }

        @Override
        public void setAlpha(int alpha) {
            mBg.setAlpha(alpha);
            mText.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            mBg.setColorFilter(colorFilter);
            mText.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return 96;
        }

        @Override
        public int getIntrinsicHeight() {
            return 96;
        }
    }
}
