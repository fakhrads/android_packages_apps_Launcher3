package com.android.launcher3;

import android.content.ComponentName;
import android.content.Context;

import com.android.launcher3.dagger.ApplicationContext;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

/**
 * Utility class to filter out components from various lists
 */
public class AppFilter {

    private final Context mContext;
    private final Set<ComponentName> mFilteredComponents;

    @Inject
    public AppFilter(@ApplicationContext Context context) {
        mContext = context;
        mFilteredComponents = Arrays.stream(
                context.getResources().getStringArray(R.array.filtered_components))
                .map(ComponentName::unflattenFromString)
                .collect(Collectors.toSet());
    }

    /**
     * Whether an app (or a widget provider) is listed. Apps the user hid are not, so they are
     * gone from the app drawer and its search; their icons on the home screen stay.
     */
    public boolean shouldShowApp(ComponentName app) {
        return !mFilteredComponents.contains(app) && !isHiddenApp(mContext, app);
    }

    public static boolean isHiddenApp(Context context, ComponentName app) {
        return LauncherPrefs.get(context).get(LauncherPrefs.HIDDEN_APPS)
                .contains(app.flattenToShortString());
    }

    /** Hides or shows an app in the app drawer; the model reloads to apply it. */
    public static void setAppHidden(Context context, ComponentName app, boolean hidden) {
        Set<String> apps = new HashSet<>(
                LauncherPrefs.get(context).get(LauncherPrefs.HIDDEN_APPS));
        boolean changed = hidden ? apps.add(app.flattenToShortString())
                : apps.remove(app.flattenToShortString());
        if (!changed) return;
        LauncherPrefs.get(context).put(LauncherPrefs.HIDDEN_APPS, apps);
        LauncherAppState.INSTANCE.executeIfCreated(
                state -> state.getModel().reloadIfActive("hidden apps changed"));
    }
}
