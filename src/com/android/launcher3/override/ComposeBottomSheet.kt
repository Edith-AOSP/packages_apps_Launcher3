/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.launcher3.override

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.android.compose.theme.PlatformTheme
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Launcher
import com.android.launcher3.views.AbstractSlideInView
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.BaseDragLayer

class ComposeBottomSheet<T>(context: Context) :
    AbstractSlideInView<T>(context, null, 0)
    where T : Context, T : ActivityContext {

    private val container = ComposeView(context)
    private var imeShift = 0f
    private var _hintCloseProgress = mutableFloatStateOf(0f)
    private var hintCloseDistance = 0f
    val hintCloseProgress: Float get() = _hintCloseProgress.floatValue

    init {
        layoutParams = BaseDragLayer.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            .apply { ignoreInsets = true }
        gravity = Gravity.BOTTOM
        mContent = LinearLayout(context).apply { addView(container) }
    }

    fun show() {
        val parent = parent
        if (parent is ViewGroup) {
            parent.removeView(this)
        }
        removeAllViews()
        addView(mContent)
        attachToContainer()
        animateOpen()
    }

    fun setContent(
        contentPaddings: PaddingValues = PaddingValues(all = 0.dp),
        content: @Composable ComposeBottomSheet<T>.() -> Unit,
    ) {
        container.setContent {
            PlatformTheme {
                ContentWrapper(contentPaddings = contentPaddings) {
                    content(this)
                }
            }
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        setTranslationShift(mTranslationShift)
    }

    private fun animateOpen() {
        if (mIsOpen || mOpenCloseAnimation.animationPlayer.isRunning) {
            return
        }
        mIsOpen = true
        setUpDefaultOpenAnimation().start()
    }

    override fun handleClose(animate: Boolean) {
        if (mActivityContext is Launcher) {
            mActivityContext.hideKeyboard()
        }
        handleClose(animate, DEFAULT_CLOSE_DURATION)
    }

    override fun onCloseComplete() {
        super.onCloseComplete()
    }

    override fun isOfType(type: Int): Boolean {
        return type and AbstractFloatingView.TYPE_COMPOSE_SHEET != 0
    }

    override fun onUserSwipeToDismissProgressChanged() {
        // Skip default scale animation — Compose handles rendering internally.
    }

    override fun getScrimColor(context: Context): Int {
        val ta = context.obtainStyledAttributes(
            intArrayOf(android.R.attr.colorBackground),
        )
        val surfaceColor = ta.getColor(0, 0xFF000000.toInt())
        ta.recycle()
        return (surfaceColor and 0x00FFFFFF) or 0x99000000.toInt()
    }

    override fun setTranslationShift(translationShift: Float) {
        mTranslationShift = translationShift
        updateContentShift()
        if (mColorScrim != null) {
            mColorScrim.alpha = 1 - mTranslationShift
        }
    }

    private fun updateContentShift() {
        mContent.translationY = mTranslationShift * mContent.height + imeShift
    }

    private fun setImeShift(shift: Float) {
        imeShift = shift
        updateContentShift()
    }

    @Composable
    private fun ContentWrapper(
        contentPaddings: PaddingValues = PaddingValues(all = 0.dp),
        content: @Composable ComposeBottomSheet<T>.() -> Unit,
    ) {
        val imePaddings = WindowInsets.ime
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()

        val imeTranslation = with(LocalDensity.current) {
            -(imePaddings.calculateBottomPadding().toPx())
        }

        SideEffect { setImeShift(imeTranslation) }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = androidx.compose.ui.Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                shape = backgroundShape,
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Column {
                    // Drag handle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Spacer(
                            modifier = Modifier
                                .width(32.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                        .copy(alpha = 0.4f),
                                ),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .padding(contentPaddings)
                            .graphicsLayer(
                                alpha = 1f - (hintCloseProgress * 0.5f),
                                translationY = hintCloseProgress * -hintCloseDistance,
                            ),
                    ) {
                        content(this@ComposeBottomSheet)
                    }
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_CLOSE_DURATION = 200L
        val backgroundShape = RoundedCornerShape(24.dp, 24.dp, 0.dp, 0.dp)

        fun <T> show(
            context: T,
            contentPaddings: PaddingValues = PaddingValues(all = 0.dp),
            content: @Composable ComposeBottomSheet<T>.() -> Unit,
        ) where T : Context, T : ActivityContext {
            AbstractFloatingView.closeAllOpenViews(context)
            val view = ComposeBottomSheet<T>(context)
            view.setContent(contentPaddings, content)
            view.show()
        }
    }
}
