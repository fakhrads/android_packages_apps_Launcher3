
/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.quickstep.inputconsumers;

import static android.app.contextualsearch.ContextualSearchManager.ENTRYPOINT_LONG_PRESS_NAV_HANDLE;
import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_POINTER_DOWN;
import static android.view.MotionEvent.ACTION_POINTER_UP;
import static android.view.MotionEvent.ACTION_UP;

import static com.android.internal.app.AssistUtils.INVOCATION_TYPE_GESTURE;
import static com.android.internal.app.AssistUtils.INVOCATION_TYPE_KEY;
import static com.android.launcher3.Utilities.squaredHypot;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.PointF;
import android.hardware.input.InputManager;
import android.hardware.input.InputManagerGlobal;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.android.app.animation.Interpolators;
import com.android.internal.app.AssistUtils;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.window.WindowManagerProxy;
import com.android.quickstep.BaseContainerInterface;
import com.android.quickstep.GestureState;
import com.android.quickstep.InputConsumer;
import com.android.quickstep.RecentsAnimationDeviceState;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.util.ContextualSearchInvoker;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.systemui.shared.system.InputMonitorCompat;

import java.util.function.Consumer;

/**
 * Touch consumer for handling events to launch assistant from launcher
 */
public class AssistantInputConsumer extends DelegateInputConsumer {

    private static final String TAG = "AssistantInputConsumer";
    private static final long RETRACT_ANIMATION_DURATION_MS = 300;

    // From //java/com/google/android/apps/gsa/search/shared/util/OpaContract.java.
    private static final String OPA_BUNDLE_TRIGGER = "triggered_by";
    // From //java/com/google/android/apps/gsa/assistant/shared/proto/opa_trigger.proto.
    private static final int OPA_BUNDLE_TRIGGER_DIAG_SWIPE_GESTURE = 83;

    private final PointF mDownPos = new PointF();
    private final PointF mLastPos = new PointF();
    private final PointF mStartDragPos = new PointF();

    private int mActivePointerId = -1;
    private boolean mPassedSlop;
    private boolean mLaunchedAssistant;
    private float mDistance;
    private float mTimeFraction;
    private long mDragTime;
    private float mLastProgress;
    private BaseContainerInterface mContainerInterface;

    private final float mDragDistThreshold;
    private final float mFlingDistThreshold;
    private final long mTimeThreshold;
    private final int mAngleThreshold;
    private final float mSquaredSlop;
    private final Context mContext;
    private final Consumer<MotionEvent> mGestureDetector;
    private final boolean mCornerSwipeCts;
    private final boolean mCornerSwipeGemini;
    // Resolved when the swipe starts, so completing the gesture does not pay to resolve it.
    private ContextualSearchInvoker mCtsInvoker;

    public AssistantInputConsumer(
            Context context,
            GestureState gestureState,
            InputConsumer delegate,
            InputMonitorCompat inputMonitor,
            RecentsAnimationDeviceState deviceState,
            MotionEvent startEvent) {
        super(gestureState.getDisplayId(), delegate, inputMonitor);
        final Resources res = context.getResources();
        mContext = context;
        final String cornerSwipeAction = LauncherPrefs.CORNER_SWIPE_ACTION.get(context);
        mCornerSwipeCts = "cts".equals(cornerSwipeAction);
        mCornerSwipeGemini = "gemini".equals(cornerSwipeAction);
        mDragDistThreshold = res.getDimension(R.dimen.gestures_assistant_drag_threshold);
        mFlingDistThreshold = res.getDimension(R.dimen.gestures_assistant_fling_threshold);
        mTimeThreshold = res.getInteger(R.integer.assistant_gesture_min_time_threshold);
        mAngleThreshold = res.getInteger(R.integer.assistant_gesture_corner_deg_threshold);

        float slop = ViewConfiguration.get(context).getScaledTouchSlop();

        mSquaredSlop = slop * slop;
        mContainerInterface = gestureState.getContainerInterface();

        boolean flingDisabled = deviceState.isAssistantGestureIsConstrained()
                || deviceState.isInDeferredGestureRegion(startEvent);
        mGestureDetector = flingDisabled
                ? ev -> { }
                : new GestureDetector(context, new AssistantGestureListener())::onTouchEvent;
    }

