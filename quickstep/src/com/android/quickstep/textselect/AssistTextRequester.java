/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.quickstep.textselect;

import android.app.ActivityTaskManager;
import android.app.IAssistDataReceiver;
import android.app.assist.AssistStructure;
import android.app.assist.AssistStructure.ViewNode;
import android.app.assist.AssistStructure.WindowNode;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.launcher3.util.Executors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Asks the system for the view structure of a task and turns the text in it into words with
 * positions on the task snapshot. The text comes from the app itself, so it is exact and nothing
 * leaves the device. It is empty for what an app draws itself (images, games, canvas based UI) and
 * for apps that opt out of assist data.
 */
public final class AssistTextRequester {
    private static final String TAG = "AssistTextRequester";
    private static final String KEY_STRUCTURE = "structure";
    private static final long TIMEOUT_MS = 4000;
    /** The snapshot and the window may differ by this much in aspect before they are not matched. */
    private static final float MAX_ASPECT_DRIFT = 0.06f;
    private static final int MAX_WORDS = 4000;
    private static final int MAX_DEPTH = 64;

    /** Called on the main thread. {@code session} is null when there is nothing to select. */
    public interface Callback {
        void onResult(@Nullable SelectSession session);
    }

    private AssistTextRequester() {}

    public static void request(Context context, int taskId, Bitmap snapshot, Callback callback) {
        final Context appContext = context.getApplicationContext();
        final AtomicBoolean done = new AtomicBoolean(false);
        final Runnable fail = () -> {
            if (done.compareAndSet(false, true)) {
                Executors.MAIN_EXECUTOR.execute(() -> callback.onResult(null));
            }
        };
        Executors.MAIN_EXECUTOR.getHandler().postDelayed(() -> {
            if (!done.get()) {
                Log.w(TAG, "No assist structure for task " + taskId + " after " + TIMEOUT_MS + " ms");
            }
            fail.run();
        }, TIMEOUT_MS);

        Executors.UI_HELPER_EXECUTOR.execute(() -> {
            try {
                final IAssistDataReceiver receiver = new IAssistDataReceiver.Stub() {
                    @Override
                    public void onHandleAssistData(Bundle data) {
                        if (done.get()) {
                            return;
                        }
                        SelectSession session = null;
                        try {
                            session = buildSession(data, snapshot);
                        } catch (RuntimeException e) {
                            Log.e(TAG, "Reading the assist structure failed", e);
                        }
                        if (session == null) {
                            fail.run();
                            return;
                        }
                        final SelectSession result = session;
                        if (done.compareAndSet(false, true)) {
                            Executors.MAIN_EXECUTOR.execute(() -> callback.onResult(result));
                        }
                    }

                    @Override
                    public void onHandleAssistScreenshot(Bitmap screenshot) {}
                };
                final boolean accepted = ActivityTaskManager.getService()
                        .requestAssistDataForTask(receiver, taskId, appContext.getPackageName(),
                                appContext.getAttributionTag(), true /* fetchStructure */);
                if (!accepted) {
                    Log.w(TAG, "The system refused assist data for task " + taskId);
                    fail.run();
                }
            } catch (Exception e) {
                Log.e(TAG, "Requesting assist data failed for task " + taskId, e);
                fail.run();
            }
        });
    }

