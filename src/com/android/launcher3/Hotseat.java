/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.launcher3;

import static android.view.View.MeasureSpec.makeMeasureSpec;

import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;
import static com.android.launcher3.util.MultiTranslateDelegate.INDEX_BUBBLE_ADJUSTMENT_ANIM;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import com.android.launcher3.ShortcutAndWidgetContainer.TranslationProvider;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.dagger.LauncherComponentProvider;
import com.android.launcher3.dragndrop.SystemDragItemInfo;
import com.android.launcher3.homescreenfiles.HomeScreenFilesUtilsKt;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.util.BlurBackgroundHelper;
import com.android.launcher3.util.DockStyle;
import com.android.launcher3.util.HorizontalInsettableView;
import com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.MultiPropertyFactory.MultiProperty;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.widget.PendingAddWidgetInfo;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * View class that represents the bottom row of the home screen.
 */
public class Hotseat extends CellLayout implements Insettable {

    public static final int ALPHA_CHANNEL_TASKBAR_ALIGNMENT = 0;
    public static final int ALPHA_CHANNEL_PREVIEW_RENDERER = 1;
    public static final int ALPHA_CHANNEL_TASKBAR_STASH = 2;
    public static final int ALPHA_CHANNEL_ASSISTANT_VISIBILITY = 3;
    public static final int ALPHA_CHANNEL_CHANNELS_COUNT = 4;

    @Retention(RetentionPolicy.RUNTIME)
    @IntDef({ALPHA_CHANNEL_TASKBAR_ALIGNMENT, ALPHA_CHANNEL_PREVIEW_RENDERER,
            ALPHA_CHANNEL_TASKBAR_STASH, ALPHA_CHANNEL_ASSISTANT_VISIBILITY})
    public @interface HotseatQsbAlphaId {
    }

    public static final int ICONS_TRANSLATION_X_NAV_BAR_ALIGNMENT = 0;
    public static final int ICONS_TRANSLATION_X_CHANNELS_COUNT = 1;

    @Retention(RetentionPolicy.RUNTIME)
    @IntDef({ICONS_TRANSLATION_X_NAV_BAR_ALIGNMENT})
    public @interface IconsTranslationX {
    }

    // Ratio of empty space, qsb should take up to appear visually centered.
    public static final float QSB_CENTER_FACTOR = .325f;
    private static final int BUBBLE_BAR_ADJUSTMENT_ANIMATION_DURATION_MS = 250;

    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mHasVerticalHotseat;
    private Workspace<?> mWorkspace;
    private boolean mSendTouchToWorkspace;
    private final MultiValueAlpha mIconsAlphaChannels;
    private final @Nullable MultiValueAlpha mQsbAlphaChannels;

    private @Nullable MultiProperty mQsbTranslationX;

    private final MultiPropertyFactory mIconsTranslationXFactory;

    private final @Nullable View mQsb;
    private final String mDockStyle;
    private final @Nullable View mDockPillView;
    private boolean mWindowVisible = true;

    public Hotseat(Context context) {
        this(context, null);
    }

    public Hotseat(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Hotseat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mDockStyle = DockStyle.get(context);
        boolean pillDock = DockStyle.isPill(mDockStyle);
        if (pillDock) {
            mDockPillView = new View(context);
            mDockPillView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            mDockPillView.setClickable(false);
            mDockPillView.setFocusable(false);
            // Index 0 keeps the floating dock capsule strictly behind the dock icons container.
            addView(mDockPillView, 0);
        } else {
            mDockPillView = null;
        }

        if (Utilities.showQSB(context)) {
            int qsbLayout = pillDock
                    ? R.layout.qsb_pill_compact
                    : R.layout.qsb_container_hotseat_pixel;
            mQsb = LayoutInflater.from(context).inflate(qsbLayout, this, false);
        } else {
            mQsb = null;
        }

        if (mQsb != null) {
            addView(mQsb);
            if (mQsb instanceof Reorderable qsbReorderable) {
                mQsbTranslationX = qsbReorderable.getTranslateDelegate()
                        .getTranslationX(MultiTranslateDelegate.INDEX_NAV_BAR_ANIM);
            }
            mQsbAlphaChannels = new MultiValueAlpha(mQsb, ALPHA_CHANNEL_CHANNELS_COUNT);
            mQsbAlphaChannels.setUpdateVisibility(true);
        } else {
            mQsbAlphaChannels = null;
        }

        mIconsAlphaChannels = new MultiValueAlpha(getShortcutsAndWidgets(),
                ALPHA_CHANNEL_CHANNELS_COUNT);
        mIconsAlphaChannels.setUpdateVisibility(true);
        mIconsTranslationXFactory = new MultiPropertyFactory<>(getShortcutsAndWidgets(),
                VIEW_TRANSLATE_X, ICONS_TRANSLATION_X_CHANNELS_COUNT, Float::sum);
    }

