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

import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_EMPTY_SEARCH;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.app.search.Query;
import android.app.search.SearchAction;
import android.app.search.SearchContext;
import android.app.search.SearchSession;
import android.app.search.SearchTarget;
import android.app.search.SearchUiManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.allapps.search.PixelSearchAdapterProvider.SearchRow;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.search.SearchCallback;
import com.android.launcher3.util.LooperExecutor;
import com.android.launcher3.views.ActivityContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * App drawer search in the spirit of the Pixel Launcher's. Apps come from the local title match
 * right away; the other sources run in parallel and their rows are added as they arrive:
 * the top app's shortcuts and matching shortcuts, Settings entries, contacts, and whatever the
 * platform search service (Android System Intelligence) returns. Every source fails closed to no
 * rows, so the worst case is the plain local search.
 */
public class PixelSearchAlgorithm extends DefaultAppSearchAlgorithm {

    private static final String TAG = "PixelSearch";

    // com.android.app.search.ResultType bits, minus the ones there is no view for
    private static final int RESULT_APPLICATION = 1;
    private static final int RESULT_PEOPLE = 1 << 2;
    private static final int RESULT_SLICE = 1 << 6;
    private static final int RESULT_WIDGETS = 1 << 7;
    private static final int RESULT_TYPES = ((1 << 18) - 1) & ~(RESULT_SLICE | RESULT_WIDGETS);
    private static final int SESSION_TIMEOUT_MS = 1500;
    private static final int MAX_SERVICE_ROWS = 20;

    // com.android.app.search.LayoutType values
    private static final String LAYOUT_TEXT_HEADER = "header";
    private static final String LAYOUT_SECTION_HEADER = "section_header";
    private static final String LAYOUT_DIVIDER = "divider";
    private static final String LAYOUT_EMPTY_DIVIDER = "empty_divider";

    // Sources, in display order after the apps
    private static final int SRC_SHORTCUTS = 0;
    private static final int SRC_SETTINGS = 1;
    private static final int SRC_CONTACTS = 2;
    private static final int SRC_SERVICE = 3;
    private static final int SRC_COUNT = 4;

    private static final ExecutorService WORKER = Executors.newFixedThreadPool(2);
    private static final ExecutorService INDEXER = Executors.newSingleThreadExecutor();

    private final Context mContext;
    private final LauncherAppState mAppState;
    private final Handler mUiHandler;
    private final boolean mAddNoResultsMessage;

    private SearchSession mSession;
    private boolean mSessionFailed;

    // State of the newest query; results of older ones are dropped
    private int mGeneration;
    private String mQuery;
    private SearchCallback<AdapterItem> mCallback;
    private ArrayList<AdapterItem> mLocal;
    @SuppressWarnings("unchecked")
    private final List<SearchRow>[] mRows = new List[SRC_COUNT];

    public PixelSearchAlgorithm(Context context, LooperExecutor uiExecutor,
            boolean addNoResultsMessage) {
        super(context, uiExecutor, addNoResultsMessage);
        mContext = context;
        mAppState = LauncherAppState.getInstance(context);
        mUiHandler = new Handler(uiExecutor.getLooper());
        mAddNoResultsMessage = addNoResultsMessage;
        // Warm the Settings index so the first query has it
        SettingsSearchIndex.get().ensureFresh(context, INDEXER, () -> {});
    }

