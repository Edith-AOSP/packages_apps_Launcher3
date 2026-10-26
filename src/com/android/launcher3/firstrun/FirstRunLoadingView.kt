/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.launcher3.firstrun

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import android.view.WindowInsetsController
import android.widget.FrameLayout
import androidx.annotation.Keep
import com.airbnb.lottie.LottieAnimationView
import com.android.launcher3.R
import com.android.launcher3.util.Themes

/**
 * Full-screen overlay shown while the workspace binds on the first cold start of the launcher
 * process (reboot, force-stop, process death). Warm starts in the same process do not show it.
 * Fades out once the first bind completes, or after [FAILSAFE_MS] if it never does.
 */
@Keep
class FirstRunLoadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val animationView: LottieAnimationView
    private val shownAt = SystemClock.uptimeMillis()

    /** System-bar appearance the launcher had before this overlay took over. */
    private val previousLightBars = Themes.getAttrBoolean(context, R.attr.isWorkspaceDarkText)

    private var dismissed = false

    /** True only while this overlay is actually covering the launcher. */
    private var overlayActive = false

    init {
        inflate(context, R.layout.first_run_loading, this)
        animationView = findViewById(R.id.first_run_loading_animation)
        isClickable = true
        isFocusable = true

        if (shownThisProcess) {
            visibility = GONE
        } else {
            shownThisProcess = true
            overlayActive = true
            postDelayed(::dismiss, FAILSAFE_MS)
        }
    }

    /** Starts the (minimum-duration) dismissal once the workspace has finished binding. */
    fun onWorkspaceReady() {
        if (!overlayActive || dismissed) return
        val elapsed = SystemClock.uptimeMillis() - shownAt
        postDelayed(::dismiss, (MIN_SHOW_MS - elapsed).coerceAtLeast(0L))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (overlayActive && !dismissed) setLightSystemBars(false)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (!overlayActive || dismissed) return
        if (visibility == VISIBLE) {
            setLightSystemBars(false)
            animationView.playAnimation()
        } else {
            animationView.pauseAnimation()
        }
    }

    private fun dismiss() {
        if (!overlayActive || dismissed) return
        dismissed = true
        overlayActive = false
        setLightSystemBars(previousLightBars)
        animate()
            .alpha(0f)
            .setDuration(FADE_OUT_MS)
            .withEndAction { visibility = View.GONE }
    }

    private fun setLightSystemBars(light: Boolean) {
        val window = (context as? Activity)?.window ?: return
        // The decor view does not exist yet while the launcher layout is being inflated.
        val decor = window.peekDecorView() ?: return
        val controller = decor.windowInsetsController ?: return
        val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        controller.setSystemBarsAppearance(if (light) mask else 0, mask)
    }

    private companion object {
        /** Set once per launcher process; a reboot or force-stop clears it. */
        var shownThisProcess = false
        const val MIN_SHOW_MS = 250L
        const val FAILSAFE_MS = 8000L
        const val FADE_OUT_MS = 300L
    }
}