    /** Provides translation X for hotseat icons for the channel. */
    public MultiProperty getIconsTranslationX(@IconsTranslationX int channelId) {
        return mIconsTranslationXFactory.get(channelId);
    }

    /** Provides translation X for hotseat Qsb. */
    @Nullable
    public MultiProperty getQsbTranslationX() {
        return mQsbTranslationX;
    }

    /**
     * Returns orientation specific cell X given invariant order in the hotseat
     */
    public int getCellXFromOrder(int rank) {
        return mHasVerticalHotseat ? 0 : rank;
    }

    /**
     * Returns orientation specific cell Y given invariant order in the hotseat
     */
    public int getCellYFromOrder(int rank) {
        return mHasVerticalHotseat ? (getCountY() - (rank + 1)) : 0;
    }

    boolean isHasVerticalHotseat() {
        return mHasVerticalHotseat;
    }

    /** Returns whether the hotseat is a valid drop target for the specified drag object. */
    public boolean isValidDropTarget(DropTarget.DragObject dragObject) {
        final ShortcutAndWidgetContainer shortcutAndWidgetContainer = getShortcutsAndWidgets();
        return shortcutAndWidgetContainer != null
                && shortcutAndWidgetContainer.getVisibility() == View.VISIBLE
                && isSupportedDrag(dragObject);
    }

    public void resetLayout(boolean hasVerticalHotseat) {
        ActivityContext activityContext = ActivityContext.lookupContext(getContext());
        boolean bubbleBarEnabled = activityContext.isBubbleBarEnabled();
        boolean hasBubbles = activityContext.hasBubbles();
        removeAllViewsInLayout();
        mHasVerticalHotseat = hasVerticalHotseat;
        DeviceProfile dp = mActivity.getDeviceProfile();

        if (bubbleBarEnabled) {
            if (dp.shouldAdjustHotseatForBubbleBar(getContext(), hasBubbles)) {
                getShortcutsAndWidgets().setTranslationProvider(
                        cellX -> dp.getHotseatAdjustedTranslation(getContext(), cellX));
                if (mQsb != null && mQsb instanceof HorizontalInsettableView) {
                    HorizontalInsettableView insettableQsb = (HorizontalInsettableView) mQsb;
                    final float insetFraction =
                            (float) dp.getWorkspaceProfile().getIconSizePx()
                                    / dp.getHotseatProfile().getQsbWidth();
                    // post this to the looper so that QSB has a chance to redraw itself, e.g.
                    // after device rotation
                    mQsb.post(() -> insettableQsb.setHorizontalInsets(insetFraction));
                }
            } else {
                getShortcutsAndWidgets().setTranslationProvider(null);
                if (mQsb != null && mQsb instanceof HorizontalInsettableView) {
                    ((HorizontalInsettableView) mQsb).setHorizontalInsets(0);
                }
            }
        }

        resetCellSize(dp);
        if (hasVerticalHotseat) {
            setGridSize(1, dp.getHotseatProfile().getNumShownIcons());
        } else {
            setGridSize(dp.getHotseatProfile().getNumShownIcons(), 1);
        }
    }

