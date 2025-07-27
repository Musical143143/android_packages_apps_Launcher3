/*
 * Copyright (C) 2019 The LineageOS Project
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
package com.android.launcher3.lineage.trust.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.util.NTAppLockerHelper;

public class TrustDatabaseHelper {
    @Nullable
    private static TrustDatabaseHelper sSingleton;

    private TrustDatabaseHelper() {}

    public static synchronized TrustDatabaseHelper getInstance() {
        if (sSingleton == null) {
            sSingleton = new TrustDatabaseHelper();
        }
        return sSingleton;
    }

    public void addHiddenApp(@NonNull String packageName) {
        if (isPackageHidden(packageName)) {
            return;
        }
        NTAppLockerHelper.get().setPackageHidden(packageName, true);
    }

    public void addProtectedApp(@NonNull String packageName) {
        if (isPackageProtected(packageName)) {
            return;
        }
        NTAppLockerHelper.get().addLockedApp(packageName);
    }


    public void removeHiddenApp(@NonNull String packageName) {
        if (!isPackageHidden(packageName)) {
            return;
        }
        NTAppLockerHelper.get().setPackageHidden(packageName, false);
    }

    public void removeProtectedApp(@NonNull String packageName) {
        if (!isPackageProtected(packageName)) {
            return;
        }
        NTAppLockerHelper.get().removeLockedApp(packageName);
    }

    public boolean isPackageHidden(@NonNull String packageName) {
        return NTAppLockerHelper.get().isPackageHidden(packageName);
    }

    public boolean isPackageProtected(@NonNull String packageName) {
        return NTAppLockerHelper.get().isAppLocked(packageName);
    }
}
