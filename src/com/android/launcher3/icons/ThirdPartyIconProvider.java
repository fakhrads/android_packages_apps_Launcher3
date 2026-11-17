package com.android.launcher3.icons;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ComponentInfo;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;

import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.icons.pack.IconResolver;
import com.android.launcher3.util.ComponentKey;

import javax.inject.Inject;

import static com.android.launcher3.graphics.IconLoader.CONFIG_HINT_NO_WRAP;

@LauncherAppSingleton
public class ThirdPartyIconProvider extends LauncherIconProvider {

    @Inject
    public ThirdPartyIconProvider(
            @ApplicationContext Context context,
            ThemeManager themeManager) {
        super(context, themeManager);
    }

    public ThirdPartyIconProvider(Context context) {
        this(context, ThemeManager.INSTANCE.get(context));
    }

    @SuppressLint("WrongConstant")
    @Override
    public Drawable getIcon(ComponentInfo info, int iconDpi) {
        ComponentKey key = new ComponentKey(
                info.getComponentName(), UserHandle.getUserHandleForUid(info.applicationInfo.uid));

        IconResolver.DefaultDrawableProvider fallback =
                () -> super.getIcon(info, iconDpi);
        Drawable icon = ThirdPartyIconUtils.getByKey(mContext, key, iconDpi, fallback);

        if (icon == null) {
            return fallback.get();
        }
        if (!(icon instanceof AdaptiveIconDrawable)) {
            // DerpFest's icon factory leaves icon pack artwork unwrapped (no adaptive mask,
            // shadow or fill) when it comes as an ExtendedBitmapDrawable from an icon pack. The
            // hint is set after wrapping: IconLoader reads it from the drawable it gets.
            if (icon instanceof BitmapDrawable) {
                icon = ExtendedBitmapDrawable.wrap(mContext.getResources(), icon, true);
            }
            icon.setChangingConfigurations(icon.getChangingConfigurations() | CONFIG_HINT_NO_WRAP);
        }
        return icon;
    }
}