    /**
     * Adjust the hotseat icons for the bubble bar.
     *
     * <p>When the bubble bar becomes visible, if needed, this method animates the hotseat icons
     * to reduce the spacing between them and make room for the bubble bar. The QSB width is
     * animated as well to align with the hotseat icons.
     *
     * <p>When the bubble bar goes away, any adjustments that were previously made are reversed.
     */
    public void adjustForBubbleBar(boolean isBubbleBarVisible) {
        DeviceProfile dp = mActivity.getDeviceProfile();
        boolean shouldAdjust = isBubbleBarVisible
                && dp.shouldAdjustHotseatOrQsbForBubbleBar(getContext());
        boolean shouldAdjustHotseat = shouldAdjust
                && dp.shouldAlignBubbleBarWithHotseat();
        ShortcutAndWidgetContainer icons = getShortcutsAndWidgets();
        // update the translation provider for future layout passes of hotseat icons.
        if (shouldAdjustHotseat) {
            icons.setTranslationProvider(
                    cellX -> dp.getHotseatAdjustedTranslation(getContext(), cellX));
        } else {
            icons.setTranslationProvider(null);
        }
        AnimatorSet animatorSet = new AnimatorSet();
        for (int i = 0; i < icons.getChildCount(); i++) {
            View child = icons.getChildAt(i);
            if (child.getLayoutParams() instanceof CellLayoutLayoutParams lp) {
                float tx = shouldAdjustHotseat
                        ? dp.getHotseatAdjustedTranslation(getContext(), lp.getCellX()) : 0;
                if (child instanceof Reorderable) {
                    MultiTranslateDelegate mtd = ((Reorderable) child).getTranslateDelegate();
                    animatorSet.play(
                            mtd.getTranslationX(INDEX_BUBBLE_ADJUSTMENT_ANIM).animateToValue(tx));
                } else {
                    animatorSet.play(ObjectAnimator.ofFloat(child, VIEW_TRANSLATE_X, tx));
                }
            }
        }
        //TODO(b/381109832) refactor & simplify adjustment logic
        boolean shouldAdjustQsb =
                shouldAdjustHotseat || (shouldAdjust && dp.shouldAlignBubbleBarWithQSB());
        if (mQsb != null && mQsb instanceof HorizontalInsettableView horizontalInsettableQsb) {
            final float currentInsetFraction = horizontalInsettableQsb.getHorizontalInsets();
            final float targetInsetFraction = shouldAdjustQsb
                    ? (float) dp.getWorkspaceProfile().getIconSizePx() / dp.getHotseatProfile()
                    .getQsbWidth()
                    : 0;
            ValueAnimator qsbAnimator =
                    ValueAnimator.ofFloat(currentInsetFraction, targetInsetFraction);
            qsbAnimator.addUpdateListener(animation -> {
                float insetFraction = (float) animation.getAnimatedValue();
                horizontalInsettableQsb.setHorizontalInsets(insetFraction);
            });
            animatorSet.play(qsbAnimator);
        }
        animatorSet.setDuration(BUBBLE_BAR_ADJUSTMENT_ANIMATION_DURATION_MS).start();
    }

    @Override
    protected int getTranslationXForCell(int cellX, int cellY) {
        TranslationProvider translationProvider = getShortcutsAndWidgets().getTranslationProvider();
        if (translationProvider == null) return 0;
        return (int) translationProvider.getTranslationX(cellX);
    }

    @Override
    public void setInsets(Rect insets) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        DeviceProfile grid = mActivity.getDeviceProfile();

        int topOverlap = 0;
        if (grid.isVerticalBarLayout()) {
            if (mQsb != null) mQsb.setVisibility(View.GONE);
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            if (grid.isSeascape()) {
                lp.gravity = Gravity.LEFT;
                lp.width = grid.getHotseatProfile().getBarSizePx() + insets.left;
            } else {
                lp.gravity = Gravity.RIGHT;
                lp.width = grid.getHotseatProfile().getBarSizePx() + insets.right;
            }
        } else {
            if (mQsb != null) mQsb.setVisibility(View.VISIBLE);
            lp.gravity = Gravity.BOTTOM;
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;

            // Since QSB is laid out relative to bottom, it expects a certain amount of available
            // space in its parent (hotseat). If hotseatBarSizePx is less than that, we let it go
            // beyond and offset the content accordingly.
            int totalHeightForQsb = grid.getQsbOffsetY() + grid.getHotseatProfile().getQsbHeight();
            topOverlap = Math.max(0, totalHeightForQsb - grid.getHotseatProfile().getBarSizePx());
            lp.height = grid.getHotseatProfile().getBarSizePx() + topOverlap;
        }

        if (mDockPillView != null) {
            mDockPillView.setVisibility(grid.isPillDockStyle() ? View.VISIBLE : View.GONE);
        }

