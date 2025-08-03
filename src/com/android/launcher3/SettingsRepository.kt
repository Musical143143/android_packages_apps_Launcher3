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
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*

import com.android.internal.util.android.Utils

class SettingsRepository private constructor(private val context: Context) :
    SharedPreferences.OnSharedPreferenceChangeListener {

    private var isListenerRegistered = false
    private var isResumed = false

    private val _restartNeeded = MutableLiveData(false)
    val restartNeeded: LiveData<Boolean> get() = _restartNeeded

    private val job = SupervisorJob()
    private val coroutineScope = CoroutineScope(job + Dispatchers.Main)

    private val observedKeys = mutableSetOf<String>()

    fun addTunables(vararg keys: String) {
        val wasEmpty = observedKeys.isEmpty()
        observedKeys.addAll(keys)
        if (DEBUG) Log.d(TAG, "Added tunables: ${keys.toList()}")
        if (wasEmpty && isResumed) {
            registerListener()
        }
    }

    fun onResume() {
        if (!isResumed) {
            isResumed = true
            if (observedKeys.isNotEmpty()) {
                registerListener()
            }
            if (DEBUG) Log.d(TAG, "SettingsRepository resumed")
        }
    }

    fun onPause() {
        if (isResumed) {
            isResumed = false
            unregisterListener()
            if (DEBUG) Log.d(TAG, "SettingsRepository paused")
        }
    }

    fun removeAllTunables() {
        observedKeys.clear()
        unregisterListener()
        if (DEBUG) Log.d(TAG, "Removed all tunables")
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key == null) return
        if (DEBUG) Log.d(TAG, "Preference changed: $key")
        if (observedKeys.contains(key)) {
            if (DEBUG) Log.d(TAG, "Key '$key' requires restart.")
            _restartNeeded.value = true
        }
    }

    fun onUserReturnedHome() {
        if (_restartNeeded.value == true) {
            // just a precaution. unregister is already done during activity onPause()
            unregisterListener()
            removeAllTunables()
            _restartNeeded.value = false
            coroutineScope.launch {
                Toast.makeText(context, "Restarting launcher...", Toast.LENGTH_SHORT).show()
                delay(1250)
                job.cancel()
                System.exit(0)
            }
        }
    }

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

    fun onDestroy() {
        if (DEBUG) Log.d(TAG, "SettingsRepository destroyed")
        unregisterListener()
        removeAllTunables()
        job.cancel()
    }

    fun forceRestart() {
        if (_restartNeeded.value != true) {
            if (DEBUG) Log.d(TAG, "forceRestart() triggered")
            _restartNeeded.value = true
        }
    }
    
    fun getPrefs(): SharedPreferences = LauncherPrefs.getPrefs(context)

    fun isPackageEnabled(pkg: String): Boolean { 
        return Utils.isPackageEnabled(context, pkg)
    }

    fun isPackageInstalled(pkg: String): Boolean { 
        return Utils.isPackageInstalled(context, pkg)
    }

    companion object {
        private const val TAG = "SettingsRepository"
        private const val DEBUG = false

        @Volatile
        private var instance: SettingsRepository? = null

        @JvmStatic
        fun init(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = SettingsRepository(context.applicationContext)
                    }
                }
            }
        }

        @JvmStatic
        fun get(): SettingsRepository {
            return instance ?: throw IllegalStateException("SettingsRepository is not initialized.")
        }
    }
}
