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
package com.android.launcher3.allapps.search

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.text.Selection
import android.text.SpannableStringBuilder
import android.text.method.TextKeyListener
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.View.OnTouchListener
import android.view.ViewGroup.MarginLayoutParams
import android.widget.ImageButton
import android.widget.RelativeLayout
import com.android.launcher3.DeviceProfile
import com.android.launcher3.ExtendedEditText
import com.android.launcher3.Insettable
import com.android.launcher3.R
import com.android.launcher3.allapps.ActivityAllAppsContainerView
import com.android.launcher3.allapps.AllAppsStore
import com.android.launcher3.allapps.BaseAllAppsAdapter.AdapterItem
import com.android.launcher3.allapps.SearchUiManager
import com.android.launcher3.search.SearchCallback
import com.android.launcher3.util.ApiWrapper
import com.android.launcher3.views.ActivityContext
import kotlin.math.roundToInt

class NTAppsSearchContainerLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr),
    SearchUiManager,
    SearchCallback<AdapterItem>,
    AllAppsStore.OnUpdateListener,
    Insettable {

    private val mLauncher: ActivityContext = ActivityContext.lookupContext(context)
    private val mSearchBarController = AllAppsSearchBarController()
    private val mSearchQueryBuilder = SpannableStringBuilder()
    private val mContentOverlap: Int =
        resources.getDimensionPixelSize(R.dimen.all_apps_search_bar_content_overlap)

    private var mAppsView: ActivityAllAppsContainerView<*>? = null

    private lateinit var searchEditText: ExtendedEditText
    private lateinit var searchButton: ImageButton
    private lateinit var menuButton: ImageButton
    private lateinit var rippleView: View

    private var expansionFraction = 0f

    var expanded: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    override fun onFinishInflate() {
        super.onFinishInflate()
        searchEditText = requireViewById(R.id.text_all_apps_search_search)
        searchButton = requireViewById(R.id.btn_all_apps_search_search)
        menuButton = requireViewById(R.id.btn_all_apps_search_menu)
        rippleView = requireViewById(R.id.search_bar_ripple_view)
        Selection.setSelection(mSearchQueryBuilder, 0)
        menuButton.setOnClickListener {
            try {
                context.startActivity(
                    Intent(context, com.android.launcher3.settings.SettingsActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        expanded = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        mAppsView?.appsStore?.addUpdateListener(this)
        setOnTouchListener(touchInterceptor)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mAppsView?.appsStore?.removeUpdateListener(this)
        setOnTouchListener(null)
    }

    private val touchInterceptor = object : OnTouchListener {

        private var isInsideMenu = false
        private var downX = 0f
        private var downY = 0f
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        override fun onTouch(v: View?, event: MotionEvent): Boolean {
            val containerLocation = IntArray(2)
            getLocationOnScreen(containerLocation)
            val menuLocation = IntArray(2)
            menuButton.getLocationOnScreen(menuLocation)
            val menuRect = Rect(
                menuLocation[0],
                menuLocation[1],
                menuLocation[0] + menuButton.width,
                menuLocation[1] + menuButton.height
            )
            val screenX = containerLocation[0] + event.x.toInt()
            val screenY = containerLocation[1] + event.y.toInt()

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!expanded && menuRect.contains(screenX, screenY)) {
                        isInsideMenu = true
                        downX = event.x
                        downY = event.y
                        return true
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isInsideMenu) {
                        val dx = event.x - downX
                        val dy = event.y - downY
                        if (dx * dx + dy * dy > touchSlop * touchSlop) {
                            isInsideMenu = false
                        }
                        return isInsideMenu
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (isInsideMenu) {
                        isInsideMenu = false
                        menuButton.isPressed = false
                        if (menuRect.contains(screenX, screenY)) {
                            menuButton.performClick()
                            return true
                        }
                    }
                }

                MotionEvent.ACTION_CANCEL -> {
                    if (isInsideMenu) {
                        isInsideMenu = false
                        menuButton.isPressed = false
                    }
                }
            }
            return false
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (!expanded) {
            val requestedWidth = View.MeasureSpec.getSize(widthMeasureSpec)
            val recyclerView = mAppsView?.activeRecyclerView
            val rowWidth = requestedWidth -
                (recyclerView?.paddingLeft ?: 0) -
                (recyclerView?.paddingRight ?: 0)
            super.onMeasure(
                View.MeasureSpec.makeMeasureSpec(rowWidth, View.MeasureSpec.EXACTLY),
                heightMeasureSpec
            )
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (!expanded) {
            val padding = resources.getDimensionPixelSize(R.dimen.nt_all_apps_search_container_padding_left)
            val containerWidth = right - left
            searchButton.translationX = (padding - searchButton.left).toFloat()
            val menuTargetX = containerWidth - padding - menuButton.width
            menuButton.translationX = (menuTargetX - menuButton.left).toFloat()
            val editTextLeft = searchButton.left + searchButton.translationX + searchButton.width
            val editTextRight = menuButton.left + menuButton.translationX
            val editTextWidth = (editTextRight - editTextLeft).toInt()
            if (editTextWidth > 0) {
                searchEditText.layout(
                    editTextLeft.toInt(),
                    searchEditText.top,
                    editTextRight.toInt(),
                    searchEditText.bottom
                )
            }
            searchEditText.translationX = 0f
            val parentView = parent as? View ?: return
            val availableWidth = parentView.width - parentView.paddingLeft - parentView.paddingRight
            val expectedLeft = parentView.paddingLeft + (availableWidth - containerWidth) / 2
            val shift = (expectedLeft - left).toFloat()
            translationX = shift
        } else {
            translationX = 0f
            searchButton.translationX = 0f
            menuButton.translationX = 0f
            searchEditText.translationX = 0f
        }
        offsetTopAndBottom(mContentOverlap)
    }

    override fun initializeSearch(appsView: ActivityAllAppsContainerView<*>) {
        mAppsView = appsView
        mSearchBarController.initialize(
            DefaultAppSearchAlgorithm(context, true),
            searchEditText,
            mLauncher,
            this
        )
    }

    override fun onAppsUpdated() {
        mSearchBarController.refreshSearchResult()
    }

    override fun resetSearch() {
        mSearchBarController.reset()
    }

    override fun focusSearchField() {
        mSearchBarController.focusSearchField()
    }

    override fun preDispatchKeyEvent(event: KeyEvent) {
        if (!mSearchBarController.isSearchFieldFocused() &&
            event.action == KeyEvent.ACTION_DOWN
        ) {
            val unicodeChar = event.unicodeChar
            val isKeyNotWhitespace =
                unicodeChar > 0 &&
                    !Character.isWhitespace(unicodeChar) &&
                    !Character.isSpaceChar(unicodeChar)
            if (isKeyNotWhitespace) {
                val gotKey = TextKeyListener.getInstance()
                    .onKeyDown(searchEditText, mSearchQueryBuilder, event.keyCode, event)
                if (gotKey && mSearchQueryBuilder.isNotEmpty()) {
                    mSearchBarController.focusSearchField()
                }
            }
        }
    }

    override fun onSearchResult(query: String, items: ArrayList<AdapterItem>?) {
        if (query.equals(context.getString(R.string.private_space_label), ignoreCase = true)) {
            privateSpaceQuery()
            return
        }
        if (items != null) {
            mAppsView?.setSearchResults(items)
        }
    }

    override fun clearSearchResult() {
        mSearchQueryBuilder.clear()
        mSearchQueryBuilder.clearSpans()
        Selection.setSelection(mSearchQueryBuilder, 0)
        mAppsView?.onClearSearchResult()
    }

    override fun setInsets(insets: Rect) {
        val mlp = layoutParams as MarginLayoutParams
        mlp.topMargin = insets.top
        requestLayout()
    }

    override fun getEditText(): ExtendedEditText {
        return searchEditText
    }

    private fun privateSpaceQuery() {
        val privateProfileManager = mAppsView?.privateProfileManager ?: return
        if (privateProfileManager.isPrivateSpaceHidden) {
            privateProfileManager.setQuietMode(false)
        } else if (mAppsView?.hasPrivateProfile() == false) {
            val privateSpaceSettingsIntent: Intent? =
                ApiWrapper.INSTANCE.get(context).privateSpaceSettingsIntent
            if (privateSpaceSettingsIntent != null) {
                mLauncher.startActivitySafely(mAppsView, privateSpaceSettingsIntent, null)
            }
        }
    }
}