        Rect padding = grid.getHotseatLayoutPadding(getContext());
        setPadding(padding.left, padding.top + topOverlap, padding.right, padding.bottom);
        setLayoutParams(lp);
        InsettableFrameLayout.dispatchInsets(this, insets);
        updateDockPillSurfaces();
    }

    public void setWorkspace(Workspace<?> w) {
        mWorkspace = w;
        setCellLayoutContainer(w);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // We allow horizontal workspace scrolling from within the Hotseat. We do this by delegating
        // touch intercept the Workspace, and if it intercepts, delegating touch to the Workspace
        // for the remainder of the this input stream.
        int yThreshold = getMeasuredHeight() - getPaddingBottom();
        if (mWorkspace != null && ev.getY() <= yThreshold) {
            mSendTouchToWorkspace = mWorkspace.onInterceptTouchEvent(ev);
            return mSendTouchToWorkspace;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // See comment in #onInterceptTouchEvent
        if (mSendTouchToWorkspace) {
            final int action = event.getAction();
            switch (action & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mSendTouchToWorkspace = false;
            }
            return mWorkspace.onTouchEvent(event);
        }
        // Always let touch follow through to Workspace.
        return false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (mQsb != null && mQsb.getVisibility() != View.GONE) {
            DeviceProfile dp = mActivity.getDeviceProfile();
            if (dp.isPillDockStyle()) {
                int maxWidth = Math.max(0, MeasureSpec.getSize(widthMeasureSpec)
                        - getPaddingLeft() - getPaddingRight());
                mQsb.measure(
                        makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST),
                        makeMeasureSpec(dp.getHotseatProfile().getQsbHeight(), MeasureSpec.EXACTLY)
                );
            } else {
                mQsb.measure(
                        makeMeasureSpec(dp.getHotseatProfile().getQsbWidth(), MeasureSpec.EXACTLY),
                        makeMeasureSpec(dp.getHotseatProfile().getQsbHeight(), MeasureSpec.EXACTLY)
                );
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        DeviceProfile dp = mActivity.getDeviceProfile();
        if (mDockPillView != null && mDockPillView.getVisibility() == View.VISIBLE
                && dp.isPillDockStyle()) {
            ShortcutAndWidgetContainer icons = getShortcutsAndWidgets();
            int padH = getResources().getDimensionPixelSize(R.dimen.dock_pill_horizontal_padding);
            int minEdge = getResources().getDimensionPixelSize(R.dimen.dock_pill_min_edge_margin);
            int pillLeft = Math.max(minEdge, icons.getLeft() - padH);
            int pillRight = Math.min((r - l) - minEdge, icons.getRight() + padH);

            int minIconPad = Math.round(8f * getResources().getDisplayMetrics().density);
            int targetPillHeight = Math.max(
                    dp.getWorkspaceProfile().getIconSizePx()
                            + dp.getHotseatProfile().getDockPillVerticalExtraPx(),
                    (icons.getBottom() - icons.getTop()) + minIconPad);
            int centerY = (icons.getTop() + icons.getBottom()) / 2;
            int pillTop = Math.max(0, centerY - targetPillHeight / 2);
            int pillBottom = Math.min(b - t, pillTop + targetPillHeight);

            if (pillRight > pillLeft && pillBottom > pillTop) {
                mDockPillView.measure(
                        makeMeasureSpec(pillRight - pillLeft, MeasureSpec.EXACTLY),
                        makeMeasureSpec(pillBottom - pillTop, MeasureSpec.EXACTLY));
                mDockPillView.layout(pillLeft, pillTop, pillRight, pillBottom);
            }
        }

        if (mQsb != null && mQsb.getVisibility() != View.GONE) {
            int qsbMeasuredWidth = mQsb.getMeasuredWidth();
            int left;
            if (dp.getHotseatProfile().isQsbInline()) {
                int qsbSpace = dp.getHotseatProfile().getBorderSpace();
                left = Utilities.isRtl(getResources()) ? r - getPaddingRight() + qsbSpace
                        : l + getPaddingLeft() - qsbMeasuredWidth - qsbSpace;
            } else {
                left = (r - l - qsbMeasuredWidth) / 2;
            }
            int right = left + qsbMeasuredWidth;

            int bottom = b - t - dp.getQsbOffsetY();
            int top = bottom - dp.getHotseatProfile().getQsbHeight();
            mQsb.layout(left, top, right, bottom);
        }

        updateDockPillSurfaces();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mDockPillView != null) {
            post(this::updateDockPillSurfaces);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        mWindowVisible = false;
        updateDockPillSurfaces();
        super.onDetachedFromWindow();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        mWindowVisible = isVisible;
        updateDockPillSurfaces();
    }

    @Override
    public void setAlpha(float alpha) {
        super.setAlpha(alpha);
        updateDockPillSurfaces();
    }

    private void updateDockPillSurfaces() {
        if (mDockPillView == null) return;
        DeviceProfile dp = mActivity.getDeviceProfile();
        if (dp == null) return;
        BlurBackgroundHelper blurHelper = mActivity.getActivityComponent() != null
                ? mActivity.getActivityComponent().getBlurBackgroundHelper()
                : null;
        boolean pillActive = dp.isPillDockStyle();
        boolean hostVisible = isAttachedToWindow()
                && getVisibility() == View.VISIBLE
                && mWindowVisible;
        float hostAlpha = getAlpha();

        if (mDockPillView.getHeight() > 0) {
            float iconsAlpha = getShortcutsAndWidgets().getAlpha();
            mDockPillView.setAlpha(iconsAlpha);
            boolean dockVisible = hostVisible
                    && pillActive
                    && mDockPillView.getVisibility() == View.VISIBLE
                    && getShortcutsAndWidgets().getVisibility() == View.VISIBLE;
            float dockCorner = mDockPillView.getHeight() / 2f;
            if (blurHelper != null) {
                blurHelper.updateDockPillBackground(
                        mDockPillView, mDockStyle, dockCorner,
                        hostAlpha * iconsAlpha, dockVisible);
            } else if (mDockPillView.getBackground() == null) {
                mDockPillView.setBackground(
                        DockStyle.createSurfaceDrawable(getContext(), mDockStyle, dockCorner, false));
            }
        }

        if (mQsb != null && pillActive && mQsb.getHeight() > 0) {
            float qsbAlpha = mQsb.getAlpha();
            boolean qsbVisible = hostVisible && mQsb.getVisibility() == View.VISIBLE;
            float qsbCorner = mQsb.getHeight() / 2f;
            if (blurHelper != null) {
                blurHelper.updateDockPillBackground(
                        mQsb, mDockStyle, qsbCorner,
                        hostAlpha * qsbAlpha, qsbVisible);
            } else if (mQsb.getBackground() == null) {
                mQsb.setBackground(
                        DockStyle.createSurfaceDrawable(getContext(), mDockStyle, qsbCorner, false));
            }
        }
    }

    /**
     * Sets the alpha value of the specified alpha channel of just our ShortcutAndWidgetContainer.
     */
    public void setIconsAlpha(float alpha, @HotseatQsbAlphaId int channelId) {
        getIconsAlpha(channelId).setValue(alpha);
        updateDockPillSurfaces();
    }

    /**
     * Sets the alpha value of just our QSB.
     */
    public void setQsbAlpha(float alpha, @HotseatQsbAlphaId int channelId) {
        MultiProperty prop = getQsbAlpha(channelId);
        if (prop != null) {
            prop.setValue(alpha);
        }
        updateDockPillSurfaces();
    }

    /** Returns the alpha channel for ShortcutAndWidgetContainer */
    public MultiProperty getIconsAlpha(@HotseatQsbAlphaId int channelId) {
        return mIconsAlphaChannels.get(channelId);
    }

    /** Returns the alpha channel for Qsb, or null if QSB is not present */
    @Nullable
    public MultiProperty getQsbAlpha(@HotseatQsbAlphaId int channelId) {
        return mQsbAlphaChannels != null ? mQsbAlphaChannels.get(channelId) : null;
    }

    /**
     * Returns the QSB inside hotseat, or null if QSB is not enabled.
     */
    @Nullable
    public View getQsb() {
        return mQsb;
    }

    @Nullable
    @Override
    public View mapOverItems(ItemOperator op) {
        if (mQsb != null
                && mQsb.getTag() instanceof ItemInfo info
                && op.evaluate(info, mQsb)) {
            return mQsb;
        }
        return super.mapOverItems(op);
    }

    /** Dumps the Hotseat internal state */
    public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + "Hotseat:");
        mIconsAlphaChannels.dump(
                prefix + "\t",
                writer,
                "mIconsAlphaChannels",
                "ALPHA_CHANNEL_TASKBAR_ALIGNMENT",
                "ALPHA_CHANNEL_PREVIEW_RENDERER",
                "ALPHA_CHANNEL_TASKBAR_STASH");
        if (mQsbAlphaChannels != null) {
            mQsbAlphaChannels.dump(
                    prefix + "\t",
                    writer,
                    "mQsbAlphaChannels",
                    "ALPHA_CHANNEL_TASKBAR_ALIGNMENT",
                    "ALPHA_CHANNEL_PREVIEW_RENDERER",
                    "ALPHA_CHANNEL_TASKBAR_STASH"
            );
        }
    }

    // TODO(b/479881252): Determine whether it still makes sense to disallow all instances of
    //  `SystemDragItemInfo` once `SystemDragController` is supported in all `ActivityContext`s. The
    //  current assumption is that `SystemDragItemInfo` indicates files dragged from another app.
    private boolean isSupportedDrag(DropTarget.DragObject d) {
        return !(HomeScreenFilesUtilsKt.isFileSystemItem(d.dragInfo)
                || d.dragInfo instanceof LauncherAppWidgetInfo
                || d.dragInfo instanceof PendingAddWidgetInfo
                || d.dragInfo instanceof SystemDragItemInfo);
    }

}