    @Override
    public void doSearch(String query, SearchCallback<AdapterItem> callback) {
        final int generation = ++mGeneration;
        mQuery = query;
        mCallback = callback;
        mLocal = null;
        for (int i = 0; i < SRC_COUNT; i++) mRows[i] = null;

        mAppState.getModel().enqueueModelUpdateTask((taskController, dataModel, apps) -> {
            ArrayList<AdapterItem> local =
                    getTitleMatchResult(mAppState.getContext(), apps.data, query);
            mUiHandler.post(() -> {
                if (generation != mGeneration) return;
                mLocal = local;
                deliver();
                AppInfo top = !local.isEmpty() ? local.get(0).itemInfo : null;
                String topPackage = top != null && top.getTargetComponent() != null
                        ? top.getTargetComponent().getPackageName() : null;
                UserHandle topUser = top != null ? top.user : null;
                runSource(generation, SRC_SHORTCUTS,
                        () -> DeviceSearchSources.shortcuts(mContext, query, topPackage, topUser));
            });
        });
        runSettings(generation, query);
        runSource(generation, SRC_CONTACTS, () -> DeviceSearchSources.contacts(mContext, query));
        queryService(query, generation);
    }

    @Override
    public void cancel(boolean interruptActiveRequests) {
        if (interruptActiveRequests) {
            mGeneration++;
            mUiHandler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    public void destroy() {
        mGeneration++;
        if (mSession != null) {
            try {
                mSession.close();
            } catch (RuntimeException e) {
                Log.w(TAG, "destroy failed", e);
            }
            mSession = null;
        }
    }

    private interface RowsTask {
        List<SearchRow> run();
    }

    private void runSource(int generation, int source, RowsTask task) {
        WORKER.execute(() -> {
            List<SearchRow> rows;
            try {
                rows = task.run();
            } catch (RuntimeException e) {
                Log.w(TAG, "source " + source + " failed", e);
                rows = new ArrayList<>();
            }
            List<SearchRow> result = rows;
            mUiHandler.post(() -> {
                if (generation != mGeneration) return;
                mRows[source] = result;
                deliver();
            });
        });
    }

    private void runSettings(int generation, String query) {
        SettingsSearchIndex index = SettingsSearchIndex.get();
        boolean ready = index.ensureFresh(mContext, INDEXER, () -> mUiHandler.post(() -> {
            // The index finished while this query was showing: add its rows now
            if (generation == mGeneration) {
                runSource(generation, SRC_SETTINGS, () -> index.search(mContext, query));
            }
        }));
        if (ready) runSource(generation, SRC_SETTINGS, () -> index.search(mContext, query));
    }

    private void queryService(String query, int generation) {
        SearchSession session = getSession();
        if (session == null) return;
        try {
            session.query(new Query(query, System.currentTimeMillis()), mUiHandler::post,
                    targets -> {
                        logTargets(query, targets);
                        if (generation != mGeneration) return;
                        mRows[SRC_SERVICE] = serviceRows(targets);
                        deliver();
                    });
        } catch (RuntimeException e) {
            Log.w(TAG, "query failed, no service results", e);
            mSessionFailed = true;
            mSession = null;
        }
    }

    private SearchSession getSession() {
        if (mSession != null || mSessionFailed) return mSession;
        try {
            SearchUiManager manager = mContext.getSystemService(SearchUiManager.class);
            if (manager != null) {
                mSession = manager.createSearchSession(
                        new SearchContext(RESULT_TYPES, SESSION_TIMEOUT_MS, new Bundle()));
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "no search session, no service results", e);
        }
        if (mSession == null) mSessionFailed = true;
        Log.d(TAG, "search session " + (mSession != null ? "created" : "unavailable"));
        return mSession;
    }

    // Which source keeps a result found by several: Settings, shortcuts, contacts, the service
    private static final int[] DEDUPE_PRIORITY = {
            SRC_SETTINGS, SRC_SHORTCUTS, SRC_CONTACTS, SRC_SERVICE};

    /** Apps, then each source under its header, without repeats; waits for the apps. */
    private void deliver() {
        if (mLocal == null || mCallback == null) return;
        ArrayList<AdapterItem> result = new ArrayList<>(mLocal);
        // Pick the surviving rows in priority order, then show them in display order
        Set<String> seen = new HashSet<>();
        Set<String> ownTitles = new HashSet<>();
        Set<SearchRow> keep = new HashSet<>();
        for (int source : DEDUPE_PRIORITY) {
            List<SearchRow> rows = mRows[source];
            if (rows == null) continue;
            for (SearchRow row : rows) {
                if (row.header) continue;
                String title = SettingsSearchIndex.normalize(row.title);
                // The service may name itself as the package; its copy of a result already
                // shown by an own source is dropped by title
                if (source == SRC_SERVICE && ownTitles.contains(title)) continue;
                if (seen.add(row.key())) {
                    keep.add(row);
                    if (source != SRC_SERVICE) ownTitles.add(title);
                }
            }
        }
        addSection(result, keep, mRows[SRC_SHORTCUTS], "Shortcuts");
        addSection(result, keep, mRows[SRC_SETTINGS], settingsLabel());
        addSection(result, keep, mRows[SRC_CONTACTS], "Contacts");
        addSection(result, keep, mRows[SRC_SERVICE], null);
        if (mAddNoResultsMessage && result.isEmpty()) {
            AdapterItem empty = new AdapterItem(VIEW_TYPE_EMPTY_SEARCH);
            AppInfo placeHolder = new AppInfo();
            placeHolder.title = mQuery;
            empty.itemInfo = placeHolder;
            result.add(empty);
        }
        mCallback.onSearchResult(mQuery, result);
    }

    private static void addSection(ArrayList<AdapterItem> out, Set<SearchRow> keep,
            List<SearchRow> rows, CharSequence header) {
        if (rows == null || rows.isEmpty()) return;
        List<SearchRow> fresh = new ArrayList<>();
        for (SearchRow row : rows) {
            if (row.header || keep.contains(row)) fresh.add(row);
        }
        dropEmptyHeaders(fresh);
        if (fresh.isEmpty()) return;
        if (header != null) out.add(SearchRow.header("hdr:" + header, header));
        out.addAll(fresh);
    }

    /** Removes headers that are not followed by an item. */
    private static void dropEmptyHeaders(List<SearchRow> rows) {
        for (int i = rows.size() - 1; i >= 0; i--) {
            boolean nextIsItem = i + 1 < rows.size() && !rows.get(i + 1).header;
            if (rows.get(i).header && !nextIsItem) rows.remove(i);
        }
    }

    private CharSequence mSettingsLabel;

    private CharSequence settingsLabel() {
        if (mSettingsLabel == null) {
            try {
                mSettingsLabel = mContext.getPackageManager().getApplicationLabel(mContext
                        .getPackageManager().getApplicationInfo("com.android.settings", 0));
            } catch (Exception e) {
                mSettingsLabel = "Settings";
            }
        }
        return mSettingsLabel;
    }

    // ---- Platform search service ----

    /**
     * Rows for everything but plain apps (the local match has those). A child target is shown
     * on its own only when its parent is not, so app shortcuts under a skipped app still appear.
     */
    private static List<SearchRow> serviceRows(List<SearchTarget> targets) {
        List<SearchRow> rows = new ArrayList<>();
        if (targets == null) return rows;
        Set<String> shown = new HashSet<>();
        for (SearchTarget t : targets) {
            if (rows.size() >= MAX_SERVICE_ROWS) break;
            if (t == null || t.isHidden()) continue;
            if (t.getParentId() != null && shown.contains(t.getParentId())) continue;
            String layout = t.getLayoutType();
            if (LAYOUT_DIVIDER.equals(layout) || LAYOUT_EMPTY_DIVIDER.equals(layout)) continue;
            SearchAction action = t.getSearchAction();
            CharSequence title = action != null ? action.getTitle() : null;
            if (LAYOUT_TEXT_HEADER.equals(layout) || LAYOUT_SECTION_HEADER.equals(layout)) {
                if (!TextUtils.isEmpty(title)) {
                    rows.add(SearchRow.header("asi:" + t.getId(), title));
                }
                continue;
            }
            ShortcutInfo shortcut = t.getShortcutInfo();
            if (shortcut != null) {
                CharSequence label = TextUtils.isEmpty(title) ? shortcut.getShortLabel() : title;
                rows.add(serviceRow(t, DeviceSearchSources.shortcutId(shortcut.getPackage(),
                        shortcut.getId()), label, action));
                shown.add(t.getId());
                continue;
            }
            if ((t.getResultType() & RESULT_APPLICATION) != 0) continue;
            if (action == null || TextUtils.isEmpty(title)) continue;
            if (action.getIntent() == null && action.getPendingIntent() == null) continue;
            rows.add(serviceRow(t, "asi:" + t.getId(), title, action));
            shown.add(t.getId());
        }
        dropEmptyHeaders(rows);
        return rows;
    }

    private static SearchRow serviceRow(SearchTarget t, String id, CharSequence title,
            SearchAction action) {
        CharSequence subtitle = action != null ? action.getSubtitle() : null;
        boolean round = (t.getResultType() & RESULT_PEOPLE) != 0;
        ShortcutInfo shortcut = t.getShortcutInfo();
        String pkg = shortcut != null ? shortcut.getPackage() : t.getPackageName();
        return new SearchRow(id, false, title, subtitle, c -> serviceIcon(c, t),
                (view, launcher) -> launchTarget(view, launcher, t), round, true)
                .dedupeBy(title, pkg);
    }

    private static Drawable serviceIcon(Context context, SearchTarget t) {
        SearchAction action = t.getSearchAction();
        Icon icon = action != null ? action.getIcon() : null;
        Drawable d = icon != null ? icon.loadDrawable(context) : null;
        ShortcutInfo shortcut = t.getShortcutInfo();
        if (d == null && shortcut != null) {
            d = context.getSystemService(LauncherApps.class).getShortcutIconDrawable(shortcut,
                    context.getResources().getDisplayMetrics().densityDpi);
        }
        if (d == null && !TextUtils.isEmpty(t.getPackageName())) {
            d = SettingsSearchIndex.appIcon(context, t.getPackageName());
        }
        return d;
    }

    private static boolean launchTarget(View view, ActivityContext launcher, SearchTarget t) {
        Context context = view.getContext();
        try {
            ShortcutInfo shortcut = t.getShortcutInfo();
            if (shortcut != null) {
                context.getSystemService(LauncherApps.class).startShortcut(shortcut, null, null);
                return true;
            }
            SearchAction action = t.getSearchAction();
            if (action == null) return false;
            PendingIntent pi = action.getPendingIntent();
            if (pi != null) {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE);
                pi.send(context, 0, null, null, null, null, options.toBundle());
                return true;
            }
            if (action.getIntent() != null) {
                return PixelSearchAdapterProvider.start(view, launcher,
                        new Intent(action.getIntent()));
            }
        } catch (Exception e) {
            Log.w(TAG, "launch failed for " + t.getId(), e);
        }
        return false;
    }

    /** Off unless `setprop log.tag.PixelSearch DEBUG`: queries and results are personal. */
    private static void logTargets(String query, List<SearchTarget> targets) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) return;
        Log.d(TAG, "'" + query + "': " + (targets == null ? "null" : targets.size() + " targets"));
        if (targets == null) return;
        for (SearchTarget t : targets) {
            SearchAction a = t.getSearchAction();
            Log.d(TAG, "  type=0x" + Integer.toHexString(t.getResultType())
                    + " layout=" + t.getLayoutType() + " id=" + t.getId()
                    + " parent=" + t.getParentId() + " hidden=" + t.isHidden()
                    + " pkg=" + t.getPackageName()
                    + " shortcut=" + (t.getShortcutInfo() != null)
                    + " title=" + (a != null ? a.getTitle() : null)
                    + " intent=" + (a != null && a.getIntent() != null)
                    + " pi=" + (a != null && a.getPendingIntent() != null));
        }
    }
}
