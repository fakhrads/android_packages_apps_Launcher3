/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.launcher3.touch;

import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.OVERVIEW;

import android.app.StatusBarManager;
import android.view.MotionEvent;

import com.android.launcher3.Launcher;

/** What a double tap or a pinch on blank home screen space does. */
public final class HomeGestureActions {

    public static final String NONE = "none";
    public static final String SLEEP = "sleep";
    public static final String NOTIFICATIONS = "notifications";
    public static final String QUICK_SETTINGS = "quick_settings";
    public static final String APP_DRAWER = "app_drawer";
    public static final String RECENTS = "recents";

    private HomeGestureActions() {}

    /** Runs [action]; false when it is none or unknown, so the touch is not consumed. */
    public static boolean perform(Launcher launcher, String action, MotionEvent event) {
        switch (action) {
            case SLEEP:
                launcher.onSleepEvent(event);
                return true;
            case NOTIFICATIONS:
                launcher.getSystemService(StatusBarManager.class).expandNotificationsPanel();
                return true;
            case QUICK_SETTINGS:
                launcher.getSystemService(StatusBarManager.class).expandSettingsPanel();
                return true;
            case APP_DRAWER:
                launcher.getStateManager().goToState(ALL_APPS);
                return true;
            case RECENTS:
                launcher.getStateManager().goToState(OVERVIEW);
                return true;
            default:
                return false;
        }
    }
}
