/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.quickspace;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.app.smartspace.SmartspaceAction;
import android.app.smartspace.SmartspaceConfig;
import android.app.smartspace.SmartspaceManager;
import android.app.smartspace.SmartspaceSession;
import android.app.smartspace.SmartspaceTarget;
import android.app.smartspace.SmartspaceTargetEvent;
import android.app.smartspace.uitemplatedata.BaseTemplateData;
import android.app.smartspace.uitemplatedata.TapAction;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import java.util.List;

/**
 * Reads At a Glance from the system Smartspace service (Android System Intelligence on GApps
 * builds), the way Pixel Launcher does. The framework lets the recents component, which is this
 * launcher, open a session without extra permissions.
 */
public class GoogleSmartspaceClient implements SmartspaceSession.OnTargetsAvailableListener {

    private static final String TAG = "GoogleSmartspace";
    private static final String UI_SURFACE_HOME = "home";
    private static final int TARGET_COUNT = 5;

    /** One line of At a Glance, already resolved for display. */
    public static final class Card {
        public final CharSequence title;
        public final CharSequence subtitle;
        public final Drawable icon;
        public final boolean tintIcon;
        public final View.OnClickListener action;

        Card(CharSequence title, CharSequence subtitle, Drawable icon, boolean tintIcon,
                View.OnClickListener action) {
            this.title = title;
            this.subtitle = subtitle;
            this.icon = icon;
            this.tintIcon = tintIcon;
            this.action = action;
        }
    }

    public interface Callback {
        void onSmartspaceUpdated();
    }

    private final Context mContext;
    private final Callback mCallback;
    private SmartspaceSession mSession;
    private boolean mShown;
    private volatile Card mWeather;
    private volatile Card mEvent;

    public GoogleSmartspaceClient(Context context, Callback callback) {
        mContext = context;
        mCallback = callback;
    }

    public void start() {
        if (mSession != null) return;
        SmartspaceManager manager = mContext.getSystemService(SmartspaceManager.class);
        if (manager == null) {
            Log.i(TAG, "no smartspace service");
            return;
        }
        try {
            mSession = manager.createSmartspaceSession(
                    new SmartspaceConfig.Builder(mContext, UI_SURFACE_HOME)
                            .setSmartspaceTargetCount(TARGET_COUNT)
                            .build());
            mSession.addOnTargetsAvailableListener(MAIN_EXECUTOR, this);
            mSession.requestSmartspaceUpdate();
        } catch (RuntimeException e) {
            Log.w(TAG, "could not open a smartspace session", e);
            mSession = null;
        }
    }

    public void stop() {
        if (mSession == null) return;
        setShown(false);
        try {
            mSession.removeOnTargetsAvailableListener(this);
            mSession.close();
        } catch (RuntimeException e) {
            Log.w(TAG, "could not close the smartspace session", e);
        }
        mSession = null;
        mWeather = null;
        mEvent = null;
    }

    /** The service ranks and refreshes its targets for a surface it is told is on screen. */
    public void setShown(boolean shown) {
        if (mSession == null || mShown == shown) return;
        mShown = shown;
        notifyEvent(new SmartspaceTargetEvent.Builder(shown
                ? SmartspaceTargetEvent.EVENT_UI_SURFACE_SHOWN
                : SmartspaceTargetEvent.EVENT_UI_SURFACE_HIDDEN).build());
        if (shown) {
            try {
                mSession.requestSmartspaceUpdate();
            } catch (RuntimeException e) {
                Log.w(TAG, "update request failed", e);
            }
        }
    }

    public Card getWeather() {
        return mWeather;
    }

    public Card getEvent() {
        return mEvent;
    }

