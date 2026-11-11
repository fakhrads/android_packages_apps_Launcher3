/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.allapps;

import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.AppsListUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The tabs above the app drawer list: All, the tabs the user made, and (when on) one per app
 * category, the same categories Smart Auto-Organize uses. Apps are kept as flattened component
 * names, like hidden apps, so a tab holds an app in every profile.
 */
public final class DrawerTabs {

    private static final String TAG = "DrawerTabs";

    public static final String ALL = "all";
    private static final String CATEGORY_PREFIX = "category:";
    private static final int MAX_NAME_LENGTH = 40;

    private DrawerTabs() {}

    /** A tab the user made. */
    public static final class CustomTab {
        public final String id;
        public final String name;
        public final Set<String> apps;

        public CustomTab(String id, String name, Set<String> apps) {
            this.id = id;
            this.name = name;
            this.apps = Collections.unmodifiableSet(apps);
        }
    }

    /** One tab of the drawer; {@link #apps} is null for All. */
    public static final class Tab {
        public final String id;
        public final String title;
        @Nullable public final Set<String> apps;
        public final boolean custom;

        Tab(String id, String title, @Nullable Set<String> apps, boolean custom) {
            this.id = id;
            this.title = title;
            this.apps = apps;
            this.custom = custom;
        }

        public boolean matches(ItemInfo info) {
            if (apps == null) return true;
            ComponentName component = info.getTargetComponent();
            return component != null && apps.contains(component.flattenToShortString());
        }
    }

    public static List<CustomTab> loadCustomTabs(Context context) {
        String json = LauncherPrefs.get(context).get(LauncherPrefs.DRAWER_CUSTOM_TABS);
        List<CustomTab> tabs = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject tab = array.optJSONObject(i);
                if (tab == null) continue;
                String id = tab.optString("id");
                String name = tab.optString("name");
                if (id.isEmpty() || name.isEmpty()) continue;
                Set<String> apps = new LinkedHashSet<>();
                JSONArray list = tab.optJSONArray("apps");
                if (list != null) {
                    for (int j = 0; j < list.length(); j++) {
                        String app = list.optString(j);
                        if (!app.isEmpty()) apps.add(app);
                    }
                }
                tabs.add(new CustomTab(id, name, apps));
            }
        } catch (JSONException e) {
            Log.w(TAG, "Unreadable drawer tabs, starting over", e);
        }
        return tabs;
    }

    private static void saveCustomTabs(Context context, List<CustomTab> tabs) {
        JSONArray array = new JSONArray();
        try {
            for (CustomTab tab : tabs) {
                array.put(new JSONObject()
                        .put("id", tab.id)
                        .put("name", tab.name)
                        .put("apps", new JSONArray(tab.apps)));
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        LauncherPrefs.get(context).put(LauncherPrefs.DRAWER_CUSTOM_TABS, array.toString());
    }

    @Nullable
    public static CustomTab findCustomTab(Context context, String id) {
        for (CustomTab tab : loadCustomTabs(context)) {
            if (tab.id.equals(id)) return tab;
        }
        return null;
    }

    /** Adds an empty tab at the end and returns it. */
    public static CustomTab addCustomTab(Context context, String name) {
        List<CustomTab> tabs = loadCustomTabs(context);
        Set<String> ids = new HashSet<>();
        for (CustomTab tab : tabs) ids.add(tab.id);
        long n = System.currentTimeMillis();
        while (ids.contains("c" + n)) n++;
        CustomTab tab = new CustomTab("c" + n, cleanName(name), new LinkedHashSet<>());
        tabs.add(tab);
        saveCustomTabs(context, tabs);
        return tab;
    }

    public static void renameCustomTab(Context context, String id, String name) {
        replace(context, id, tab -> new CustomTab(tab.id, cleanName(name), tab.apps));
    }

    public static void deleteCustomTab(Context context, String id) {
        replace(context, id, tab -> null);
    }

    /** Moves a tab one place earlier (-1) or later (+1). */
    public static void moveCustomTab(Context context, String id, int direction) {
        List<CustomTab> tabs = loadCustomTabs(context);
        for (int i = 0; i < tabs.size(); i++) {
            if (!tabs.get(i).id.equals(id)) continue;
            int to = i + direction;
            if (to < 0 || to >= tabs.size()) return;
            Collections.swap(tabs, i, to);
            saveCustomTabs(context, tabs);
            return;
        }
    }

    public static void setAppInTab(Context context, String id, ComponentName app, boolean inTab) {
        String key = app.flattenToShortString();
        replace(context, id, tab -> {
            Set<String> apps = new LinkedHashSet<>(tab.apps);
            if (inTab) apps.add(key); else apps.remove(key);
            return new CustomTab(tab.id, tab.name, apps);
        });
    }

    private interface Change {
        @Nullable CustomTab apply(CustomTab tab);
    }

    private static void replace(Context context, String id, Change change) {
        List<CustomTab> tabs = loadCustomTabs(context);
        for (int i = 0; i < tabs.size(); i++) {
            if (!tabs.get(i).id.equals(id)) continue;
            CustomTab changed = change.apply(tabs.get(i));
            if (changed == null) tabs.remove(i); else tabs.set(i, changed);
            saveCustomTabs(context, tabs);
            return;
        }
    }

    private static String cleanName(String name) {
        String clean = name.trim().replaceAll("\\s+", " ");
        if (clean.isEmpty()) clean = "Tab";
        return clean.length() > MAX_NAME_LENGTH ? clean.substring(0, MAX_NAME_LENGTH) : clean;
    }

    /**
     * The tabs in order: All, the user's tabs (kept when empty, so a new one can be found), then
     * one per app category that has apps, "Others" last.
     */
    @WorkerThread
    public static List<Tab> buildTabs(Context context, AppInfo[] apps, boolean withCategories) {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(new Tab(ALL, context.getString(R.string.drawer_tab_all), null, false));
        for (CustomTab tab : loadCustomTabs(context)) {
            tabs.add(new Tab(tab.id, tab.name, tab.apps, true));
        }
        if (!withCategories) return tabs;

        String others = context.getString(R.string.others_category_label);
        Tab othersTab = null;
        Map<String, List<AppInfo>> categories =
                AppsListUtils.categorizeAppsWithSystemAndGoogle(context, Arrays.asList(apps));
        for (Map.Entry<String, List<AppInfo>> entry : categories.entrySet()) {
            Set<String> members = new HashSet<>();
            for (AppInfo app : entry.getValue()) {
                ComponentName component = app.getTargetComponent();
                if (component != null) members.add(component.flattenToShortString());
            }
            if (members.isEmpty()) continue;
            Tab tab = new Tab(CATEGORY_PREFIX + entry.getKey(), entry.getKey(), members, false);
            if (others.equals(entry.getKey())) othersTab = tab; else tabs.add(tab);
        }
        if (othersTab != null) tabs.add(othersTab);
        return tabs;
    }
}
