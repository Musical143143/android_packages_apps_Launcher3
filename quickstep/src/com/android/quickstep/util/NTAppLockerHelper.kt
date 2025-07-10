/*
 * Copyright (C) 2025 AxionOS Project
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
package com.android.quickstep.util

import android.os.*
import android.util.Log
import com.android.internal.app.IAppLockListener
import com.android.internal.app.IAppLockManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class NTAppLockerHelper private constructor() {

    companion object {
        private const val TAG = "NTAppLockerHelper"

        @Volatile
        private var instance: NTAppLockerHelper? = null

        fun get(): NTAppLockerHelper {
            return instance ?: synchronized(this) {
                instance ?: NTAppLockerHelper().also { instance = it }
            }
        }
    }

    private val appLockManagerRef = AtomicReference<IAppLockManager?>()
    private val appLockCache = ConcurrentHashMap<String, Boolean>()
    private var listenerRegistered = false

    private val listener = object : IAppLockListener.Stub() {
        override fun onAppLockerUpdated() {
            clearAppLockedCache()
        }
    }

    private val deathRecipient = IBinder.DeathRecipient {
        onDeathRecipient()
    }

    fun isAppLocked(packageName: String): Boolean {
        if (packageName.isBlank()) return false

        val cached = appLockCache[packageName]
        if (cached != null) return cached

        val manager = getAppLockManager() ?: return false

        return try {
            val locked = manager.isAppLocked(packageName)
            appLockCache[packageName] = locked
            locked
        } catch (e: RemoteException) {
            Log.w(TAG, "RemoteException in isAppLocked: ${e.message}")
            false
        }
    }

    fun isAppLockedWithoutCache(packageName: String): Boolean {
        return try {
            getAppLockManager()?.isAppLocked(packageName) ?: false
        } catch (e: RemoteException) {
            Log.w(TAG, "RemoteException in isAppLockedWithoutCache: ${e.message}")
            false
        }
    }

    fun clearAppLockedCache() {
        appLockCache.clear()
    }

    private fun getAppLockManager(): IAppLockManager? {
        appLockManagerRef.get()?.let { return it }

        synchronized(this) {
            appLockManagerRef.get()?.let { return it }

            val binder = ServiceManager.getService("app_lock") ?: return null
            val manager = IAppLockManager.Stub.asInterface(binder)

            try {
                binder.linkToDeath(deathRecipient, 0)
                if (!listenerRegistered) {
                    manager.registerListener(listener)
                    listenerRegistered = true
                }
                appLockManagerRef.set(manager)
                return manager
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to connect to AppLockManager: ${e.message}")
                return null
            }
        }
    }

    fun onDeathRecipient() {
        synchronized(this) {
            try {
                appLockManagerRef.get()?.asBinder()?.unlinkToDeath(deathRecipient, 0)
            } catch (_: Exception) {}

            try {
                appLockManagerRef.get()?.unregisterListener(listener)
            } catch (_: Exception) {}

            appLockManagerRef.set(null)
            listenerRegistered = false
            appLockCache.clear()
        }
    }
}