    @Override
    public void onTargetsAvailable(List<SmartspaceTarget> targets) {
        Card weather = null;
        Card event = null;
        boolean debug = Log.isLoggable(TAG, Log.DEBUG);
        for (SmartspaceTarget target : targets) {
            Card card = toCard(target);
            if (debug) {
                Log.d(TAG, "target " + target.getSmartspaceTargetId()
                        + " feature=" + target.getFeatureType()
                        + " title=" + (card == null ? null : card.title)
                        + " subtitle=" + (card == null ? null : card.subtitle)
                        + " template=" + (target.getTemplateData() == null ? "none"
                                : target.getTemplateData().getTemplateType()));
            }
            if (card == null) continue;
            if (target.getFeatureType() == SmartspaceTarget.FEATURE_WEATHER) {
                if (weather == null) weather = card;
            } else if (event == null) {
                event = card;
            }
        }
        Log.i(TAG, targets.size() + " targets, weather=" + (weather != null)
                + " event=" + (event != null));
        mWeather = weather;
        mEvent = event;
        mCallback.onSmartspaceUpdated();
    }

    private Card toCard(SmartspaceTarget target) {
        CharSequence title = null;
        CharSequence subtitle = null;
        Icon icon = null;
        boolean tint = true;
        Intent intent = null;
        PendingIntent pendingIntent = null;

        BaseTemplateData data = target.getTemplateData();
        if (data != null) {
            BaseTemplateData.SubItemInfo primary = data.getPrimaryItem();
            BaseTemplateData.SubItemInfo sub = data.getSubtitleItem();
            title = text(primary);
            subtitle = text(sub);
            BaseTemplateData.SubItemInfo iconItem =
                    sub != null && sub.getIcon() != null ? sub
                    : primary != null && primary.getIcon() != null ? primary : null;
            if (iconItem != null) {
                icon = iconItem.getIcon().getIcon();
                tint = iconItem.getIcon().shouldTint();
            }
            TapAction tap = primary != null && primary.getTapAction() != null
                    ? primary.getTapAction()
                    : sub != null ? sub.getTapAction() : null;
            if (tap != null) {
                intent = tap.getIntent();
                pendingIntent = tap.getPendingIntent();
            }
        }

        SmartspaceAction header = target.getHeaderAction();
        if (header != null) {
            if (TextUtils.isEmpty(title)) title = header.getTitle();
            if (TextUtils.isEmpty(subtitle)) subtitle = header.getSubtitle();
            if (icon == null) icon = header.getIcon();
            if (intent == null && pendingIntent == null) {
                intent = header.getIntent();
                pendingIntent = header.getPendingIntent();
            }
        }
        SmartspaceAction base = target.getBaseAction();
        if (base != null && intent == null && pendingIntent == null) {
            intent = base.getIntent();
            pendingIntent = base.getPendingIntent();
        }

        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(subtitle)) return null;
        Drawable drawable = null;
        if (icon != null) {
            try {
                drawable = icon.loadDrawable(mContext);
            } catch (RuntimeException e) {
                Log.w(TAG, "icon load failed", e);
            }
        }
        return new Card(title, subtitle, drawable, tint,
                clickAction(target, intent, pendingIntent));
    }

    private View.OnClickListener clickAction(SmartspaceTarget target, Intent intent,
            PendingIntent pendingIntent) {
        if (intent == null && pendingIntent == null) return null;
        return v -> {
            try {
                if (pendingIntent != null) {
                    ActivityOptions options = ActivityOptions.makeBasic();
                    options.setPendingIntentBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                    pendingIntent.send(mContext, 0, null, null, null, null, options.toBundle());
                } else {
                    mContext.startActivity(new Intent(intent)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                }
            } catch (PendingIntent.CanceledException | RuntimeException e) {
                Log.w(TAG, "could not open " + target.getSmartspaceTargetId(), e);
            }
            notifyEvent(new SmartspaceTargetEvent.Builder(
                    SmartspaceTargetEvent.EVENT_TARGET_INTERACTION)
                    .setSmartspaceTarget(target)
                    .build());
        };
    }

    private void notifyEvent(SmartspaceTargetEvent event) {
        if (mSession == null) return;
        try {
            mSession.notifySmartspaceEvent(event);
        } catch (RuntimeException e) {
            Log.w(TAG, "event failed", e);
        }
    }

    private static CharSequence text(BaseTemplateData.SubItemInfo item) {
        if (item == null || item.getText() == null) return null;
        return item.getText().getText();
    }
}