    @Nullable
    private static SelectSession buildSession(@Nullable Bundle data, Bitmap snapshot) {
        final AssistStructure structure = findStructure(data);
        if (structure == null) {
            Log.w(TAG, "The assist data carries no structure");
            return null;
        }
        // The window that covers the snapshot is the one to map onto it, the largest of them.
        WindowNode main = null;
        long mainArea = 0;
        for (int i = 0; i < structure.getWindowNodeCount(); i++) {
            final WindowNode window = structure.getWindowNodeAt(i);
            final long area = (long) window.getWidth() * window.getHeight();
            if (area > mainArea) {
                main = window;
                mainArea = area;
            }
        }
        if (main == null || main.getWidth() <= 0 || main.getHeight() <= 0) {
            return null;
        }
        final float scaleX = snapshot.getWidth() / (float) main.getWidth();
        final float scaleY = snapshot.getHeight() / (float) main.getHeight();
        if (Math.abs(scaleX - scaleY) > MAX_ASPECT_DRIFT * Math.max(scaleX, scaleY)) {
            // Most likely the snapshot was taken in another orientation than the window now is.
            Log.w(TAG, "Snapshot " + snapshot.getWidth() + "x" + snapshot.getHeight()
                    + " does not match window " + main.getWidth() + "x" + main.getHeight());
            return null;
        }

        final List<SelectSession.Word> words = new ArrayList<>();
        final int[] counters = new int[1]; // block counter
        for (int i = 0; i < structure.getWindowNodeCount(); i++) {
            final WindowNode window = structure.getWindowNodeAt(i);
            // Only the window the snapshot shows, pop-ups and the like are not on it
            if (window != main) {
                continue;
            }
            collect(window.getRootViewNode(), window.getLeft() - main.getLeft(),
                    window.getTop() - main.getTop(), scaleX, scaleY,
                    new float[] {snapshot.getWidth(), snapshot.getHeight()}, words, counters, 0);
        }
        Log.i(TAG, "Found " + words.size() + " words in " + counters[0] + " views");
        if (words.isEmpty()) {
            return null;
        }
        return new SelectSession(snapshot, words);
    }

