/*
 * Copyright 2018 stfalcon.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.stfalcon.imageviewer.common.gestures.dismiss

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.animation.Interpolator
import com.stfalcon.imageviewer.common.extensions.hitRect
import com.stfalcon.imageviewer.common.extensions.setAnimatorListener
import kotlin.math.abs

internal class SwipeToDismissHandler(
    private val animationDuration: Long,
    private val swipeRatio: Float,
    private val swipeInterpolator: Interpolator?,
    private val swipeView: View,
    private val onDismiss: () -> Unit,
    private val onSwipeViewMove: (translationY: Float, translationLimit: Int) -> Unit,
    private val shouldAnimateDismiss: () -> Boolean
) : View.OnTouchListener {

    private var tracker: VelocityTracker? = null
    private val velocityScale = 100
    private val velocityThreshold = 800
    private var translationLimit: Int = (swipeView.height * swipeRatio).toInt()
    private var isTracking = false
    private var startY: Float = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (null == tracker) tracker = VelocityTracker.obtain() else tracker?.clear()
                tracker?.addMovement(event)

                if (swipeView.hitRect.contains(event.x.toInt(), event.y.toInt())) {
                    isTracking = true
                }
                startY = event.y
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isTracking) {
                    isTracking = false
                    tracker?.addMovement(event)
                    tracker?.computeCurrentVelocity(velocityScale, Float.MAX_VALUE)
                    val velocity: Float = tracker?.yVelocity ?: 1f
                    if (null != tracker) {
                        tracker?.recycle()
                        tracker = null
                    }

                    onTrackingEnd(v.height, velocity)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(event)
                if (isTracking) {
                    val translationY = event.y - startY
                    swipeView.translationY = translationY
                    onSwipeViewMove(translationY, translationLimit)
                }
                return true
            }
            else -> {
                return false
            }
        }
    }

    internal fun initiateDismissToBottom() {
        animateTranslation(swipeView.height.toFloat())
    }

    private fun onTrackingEnd(parentHeight: Int, velocity: Float) {
        val animateTo = when {
            swipeView.translationY < -translationLimit -> -parentHeight.toFloat()
            swipeView.translationY > translationLimit -> parentHeight.toFloat()
            else -> 0f
        }
        if (animateTo != 0f && !shouldAnimateDismiss() && abs(velocity) < velocityThreshold) {
            onDismiss()
        } else {
            animateTranslation(animateTo)
        }
    }

    private fun animateTranslation(translationTo: Float) {
        swipeView.animate()
            .translationY(translationTo)
            .setDuration(animationDuration)
            .setInterpolator(swipeInterpolator)
            .setUpdateListener { onSwipeViewMove(swipeView.translationY, translationLimit) }
            .setAnimatorListener(onAnimationEnd = {
                if (translationTo != 0f) {
                    onDismiss()
                }

                //remove the update listener, otherwise it will be saved on the next animation execution:
                swipeView.animate().setUpdateListener(null)
            })
            .start()
    }
}