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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.allapps.AllAppsGridAdapter;
import com.android.launcher3.allapps.AppDrawerStyle;
import com.android.launcher3.allapps.BaseAllAppsAdapter;
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.views.ActivityContext;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Draws the rows {@link PixelSearchAlgorithm} adds under the app results, and makes Enter in the
 * search field open the top result (an app, else the first row) before falling back to web search.
 */
public class PixelSearchAdapterProvider extends DefaultSearchAdapterProvider {

    private static final String TAG = "PixelSearch";

    public static final int VIEW_TYPE_SEARCH_ROW = 1 << BaseAllAppsAdapter.NEXT_ID;

    private static final ExecutorService ICON_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final int REQUEST_CODE_NO_OP = 0x5a17;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    public PixelSearchAdapterProvider(ActivityContext launcher) {
        super(launcher);
    }

    /** What a row does when tapped. */
    public interface RowAction {
        boolean launch(View view, ActivityContext launcher);
    }

    /** Loads a row's icon; called off the main thread, may return null. */
    public interface IconSource {
        Drawable load(Context context);
    }

    /** One non-app result in the search list, or a section header. */
    public static class SearchRow extends AdapterItem {
        public final String id;
        public final boolean header;
        public final CharSequence title;
        public final CharSequence subtitle;
        public final IconSource icon;
        public final RowAction action;
        public final boolean roundIcon;
        /** Whether Enter may open this row when it is the top result. */
        public final boolean enterLaunches;
        /**
         * Rows with the same key are the same result from different sources (a Settings app
         * shortcut and the Settings entry for one page); only one is shown. Defaults to the id.
         */
        public String dedupeKey;

        public SearchRow(String id, boolean header, CharSequence title, CharSequence subtitle,
                IconSource icon, RowAction action, boolean roundIcon, boolean enterLaunches) {
            super(VIEW_TYPE_SEARCH_ROW);
            this.id = id;
            this.header = header;
            this.title = title;
            this.subtitle = subtitle;
            this.icon = icon;
            this.action = action;
            this.roundIcon = roundIcon;
            this.enterLaunches = enterLaunches;
        }

        public static SearchRow header(String id, CharSequence title) {
            return new SearchRow(id, true, title, null, null, null, false, false);
        }

        public static SearchRow item(String id, CharSequence title, CharSequence subtitle,
                IconSource icon, RowAction action) {
            return new SearchRow(id, false, title, subtitle, icon, action, false, true);
        }

        /** Sets the key to title plus target package, so sources agree on one result. */
        public SearchRow dedupeBy(CharSequence title, String targetPackage) {
            if (targetPackage != null && !TextUtils.isEmpty(title)) {
                dedupeKey = SettingsSearchIndex.normalize(title) + "|" + targetPackage;
            }
            return this;
        }

        public String key() {
            return dedupeKey != null ? dedupeKey : id;
        }

        @Override
        public boolean isSameAs(AdapterItem other) {
            return other instanceof SearchRow row && row.header == header
                    && Objects.equals(row.id, id);
        }

        @Override
        public boolean isContentSame(AdapterItem other) {
            return other instanceof SearchRow row && TextUtils.equals(row.title, title)
                    && TextUtils.equals(row.subtitle, subtitle);
        }

        @Override
        public boolean isCountedForAccessibility() {
            return !header;
        }
    }

    /** Starts {@code intent} from the launcher activity; Settings search results need a caller. */
    public static boolean startForResult(View view, ActivityContext launcher, Intent intent) {
        try {
            if (launcher instanceof Activity activity) {
                activity.startActivityForResult(intent, REQUEST_CODE_NO_OP);
                return true;
            }
            return launcher.startActivitySafely(view, intent, null) != null;
        } catch (RuntimeException e) {
            Log.w(TAG, "start failed: " + intent, e);
            return false;
        }
    }

    /** Starts {@code intent} with the launcher's usual launch animation. */
    public static boolean start(View view, ActivityContext launcher, Intent intent) {
        try {
            return launcher.startActivitySafely(view, intent, null) != null;
        } catch (RuntimeException e) {
            Log.w(TAG, "start failed: " + intent, e);
            return false;
        }
    }

    @Override
    public boolean isViewSupported(int viewType) {
        return viewType == VIEW_TYPE_SEARCH_ROW || super.isViewSupported(viewType);
    }

    @Override
    public int getItemsPerRow(int viewType, int appsPerRow) {
        return viewType == VIEW_TYPE_SEARCH_ROW ? 1 : super.getItemsPerRow(viewType, appsPerRow);
    }

    @Override
    public AllAppsGridAdapter.ViewHolder onCreateViewHolder(LayoutInflater layoutInflater,
            ViewGroup parent, int viewType) {
        if (viewType != VIEW_TYPE_SEARCH_ROW) {
            return super.onCreateViewHolder(layoutInflater, parent, viewType);
        }
        return new AllAppsGridAdapter.ViewHolder(new RowView(parent.getContext()));
    }