    @Nullable
    private static AssistStructure findStructure(@Nullable Bundle data) {
        if (data == null) {
            return null;
        }
        try {
            final AssistStructure byKey = data.getParcelable(KEY_STRUCTURE, AssistStructure.class);
            if (byKey != null) {
                return byKey;
            }
            for (String key : data.keySet()) {
                final Object value = data.get(key);
                if (value instanceof AssistStructure) {
                    return (AssistStructure) value;
                }
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Reading the assist bundle failed", e);
        }
        return null;
    }

    private static void collect(@Nullable ViewNode node, int offsetX, int offsetY, float scaleX,
            float scaleY, float[] limit, List<SelectSession.Word> out, int[] counters, int depth) {
        if (node == null || depth > MAX_DEPTH || out.size() >= MAX_WORDS) {
            return;
        }
        if (node.getVisibility() != View.VISIBLE || node.getAlpha() == 0f) {
            return;
        }
        final int left = offsetX + node.getLeft();
        final int top = offsetY + node.getTop();
        final CharSequence text = node.getText();
        if (!TextUtils.isEmpty(text) && node.getWidth() > 0 && node.getHeight() > 0
                && !isSecret(node)) {
            addWords(node, text.toString(), left, top, scaleX, scaleY, limit[0], limit[1], out,
                    counters[0]++);
        }
        final int childOffsetX = left - node.getScrollX();
        final int childOffsetY = top - node.getScrollY();
        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChildAt(i), childOffsetX, childOffsetY, scaleX, scaleY, limit, out,
                    counters, depth + 1);
        }
    }

    private static boolean isSecret(ViewNode node) {
        final int type = node.getInputType();
        if ((type & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT) {
            final int variation = type & InputType.TYPE_MASK_VARIATION;
            return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
        }
        return (type & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER
                && (type & InputType.TYPE_MASK_VARIATION) == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
    }

    /**
     * Lays the words of one view out on its box. The app reports where each line starts and its
     * baseline, not where each word is, so a word is placed by measuring the text with the size the
     * app reported. It is exact for left aligned text and close for the rest, which is enough to
     * pick the word under a finger.
     */
    private static void addWords(ViewNode node, String text, int left, int top, float scaleX,
            float scaleY, float limitW, float limitH, List<SelectSession.Word> out, int block) {
        final float textSize = node.getTextSize() > 0 ? node.getTextSize() : node.getHeight() * 0.7f;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(textSize);
        final int style = node.getTextStyle();
        final boolean bold = (style & 1) != 0;
        final boolean italic = (style & 2) != 0;
        paint.setTypeface(Typeface.create(Typeface.DEFAULT,
                bold ? (italic ? Typeface.BOLD_ITALIC : Typeface.BOLD)
                        : (italic ? Typeface.ITALIC : Typeface.NORMAL)));

        // Line starts: the ones the app reported, else a greedy wrap on the width of the view
        final int[] reported = node.getTextLineCharOffsets();
        final int[] baselines = node.getTextLineBaselines();
        final List<int[]> lines = new ArrayList<>(); // {start, end, index among the app's lines}
        final boolean appLines = reported != null && reported.length > 0;
        if (appLines) {
            for (int i = 0; i < reported.length; i++) {
                final int start = Math.min(reported[i], text.length());
                final int end = i + 1 < reported.length
                        ? Math.min(reported[i + 1], text.length()) : text.length();
                if (end > start) {
                    lines.add(new int[] {start, end, i});
                }
            }
        } else {
            wrap(text, paint, node.getWidth(), lines);
        }
        if (lines.isEmpty()) {
            return;
        }
        final float lineHeight = appLines && baselines != null && baselines.length > 1
                ? Math.max(1f, baselines[1] - baselines[0])
                : Math.min(node.getHeight() / (float) lines.size(), textSize * 1.4f);
        final boolean hasBaselines = appLines && baselines != null && baselines.length > 0;
        final float firstTop = hasBaselines
                ? baselines[0] - textSize * 0.85f : (node.getHeight() - lineHeight * lines.size()) / 2f;

        for (int l = 0; l < lines.size(); l++) {
            final int start = lines.get(l)[0];
            final int end = lines.get(l)[1];
            final int slot = appLines ? lines.get(l)[2] : l;
            final float lineTop = top + (hasBaselines && slot < baselines.length
                    ? baselines[slot] - textSize * 0.85f : firstTop + slot * lineHeight);
            final float lineWidth = paint.measureText(text, start, end);
            // A line that fills the view is stretched onto it, which also fixes padding and
            // gravity for the common case of a text view that wraps its text
            final float fit = lineWidth > 0 && Math.abs(lineWidth - node.getWidth())
                    < 0.15f * node.getWidth() ? node.getWidth() / lineWidth : 1f;
            int i = start;
            while (i < end) {
                while (i < end && Character.isWhitespace(text.charAt(i))) {
                    i++;
                }
                if (i >= end) {
                    break;
                }
                int j = i;
                while (j < end && !Character.isWhitespace(text.charAt(j))) {
                    j++;
                }
                final float x0 = left + paint.measureText(text, start, i) * fit;
                final float x1 = left + paint.measureText(text, start, j) * fit;
                final RectF bounds = new RectF(x0 * scaleX, lineTop * scaleY, x1 * scaleX,
                        (lineTop + lineHeight) * scaleY);
                // Off the snapshot: the next page of a pager, rows scrolled out of a list
                if (bounds.right > 0 && bounds.left < limitW && bounds.bottom > 0
                        && bounds.top < limitH) {
                    out.add(new SelectSession.Word(text.substring(i, j), bounds, block,
                            block * 1000 + l));
                }
                i = j;
            }
        }
    }

    private static void wrap(String text, Paint paint, int width, List<int[]> lines) {
        int lineStart = 0;
        int lastBreak = -1;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == '\n') {
                lines.add(new int[] {lineStart, i, lines.size()});
                lineStart = i + 1;
                lastBreak = -1;
                continue;
            }
            if (Character.isWhitespace(c)) {
                lastBreak = i;
            }
            if (paint.measureText(text, lineStart, i + 1) > width && i > lineStart) {
                final int cut = lastBreak > lineStart ? lastBreak : i;
                lines.add(new int[] {lineStart, cut, lines.size()});
                lineStart = lastBreak > lineStart ? cut + 1 : cut;
                lastBreak = -1;
            }
        }
        if (lineStart < text.length()) {
            lines.add(new int[] {lineStart, text.length(), lines.size()});
        }
    }
}
