/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.allapps;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableContainer;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

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
 * Picking one, or swiping the A-Z list sideways, filters the list; a long press opens the tab
 * settings.
 */
public class DrawerTabsRowView extends HorizontalScrollView implements FloatingHeaderRow,
        AllAppsStore.OnUpdateListener, LauncherPrefChangeListener {

    private final ActivityContext mActivityContext;
    private final LinearLayout mPills;
    private final int mPillHeight;
    private final int mTopMargin;
    private final int mPillPadding;
    private final int mPillGap;

    private static final int SWITCH_DURATION_MS = 120;

    private List<DrawerTabs.Tab> mTabs = Collections.emptyList();
    private String mSelectedId = DrawerTabs.ALL;
    // Drops a slow categorization that a newer one already replaced
    private int mGeneration;
    @Nullable
    private FloatingHeaderView mParent;
    private final SwipeListener mSwipeListener = new SwipeListener();
    // The A-Z list a sideways swipe switches tabs on
    @Nullable
    private RecyclerView mSwipeRecyclerView;

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
        setSwipeRecyclerView(null);
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
        updateSwipeRecyclerView();
    }

    private TextView createPill(DrawerTabs.Tab tab) {
        TextView pill = new TextView(getContext());
        pill.setText(tab.title);
        pill.setSingleLine(true);
        pill.setGravity(Gravity.CENTER);
        pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        pill.setTextColor(getContext().getColorStateList(R.color.all_apps_tab_text));
        pill.setBackgroundResource(R.drawable.all_apps_tabs_background);
        Drawable background = pill.getBackground();
        if (background instanceof DrawableContainer container) {
            // The highlight fades from the old tab to the new one (own state: mutate first)
            background.mutate();
            container.setEnterFadeDuration(SWITCH_DURATION_MS);
            container.setExitFadeDuration(SWITCH_DURATION_MS);
        }
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
        selectIndex(indexOf(id));
    }

    private void selectIndex(int index) {
        if (index < 0 || index >= mTabs.size()) return;
        int from = indexOf(mSelectedId);
        String id = mTabs.get(index).id;
        if (id.equals(mSelectedId)) return;
        mSelectedId = id;
        for (int i = 0; i < mPills.getChildCount() && i < mTabs.size(); i++) {
            mPills.getChildAt(i).setSelected(i == index);
        }
        revealPill(index);
        applyFilter(true);
        slideList(index > from ? 1 : -1);
    }

    private int indexOf(String id) {
        for (int i = 0; i < mTabs.size(); i++) {
            if (mTabs.get(i).id.equals(id)) return i;
        }
        return -1;
    }

    /** Scrolls the row so the picked tab sits in the middle, as far as the row allows. */
    private void revealPill(int index) {
        if (index >= mPills.getChildCount()) return;
        View pill = mPills.getChildAt(index);
        int center = getPaddingLeft() + pill.getLeft() + pill.getWidth() / 2;
        smoothScrollTo(Math.max(0, center - getWidth() / 2), 0);
    }

    /** The new list comes in from the side of the new tab. Translation only: the drawer
     * transition owns the alpha of the list. */
    private void slideList(int direction) {
        ActivityAllAppsContainerView<?> appsView = mActivityContext.getAppsView();
        View list = appsView == null ? null : appsView.getActiveRecyclerView();
        if (list == null) return;
        int sign = getLayoutDirection() == LAYOUT_DIRECTION_RTL ? -direction : direction;
        list.animate().cancel();
        list.setTranslationX(sign * dp(32));
        list.animate().translationX(0).setDuration(SWITCH_DURATION_MS * 2)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    /** Swipes switch tabs on the A-Z list, but not where a sideways swipe already means
     * something: personal and work pages, or the paged drawer style. */
    private void updateSwipeRecyclerView() {
        ActivityAllAppsContainerView<?> appsView = mActivityContext.getAppsView();
        if (appsView == null || appsView.isSearching()) return;
        boolean canSwipe = getVisibility() != GONE && mParent != null && !mParent.usingTabs()
                && !AppDrawerStyle.VERTICAL_PAGED.equals(AppDrawerStyle.get(getContext()));
        setSwipeRecyclerView(canSwipe ? appsView.getActiveRecyclerView() : null);
    }

    private void setSwipeRecyclerView(@Nullable RecyclerView recyclerView) {
        if (recyclerView == mSwipeRecyclerView) return;
        if (mSwipeRecyclerView != null) {
            mSwipeRecyclerView.removeOnItemTouchListener(mSwipeListener);
        }
        mSwipeRecyclerView = recyclerView;
        if (recyclerView != null) {
            recyclerView.addOnItemTouchListener(mSwipeListener);
        }
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
        // Setup follows every rebind of the lists, which can swap the A-Z list
        updateSwipeRecyclerView();
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

    /**
     * Watches touches on the A-Z list and takes over a clearly sideways one, which cancels the
     * touch on the icon under it; a fling or a drag past a quarter of the list switches tabs.
     */
    private final class SwipeListener implements RecyclerView.OnItemTouchListener {

        private final int mTouchSlop =
                ViewConfiguration.get(getContext()).getScaledTouchSlop();
        private final int mMinFlingVelocity =
                ViewConfiguration.get(getContext()).getScaledMinimumFlingVelocity() * 4;
        private float mDownX;
        private float mDownY;
        // A vertical scroll or a fling in progress: leave this touch alone
        private boolean mIgnore;
        @Nullable
        private VelocityTracker mVelocityTracker;

        @Override
        public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mDownX = e.getX();
                    mDownY = e.getY();
                    mIgnore = rv.getScrollState() != RecyclerView.SCROLL_STATE_IDLE;
                    trackVelocity(e, true);
                    return false;
                case MotionEvent.ACTION_MOVE:
                    trackVelocity(e, false);
                    if (mIgnore) return false;
                    float dx = Math.abs(e.getX() - mDownX);
                    float dy = Math.abs(e.getY() - mDownY);
                    if (dy > mTouchSlop && dy >= dx) {
                        mIgnore = true;
                        return false;
                    }
                    // Taking the touch cancels it for the icon under the finger
                    return dx > mTouchSlop * 2 && dx > dy * 2;
                case MotionEvent.ACTION_POINTER_DOWN:
                    mIgnore = true;
                    return false;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    recycleVelocity();
                    return false;
                default:
                    return false;
            }
        }

        @Override
        public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    trackVelocity(e, false);
                    break;
                case MotionEvent.ACTION_UP: {
                    trackVelocity(e, false);
                    float dx = e.getX() - mDownX;
                    float velocity = 0;
                    if (mVelocityTracker != null) {
                        mVelocityTracker.computeCurrentVelocity(1000);
                        velocity = mVelocityTracker.getXVelocity();
                    }
                    boolean fling = Math.abs(velocity) > mMinFlingVelocity
                            && Math.signum(velocity) == Math.signum(dx);
                    if (fling || Math.abs(dx) > rv.getWidth() / 4f) {
                        // Finger to the left: the next tab, the one on the right (LTR)
                        int step = dx < 0 ? 1 : -1;
                        if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) step = -step;
                        selectIndex(indexOf(mSelectedId) + step);
                    }
                    recycleVelocity();
                    break;
                }
                case MotionEvent.ACTION_CANCEL:
                    recycleVelocity();
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {}

        private void trackVelocity(MotionEvent e, boolean reset) {
            if (mVelocityTracker == null) {
                mVelocityTracker = VelocityTracker.obtain();
            } else if (reset) {
                mVelocityTracker.clear();
            }
            mVelocityTracker.addMovement(e);
        }

        private void recycleVelocity() {
            if (mVelocityTracker != null) {
                mVelocityTracker.recycle();
                mVelocityTracker = null;
            }
        }
    }
}