    @Override
    public int getType() {
        return TYPE_ASSISTANT | mDelegate.getType();
    }

    @Override
    public void onMotionEvent(MotionEvent ev) {
        // TODO add logging
        switch (ev.getActionMasked()) {
            case ACTION_DOWN: {
                mActivePointerId = ev.getPointerId(0);
                mDownPos.set(ev.getX(), ev.getY());
                mLastPos.set(mDownPos);
                mTimeFraction = 0;
                if (mCornerSwipeCts) {
                    // Warm the invoker while the finger is still travelling instead of on the
                    // frame the gesture completes.
                    mCtsInvoker = ContextualSearchInvoker.cached(mContext);
                }
                break;
            }
            case ACTION_POINTER_DOWN: {
                if (mState != STATE_ACTIVE) {
                    mState = STATE_DELEGATE_ACTIVE;
                }
                break;
            }
            case ACTION_POINTER_UP: {
                int ptrIdx = ev.getActionIndex();
                int ptrId = ev.getPointerId(ptrIdx);
                if (ptrId == mActivePointerId) {
                    final int newPointerIdx = ptrIdx == 0 ? 1 : 0;
                    mDownPos.set(
                        ev.getX(newPointerIdx) - (mLastPos.x - mDownPos.x),
                        ev.getY(newPointerIdx) - (mLastPos.y - mDownPos.y));
                    mLastPos.set(ev.getX(newPointerIdx), ev.getY(newPointerIdx));
                    mActivePointerId = ev.getPointerId(newPointerIdx);
                }
                break;
            }
            case ACTION_MOVE: {
                if (mState == STATE_DELEGATE_ACTIVE) {
                    break;
                }
                if (!mDelegate.allowInterceptByParent()) {
                    mState = STATE_DELEGATE_ACTIVE;
                    break;
                }
                int pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex == -1) {
                    break;
                }
                mLastPos.set(ev.getX(pointerIndex), ev.getY(pointerIndex));

                if (!mPassedSlop) {
                    // Normal gesture, ensure we pass the slop before we start tracking the gesture
                    if (squaredHypot(mLastPos.x - mDownPos.x, mLastPos.y - mDownPos.y)
                            > mSquaredSlop) {

                        mPassedSlop = true;
                        mStartDragPos.set(mLastPos.x, mLastPos.y);
                        mDragTime = SystemClock.uptimeMillis();

                        if (isValidAssistantGestureAngle(
                            mDownPos.x - mLastPos.x, mDownPos.y - mLastPos.y)) {
                            setActive(ev);
                        } else {
                            mState = STATE_DELEGATE_ACTIVE;
                        }
                    }
                } else {
                    // Movement
                    mDistance = (float) Math.hypot(mLastPos.x - mStartDragPos.x,
                        mLastPos.y - mStartDragPos.y);
                    if (mDistance >= 0) {
                        final long diff = SystemClock.uptimeMillis() - mDragTime;
                        mTimeFraction = Math.min(diff * 1f / mTimeThreshold, 1);
                        updateAssistantProgress();
                    }
                }
                break;
            }
            case ACTION_CANCEL:
            case ACTION_UP:
                if (mState != STATE_DELEGATE_ACTIVE && !mLaunchedAssistant) {
                    ValueAnimator animator = ValueAnimator.ofFloat(mLastProgress, 0)
                        .setDuration(RETRACT_ANIMATION_DURATION_MS);
                    animator.addUpdateListener(valueAnimator -> {
                        float progress = (float) valueAnimator.getAnimatedValue();
                        SystemUiProxy.INSTANCE.get(mContext).onAssistantProgress(progress);
                    });
                    // Ensure that we always send a zero at the end to clear the invocation state.
                    animator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            SystemUiProxy.INSTANCE.get(mContext).onAssistantProgress(0f);
                        }
                    });
                    animator.setInterpolator(Interpolators.DECELERATE_2);
                    animator.start();
                }
                mPassedSlop = false;
                mState = STATE_INACTIVE;
                break;
        }

        mGestureDetector.accept(ev);

        if (mState != STATE_ACTIVE) {
            mDelegate.onMotionEvent(ev);
        }
    }

    private void updateAssistantProgress() {
        if (!mLaunchedAssistant) {
            mLastProgress = Math.min(mDistance * 1f / mDragDistThreshold, 1) * mTimeFraction;
            // The assistant needs the hold: its invocation animation is driven by the progress built
            // over mTimeThreshold. Circle to Search does not, it is a surface that takes about 240ms
            // to display on onyx by itself, so waiting out the remaining hold on top of that is
            // exactly the lag the swipe shows. Once the finger has travelled the drag threshold the
            // swipe has committed, so hand it over then. The Gemini app is a plain app launch with
            // no invocation animation either.
            if (mDistance >= mDragDistThreshold
                    && (mCornerSwipeCts || mCornerSwipeGemini || mTimeFraction >= 1)) {
                completeGesture(0);
            } else {
                SystemUiProxy.INSTANCE.get(mContext).onAssistantProgress(mLastProgress);
            }
        }
    }

    private ContextualSearchInvoker contextualSearchInvoker() {
        if (mCtsInvoker == null) {
            mCtsInvoker = ContextualSearchInvoker.cached(mContext);
        }
        return mCtsInvoker;
    }

    private void completeGesture(float velocity) {
        // Corner swipe opens Circle to Search; fall back to the assistant when it is unavailable.
        if (mCornerSwipeCts
                && contextualSearchInvoker().show(ENTRYPOINT_LONG_PRESS_NAV_HANDLE)) {
            // Clear the assistant invocation lights started by the swipe progress.
            SystemUiProxy.INSTANCE.get(mContext).onAssistantProgress(0f);
            performGestureEndHaptic();
            mLaunchedAssistant = true;
            return;
        }
        // The Gemini app is not what the assistant setting names: that is the Google app, whose
        // assist activity shows the Gemini overlay. Fall through to the picked assistant when the
        // app is not there.
        if (mCornerSwipeGemini && startGeminiApp()) {
            SystemUiProxy.INSTANCE.get(mContext).onAssistantProgress(0f);
            performGestureEndHaptic();
            mLaunchedAssistant = true;
            return;
        }
        // The swipe promises the assistant picked in Settings > Default apps, so start that one
        // directly. Asking SystemUI to is what used to end in nothing at all: it drops the gesture
        // invocation when the picked assistant does not declare support for it, and it stands down
        // for the home long press as soon as Contextual Search exists (see
        // SystemUiProxy.setAssistantOverridesRequested), so the injected key event below lands
        // nowhere on a device that has Contextual Search.
        if (startConfiguredAssistant()) {
            // Clear the assistant invocation lights started by the swipe progress.
            SystemUiProxy.INSTANCE.get(mContext).onAssistantProgress(0f);
            performGestureEndHaptic();
            mLaunchedAssistant = true;
            return;
        }
        SystemUiProxy.INSTANCE.get(mContext).onAssistantGestureCompletion(velocity);
        startAssistantInternal();
    }

    /**
     * Opens the standalone Gemini app through its launcher activity.
     *
     * @return whether the app was found and started
     */
    private boolean startGeminiApp() {
        final Intent intent = mContext.getPackageManager()
                .getLaunchIntentForPackage(Utilities.GEMINI_PACKAGE);
        if (intent == null) {
            Log.w(TAG, "Gemini app " + Utilities.GEMINI_PACKAGE + " has no launcher activity");
            return false;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            mContext.startActivity(intent);
            Log.i(TAG, "Started the Gemini app via " + intent);
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot start the Gemini app", e);
            return false;
        }
    }

    /**
     * Starts the assistant named by the assistant setting, the way the framework itself does when
     * the assistant has no voice session to show.
     *
     * @return whether an assist activity was started
     */
    private boolean startConfiguredAssistant() {
        final ComponentName assistant = new AssistUtils(mContext)
                .getAssistComponentForUser(UserHandle.myUserId());
        if (assistant == null) {
            return false;
        }
        final PackageManager pm = mContext.getPackageManager();
        // The assistant setting normally names the assistant's VoiceInteractionService, not an
        // activity, so starting that component explicitly resolves to nothing and the gesture used to
        // end in silence for anything that is not Circle to Search. Try the component as an activity
        // first, then resolve ACTION_ASSIST the way the framework does, which lands on the assist
        // activity the picked app declares.
        final Intent explicit = newAssistIntent().setComponent(assistant);
        Intent intent = pm.resolveActivity(explicit, 0) != null ? explicit : null;
        if (intent == null) {
            final Intent implicit = newAssistIntent()
                    .putExtra(Intent.EXTRA_ASSIST_PACKAGE, assistant.getPackageName());
            if (pm.resolveActivity(implicit, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                intent = implicit;
            }
        }
        if (intent == null) {
            Log.w(TAG, "No assist activity in the configured assistant "
                    + assistant.flattenToShortString());
            return false;
        }
        try {
            mContext.startActivity(intent);
            Log.i(TAG, "Started the configured assistant "
                    + assistant.flattenToShortString() + " via " + intent);
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot start the configured assistant "
                    + assistant.flattenToShortString(), e);
            return false;
        }
    }

    private static Intent newAssistIntent() {
        return new Intent(Intent.ACTION_ASSIST)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(INVOCATION_TYPE_KEY, INVOCATION_TYPE_GESTURE);
    }

    private void startAssistantInternal() {
        sendEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME, KeyEvent.FLAG_LONG_SWIPE);
        sendEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HOME, KeyEvent.FLAG_LONG_SWIPE);
        performGestureEndHaptic();
        mLaunchedAssistant = true;
    }

    private void performGestureEndHaptic() {
        RecentsViewContainer container = mContainerInterface.getCreatedContainer();
        if (container != null) {
            container.getRootView().performHapticFeedback(
                13, // HapticFeedbackConstants.GESTURE_END
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
        }
    }

    /**
     * Determine if angle is larger than threshold for assistant detection
     */
    private boolean isValidAssistantGestureAngle(float deltaX, float deltaY) {
        float angle = (float) Math.toDegrees(Math.atan2(deltaY, deltaX));

        // normalize so that angle is measured clockwise from horizontal in the bottom right corner
        // and counterclockwise from horizontal in the bottom left corner
        angle = angle > 90 ? 180 - angle : angle;
        // `angle < 90` rejected a straight up swipe from the corner: atan2 gives exactly 90 when the
        // finger does not drift sideways, so the gesture that users actually make fell through to
        // the home consumer. Accept vertical; the region check above already keeps this gesture
        // inside the 48dp corner areas.
        return (angle > mAngleThreshold && angle <= 90);
    }

    private boolean sendEvent(int action, int code, int flags) {
        long when = SystemClock.uptimeMillis();
        final KeyEvent ev = new KeyEvent(when, when, action, code, 0 /* repeat */,
                0 /* metaState */, KeyCharacterMap.VIRTUAL_KEYBOARD, 0 /* scancode */,
                flags | KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                InputDevice.SOURCE_KEYBOARD);

        int displayId = WindowManagerProxy.INSTANCE.get(mContext).getDisplayId(mContext);
        ev.setDisplayId(displayId);
        return InputManagerGlobal.getInstance()
                .injectInputEvent(ev, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    private class AssistantGestureListener extends SimpleOnGestureListener {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (isValidAssistantGestureAngle(velocityX, -velocityY)
                    && mDistance >= mFlingDistThreshold
                    && !mLaunchedAssistant
                    && mState != STATE_DELEGATE_ACTIVE) {
                mLastProgress = 1;
                completeGesture((float) Math.sqrt(velocityX * velocityX + velocityY * velocityY));
            }
            return true;
        }
    }

    @Override
    protected String getDelegatorName() {
        return "AssistantInputConsumer";
    }
}
