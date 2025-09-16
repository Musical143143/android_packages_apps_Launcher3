/*
 * Copyright (C) 2025 AxionOS
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
package com.android.launcher3

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.*
import com.android.internal.util.android.Utils
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.DaggerSingletonTracker
import kotlinx.coroutines.*
import javax.inject.Inject

@LauncherAppSingleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    tracker: DaggerSingletonTracker
) : SharedPreferences.OnSharedPreferenceChangeListener,
    DefaultLifecycleObserver {

    private var isListenerRegistered = false
    private val observedKeys = mutableSetOf<String>()

    private val _restartNeeded = MutableLiveData(false)
    val restartNeeded: LiveData<Boolean> get() = _restartNeeded

    private val job = SupervisorJob()
    private val coroutineScope = CoroutineScope(job + Dispatchers.Main)

    init {
        tracker.addCloseable {
            cleanup()
        }
    }

    fun addTunables(vararg keys: String) {
        observedKeys.addAll(keys)
        if (DEBUG) Log.d(TAG, "Added tunables: ${keys.toList()}")
    }

    override fun onResume(owner: LifecycleOwner) {
        registerListener()
        if (DEBUG) Log.d(TAG, "SettingsRepository resumed")
    }

    override fun onPause(owner: LifecycleOwner) {
        unregisterListener()
        if (DEBUG) Log.d(TAG, "SettingsRepository paused")
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key == null) return
        if (DEBUG) Log.d(TAG, "Preference changed: $key")
        if (observedKeys.contains(key)) {
            _restartNeeded.value = true
            if (DEBUG) Log.d(TAG, "Key '$key' requires restart.")
        }
    }

    fun onUserReturnedHome() {
        if (_restartNeeded.value == true) {
            _restartNeeded.value = false
            coroutineScope.launch {
                Toast.makeText(context, "Restarting launcher...", Toast.LENGTH_SHORT).show()
                delay(1250)
                cleanup()
                System.exit(0)
            }
        }
    }

    fun forceRestart() {
        if (_restartNeeded.value != true) {
            _restartNeeded.value = true
            if (DEBUG) Log.d(TAG, "forceRestart() triggered")
        }
    }

    fun getPrefs(): SharedPreferences = LauncherPrefs.getPrefs(context)

    fun isPackageEnabled(pkg: String): Boolean =
        Utils.isPackageEnabled(context, pkg)

    fun isPackageInstalled(pkg: String): Boolean =
        Utils.isPackageInstalled(context, pkg)

    private fun registerListener() {
        if (!isListenerRegistered) {
            getPrefs().registerOnSharedPreferenceChangeListener(this)
            isListenerRegistered = true
            if (DEBUG) Log.d(TAG, "SharedPreferences listener registered.")
        }
    }

    private fun unregisterListener() {
        if (isListenerRegistered) {
            getPrefs().unregisterOnSharedPreferenceChangeListener(this)
            isListenerRegistered = false
            if (DEBUG) Log.d(TAG, "SharedPreferences listener unregistered.")
        }
    }

    private fun removeAllTunables() {
        observedKeys.clear()
        if (DEBUG) Log.d(TAG, "Removed all tunables")
    }

    private fun cleanup() {
        unregisterListener()
        removeAllTunables()
        job.cancel()
        if (DEBUG) Log.d(TAG, "SettingsRepository cleaned up")
    }

    companion object {
        @VisibleForTesting
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getSettingsRepository)

        @JvmStatic
        fun get(context: Context): SettingsRepository = INSTANCE.get(context)

        private const val TAG = "SettingsRepository"
        private const val DEBUG = false
    }
}
