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
package com.android.launcher3.allapps

import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.android.launcher3.allapps.search.NTAppsSearchContainerLayout
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.abs
import kotlin.math.max

class FloatingSearchBarManager(
    private val rootView: View,
    private val searchContainer: NTAppsSearchContainerLayout,
    private val editText: EditText
) {

    enum class SearchState {
        EXPANDED,
        COLLAPSED
    }

    private val _imeVisible = MutableStateFlow(false)
    private val _userInteracting = MutableStateFlow(false)
    private val _state = MutableStateFlow(SearchState.COLLAPSED)

    private val imeVisible = _imeVisible.asStateFlow()
    private val userInteracting = _userInteracting.asStateFlow()
    private val state = _state.asStateFlow()

    private var imeBottomInset = 0
    private var lastTranslationY: Float = 0f
    private val animationDuration = 180L

    private var observeJob: Job? = null
    private var interactJob: Job? = null
    private val scope = MainScope()

    fun bind() {
        setupInsetsListener()
        setupTouchListener()
        startFlow()
    }

    fun dispose() {
        endFlow()
        scope.cancel()
        searchContainer.translationY = 0f
        editText.setOnTouchListener(null)
        searchContainer.animate().cancel()
    }

    private fun setupInsetsListener() {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            _imeVisible.value = insets.isVisible(WindowInsetsCompat.Type.ime())
            imeBottomInset = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            insets
        }
    }

    private fun setupTouchListener() {
        editText.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    interactJob?.cancel()
                    _userInteracting.value = true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { 
                    interactJob?.cancel()
                    interactJob = scope.launch {
                        delay(1250L)
                        _userInteracting.value = false
                    }
                }
            }
            false
        }
    }

    private fun startFlow() {
        if (observeJob?.isActive == true) return
        observeJob = scope.launch {
            combine(
                imeVisible,
                userInteracting,
                state
            ) { imeVisible, userInteracting, state ->
                Triple(imeVisible, userInteracting, state)
            }.distinctUntilChanged()
             .collect { (imeVisible, userInteracting, state) ->
                if (imeVisible && state == SearchState.COLLAPSED) {
                    expandSearch()
                } else if (!imeVisible && !userInteracting && state == SearchState.EXPANDED) {
                    collapseSearch()
                }
            }
        }
    }

    private fun endFlow() {
        observeJob?.cancel()
        observeJob = null
    }

    private fun expandSearch() {
        if (_state.value == SearchState.EXPANDED) return
        val location = IntArray(2)
        searchContainer.getLocationOnScreen(location)
        val containerBottom = location[1] + searchContainer.height
        val screenHeight = rootView.height
        val keyboardTop = screenHeight - imeBottomInset
        val neededOffset = max(0, containerBottom - keyboardTop)
        val targetTranslationY = -neededOffset.toFloat()
        if (abs(lastTranslationY - targetTranslationY) > 2f) {
            animateTranslation(targetTranslationY)
        }
        searchContainer.expanded = true
        _state.value = SearchState.EXPANDED
    }

    private fun collapseSearch() {
        if (_state.value == SearchState.COLLAPSED) return
        animateTranslation(0f)
        searchContainer.expanded = false
        _state.value = SearchState.COLLAPSED
    }

    private fun animateTranslation(targetY: Float) {
        searchContainer.animate().cancel()
        searchContainer.animate()
            .translationY(targetY)
            .setDuration(animationDuration)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                lastTranslationY = targetY
            }
            .start()
    }
}
