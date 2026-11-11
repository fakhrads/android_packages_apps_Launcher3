/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.allapps;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.LauncherPrefChangeListener;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.anim.AlphaUpdateListener;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.settings.compose.ZeroSettingsActivity;
import com.android.launcher3.views.ActivityContext;

import java.util.Collections;
import java.util.List;

/**
 * The drawer tabs (see {@link DrawerTabs}) as a scrolling row of pills under the search bar.
 * Picking one filters the A-Z list; a long press opens the tab settings.
 */
public class DrawerTabsRowView extends HorizontalScrollView implements FloatingHeaderRow,
        AllAppsStore.OnUpdateListener, LauncherPrefChangeListener {

    private final ActivityContext mActivityContext;
    private final LinearLayout mPills;
    private final int mPillHeight;
    private final int mTopMargin;
    private final int mPillPadding;
    private final int mPillGap;

    private List<DrawerTabs.Tab> mTabs = Collections.emptyList();
    private String mSelectedId = DrawerTabs.ALL;
    // Drops a slow categorization that a newer one already replaced
    private int mGeneration;
    @Nullable
    private FloatingHeaderView mParent;

    public DrawerTabsRowView(@NonNull Context context) {
        this(context, null);
    }

    public DrawerTabsRowView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mActivityContext = ActivityContext.lookupContext(context);
        mPillHeight = getResources().getDimensionPixelSize(R.dimen.all_apps_header_pill_height);
        mTopMargin = getResources().getDimensionPixelSize(R.dimen.all_apps_tabs_margin_top);
        mPillPadding = dp(16);
        mPillGap = getResources().getDimensionPixelSize(
                R.dimen.all_apps_tabs_button_horizontal_padding) * 2;
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setClipToPadding(false);
        setPadding(0, mTopMargin, 0, 0);
        mPills = new LinearLayout(context);
        mPills.setOrientation(LinearLayout.HORIZONTAL);
        addView(mPills, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, mPillHeight));
        setVisibility(GONE);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ActivityAllAppsContainerView<?> appsView = mActivityContext.getAppsView();
        if (appsView == null) return;
        appsView.getAppsStore().addUpdateListener(this);
        LauncherPrefs.get(getContext()).addListener(this, LauncherPrefs.DRAWER_TABS,
                LauncherPrefs.DRAWER_TABS_AUTO, LauncherPrefs.DRAWER_CUSTOM_TABS);
        reload();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        ActivityAllAppsContainerView<?> appsView = mActivityContext.getAppsView();
        if (appsView == null) return;
        appsView.getAppsStore().removeUpdateListener(this);
        LauncherPrefs.get(getContext()).removeListener(this, LauncherPrefs.DRAWER_TABS,
                LauncherPrefs.DRAWER_TABS_AUTO, LauncherPrefs.DRAWER_CUSTOM_TABS);
    }

    @Override
    public void onAppsUpdated() {
        reload();
    }

    @Override
    public void onPrefChanged(String key) {
        // Called for every launcher pref
        if (LauncherPrefs.DRAWER_TABS.getSharedPrefKey().equals(key)
                || LauncherPrefs.DRAWER_TABS_AUTO.getSharedPrefKey().equals(key)
                || LauncherPrefs.DRAWER_CUSTOM_TABS.getSharedPrefKey().equals(key)) {
            reload();
        }
    }

    private void reload() {
        int generation = ++mGeneration;
        LauncherPrefs prefs = LauncherPrefs.get(getContext());
        if (!prefs.get(LauncherPrefs.DRAWER_TABS)) {
            applyTabs(Collections.emptyList());
            return;
        }
        ActivityAllAppsContainerView<?> appsView = mActivityContext.getAppsView();
        if (appsView == null) return;
        boolean withCategories = prefs.get(LauncherPrefs.DRAWER_TABS_AUTO);
        AppInfo[] apps = appsView.getAppsStore().getApps();
        Context context = getContext().getApplicationContext();
        // Categorizing asks the package manager about every app: keep it off the main thread
        UI_HELPER_EXECUTOR.execute(() -> {
            List<DrawerTabs.Tab> tabs = DrawerTabs.buildTabs(context, apps, withCategories);
            MAIN_EXECUTOR.execute(() -> {
                if (generation == mGeneration) applyTabs(tabs);
            });
        });
    }

    private void applyTabs(List<DrawerTabs.Tab> tabs) {
        mTabs = tabs;
        if (findTab(mSelectedId) == null) {
            mSelectedId = DrawerTabs.ALL;
        }
        mPills.removeAllViews();
        for (DrawerTabs.Tab tab : tabs) {
            mPills.addView(createPill(tab));
        }
        // Only "All" left: nothing to pick from
        boolean show = tabs.size() > 1;
        if (show != (getVisibility() != GONE)) {
            setVisibility(show ? VISIBLE : GONE);
            if (mParent != null) {
                mParent.onHeightUpdated();
            }
        }
        applyFilter(false);
    }

    private TextView createPill(DrawerTabs.Tab tab) {
        TextView pill = new TextView(getContext());
        pill.setText(tab.title);
        pill.setSingleLine(true);
        pill.setGravity(Gravity.CENTER);
        pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        pill.setTextColor(getContext().getColorStateList(R.color.all_apps_tab_text));
        pill.setBackgroundResource(R.drawable.all_apps_tabs_background);
        pill.setPadding(mPillPadding, 0, mPillPadding, 0);
        pill.setSelected(tab.id.equals(mSelectedId));
        pill.setOnClickListener(v -> select(tab.id));
        pill.setOnLongClickListener(v -> {
            openSettings();
            return true;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        if (mPills.getChildCount() > 0) {
            lp.setMarginStart(mPillGap);
        }
        pill.setLayoutParams(lp);
        return pill;
    }

    private void select(String id) {
        if (id.equals(mSelectedId)) return;
        mSelectedId = id;
        for (int i = 0; i < mPills.getChildCount() && i < mTabs.size(); i++) {
            mPills.getChildAt(i).setSelected(mTabs.get(i).id.equals(id));
        }
        applyFilter(true);
    }

    private void applyFilter(boolean scrollToTop) {
        DrawerTabs.Tab tab = findTab(mSelectedId);
        ActivityAllAppsContainerView<?> appsView = mActivityContext.getAppsView();
        if (appsView == null) return;
        appsView.setDrawerTabFilter(tab == null || tab.apps == null ? null : tab::matches);
        if (scrollToTop && appsView.getActiveRecyclerView() != null) {
            appsView.getActiveRecyclerView().scrollToTop();
        }
    }

    @Nullable
    private DrawerTabs.Tab findTab(String id) {
        for (DrawerTabs.Tab tab : mTabs) {
            if (tab.id.equals(id)) return tab;
        }
        return null;
    }

    private void openSettings() {
        Intent intent = new Intent(getContext(), ZeroSettingsActivity.class)
                .putExtra("root_key", "drawer_tabs")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(getExpectedHeight(), MeasureSpec.EXACTLY));
    }

    @Override
    public void setup(FloatingHeaderView parent, FloatingHeaderRow[] allRows, boolean tabsHidden) {
        mParent = parent;
        // Reach the screen edges, so tabs scroll out of view there instead of being cut at the
        // header padding; the first tab still lines up with the apps
        int left = parent.getPaddingLeft();
        int right = parent.getPaddingRight();
        if (getLayoutParams() instanceof MarginLayoutParams lp
                && (lp.leftMargin != -left || lp.rightMargin != -right)) {
            lp.leftMargin = -left;
            lp.rightMargin = -right;
            setLayoutParams(lp);
            setPadding(left, mTopMargin, right, 0);
        }
    }

    @Override
    public int getExpectedHeight() {
        return getVisibility() == GONE ? 0 : mPillHeight + mTopMargin;
    }

    @Override
    public boolean shouldDraw() {
        return getVisibility() != GONE;
    }

    @Override
    public boolean hasVisibleContent() {
        // Not an app: the IME launch target stays the first app
        return false;
    }

    @Override
    public void setVerticalScroll(int scroll, boolean isScrolledOut) {
        if (!isScrolledOut) {
            setTranslationY(scroll);
        }
        setAlpha(isScrolledOut ? 0 : 1);
        if (getVisibility() != GONE) {
            AlphaUpdateListener.updateVisibility(this);
        }
    }

    @Override
    public Class<DrawerTabsRowView> getTypeClass() {
        return DrawerTabsRowView.class;
    }
}