    @Override
    public void onBindView(AllAppsGridAdapter.ViewHolder holder, int position) {
        super.onBindView(holder, position);
        if (!(holder.itemView instanceof RowView view)) return;
        AdapterItem item = itemAt(position);
        if (!(item instanceof SearchRow row)) return;
        view.bind(row, mMainHandler);
        if (row.header || row.action == null) {
            view.setOnClickListener(null);
            view.setClickable(false);
        } else {
            view.setOnClickListener(v -> launchRow(v, row));
        }
    }

    @Override
    public boolean performGoogleSearch(View view, String query) {
        List<AdapterItem> items = searchItems();
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                AdapterItem item = items.get(i);
                if (item instanceof SearchRow row) {
                    if (row.header) continue;
                    if (!row.enterLaunches || row.action == null) break;
                    if (launchRow(viewAt(i, view), row)) return true;
                    break;
                }
                AppInfo app = item.itemInfo;
                if (item.viewType == BaseAllAppsAdapter.VIEW_TYPE_ICON && app != null
                        && app.getIntent() != null) {
                    return mLauncher.startActivitySafely(viewAt(i, view), app.getIntent(), app)
                            != null;
                }
                break;
            }
        }
        return super.performGoogleSearch(view, query);
    }

    private boolean launchRow(View view, SearchRow row) {
        try {
            return row.action.launch(view, mLauncher);
        } catch (RuntimeException e) {
            Log.w(TAG, "launch failed for " + row.id, e);
            return false;
        }
    }

    private List<AdapterItem> searchItems() {
        ActivityAllAppsContainerView<?> appsView = mLauncher.getAppsView();
        if (appsView == null || appsView.getSearchResultList() == null) return null;
        return appsView.getSearchResultList().getAdapterItems();
    }

    private AdapterItem itemAt(int position) {
        List<AdapterItem> items = searchItems();
        return items != null && position >= 0 && position < items.size()
                ? items.get(position) : null;
    }

    private View viewAt(int position, View fallback) {
        ActivityAllAppsContainerView<?> appsView = mLauncher.getAppsView();
        if (appsView != null && appsView.getSearchRecyclerView() != null) {
            var holder = appsView.getSearchRecyclerView().findViewHolderForAdapterPosition(position);
            if (holder != null) return holder.itemView;
        }
        return fallback;
    }

    /** Icon, title and subtitle; a header is just the title. Built in code, no layout file. */
    private static final class RowView extends LinearLayout {
        private static final ViewOutlineProvider OVAL = new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        };

        private final ImageView mIcon;
        private final TextView mTitle;
        private final TextView mSubtitle;
        private final Drawable mRipple;
        private Object mBound;

        RowView(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            int color = AppDrawerStyle.getContentColor(context);

            mIcon = new ImageView(context);
            int size = dp(36);
            LayoutParams iconLp = new LayoutParams(size, size);
            iconLp.setMarginEnd(dp(16));
            mIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            addView(mIcon, iconLp);

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(VERTICAL);
            mTitle = new TextView(context);
            mTitle.setTextColor(color);
            mTitle.setSingleLine(true);
            mTitle.setEllipsize(TextUtils.TruncateAt.END);
            mSubtitle = new TextView(context);
            mSubtitle.setTextColor(color);
            mSubtitle.setAlpha(0.7f);
            mSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            mSubtitle.setSingleLine(true);
            mSubtitle.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(mTitle);
            texts.addView(mSubtitle);
            addView(texts, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TypedArray a = context.obtainStyledAttributes(
                    new int[] {android.R.attr.selectableItemBackground});
            mRipple = a.getDrawable(0);
            a.recycle();
        }

        void bind(SearchRow row, Handler mainHandler) {
            mBound = row;
            mTitle.setText(row.title);
            mIcon.setImageDrawable(null);
            if (row.header) {
                setPadding(dp(24), dp(18), dp(24), dp(4));
                setMinimumHeight(0);
                setBackground(null);
                mTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                mTitle.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                mTitle.setAlpha(0.8f);
                mSubtitle.setVisibility(GONE);
                mIcon.setVisibility(GONE);
                return;
            }
            setPadding(dp(24), dp(10), dp(24), dp(10));
            setMinimumHeight(dp(60));
            setBackground(mRipple);
            mTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            mTitle.setTypeface(Typeface.DEFAULT);
            mTitle.setAlpha(1f);
            mSubtitle.setText(row.subtitle);
            mSubtitle.setVisibility(TextUtils.isEmpty(row.subtitle) ? GONE : VISIBLE);
            mIcon.setVisibility(VISIBLE);
            mIcon.setClipToOutline(row.roundIcon);
            mIcon.setOutlineProvider(row.roundIcon ? OVAL : ViewOutlineProvider.BACKGROUND);
            if (row.icon == null) return;
            Context context = getContext();
            ICON_EXECUTOR.execute(() -> {
                Drawable d = null;
                try {
                    d = row.icon.load(context);
                } catch (Exception e) {
                    Log.w(TAG, "icon failed for " + row.id, e);
                }
                Drawable result = d;
                mainHandler.post(() -> {
                    if (mBound == row) mIcon.setImageDrawable(result);
                });
            });
        }

        private int dp(int value) {
            return Math.round(value * getResources().getDisplayMetrics().density);
        }
    }
}
