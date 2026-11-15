package com.android.launcher3;

import android.content.ComponentName;
import android.content.Context;

import androidx.annotation.Nullable;

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
        return hiddenApps(context).contains(app.flattenToShortString());
    }

    /** The apps the user hid, as flattened short component names. */
    public static Set<String> hiddenApps(Context context) {
        return LauncherPrefs.get(context).get(LauncherPrefs.HIDDEN_APPS);
    }

    /**
     * Whether a search result of {@code pkg} belongs to a hidden app: the activity it opens is
     * hidden, or, when that activity is not known, any app of the package is.
     */
    public static boolean isHiddenTarget(Set<String> hidden, @Nullable String pkg,
            @Nullable ComponentName component) {
        if (hidden.isEmpty()) return false;
        if (component != null) return hidden.contains(component.flattenToShortString());
        if (pkg == null) return false;
        String prefix = pkg + "/";
        for (String app : hidden) {
            if (app.startsWith(prefix)) return true;
        }
        return false;
    }

    /** Hides or shows an app in the app drawer; the model reloads to apply it. */
    public static void setAppHidden(Context context, ComponentName app, boolean hidden) {
        if (saveAppHidden(context, app, hidden)) reloadForHiddenApps();
    }

    /**
     * Hides or shows an app without applying it yet, for a list of switches; call
     * {@link #reloadForHiddenApps()} once when done. Returns whether anything changed.
     */
    public static boolean saveAppHidden(Context context, ComponentName app, boolean hidden) {
        Set<String> apps = new HashSet<>(hiddenApps(context));
        boolean changed = hidden ? apps.add(app.flattenToShortString())
                : apps.remove(app.flattenToShortString());
        if (changed) LauncherPrefs.get(context).put(LauncherPrefs.HIDDEN_APPS, apps);
        return changed;
    }

    /** Reloads the model, which applies the hidden apps to the drawer and its search. */
    public static void reloadForHiddenApps() {
        LauncherAppState.INSTANCE.executeIfCreated(
                state -> state.getModel().reloadIfActive("hidden apps changed"));
    }
}
