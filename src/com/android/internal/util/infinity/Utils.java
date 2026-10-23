/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (C) 2026 The Evolution X Project
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

package com.android.internal.util.infinity;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.SystemProperties;

public class Utils {

    public static boolean isPackageInstalled(Context context, String packageName, boolean ignoreState) {
        if (packageName != null && context != null) {
            try {
                PackageInfo pi = context.getPackageManager().getPackageInfo(packageName, 0);
                if (pi.applicationInfo != null && !pi.applicationInfo.enabled && !ignoreState) {
                    return false;
                }
                return true;
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
        }
        return false;
    }

    public static boolean isPackageInstalled(Context context, String packageName) {
        return isPackageInstalled(context, packageName, true);
    }

    public static boolean hasNavbarByDefault(Context context) {
        boolean needsNav = true;
        if (context != null) {
            try {
                int resId = context.getResources().getIdentifier(
                        "config_showNavigationBar", "bool", "android");
                if (resId > 0) {
                    needsNav = context.getResources().getBoolean(resId);
                } else {
                    needsNav = context.getResources().getBoolean(
                            com.android.internal.R.bool.config_showNavigationBar);
                }
            } catch (Exception e) {
                needsNav = true;
            }
        }

        String navBarOverride = SystemProperties.get("qemu.hw.mainkeys");
        if ("1".equals(navBarOverride)) {
            needsNav = false;
        } else if ("0".equals(navBarOverride)) {
            needsNav = true;
        }
        return needsNav;
    }
}
