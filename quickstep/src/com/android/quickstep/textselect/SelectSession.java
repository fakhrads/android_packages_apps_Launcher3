/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.quickstep.textselect;

import android.graphics.Bitmap;
import android.graphics.RectF;

import java.util.List;

/**
 * What the Select screen shows: the frozen task snapshot and the words found on it. The launcher
 * runs the recents code and the Select activity in one process, so the data is handed over here
 * instead of through an intent, a bitmap of this size does not fit in one.
 */
public final class SelectSession {

    /** One selectable word, in the pixel space of {@link #bitmap}. */
    public static final class Word {
        public final String text;
        public final RectF bounds;
        /** Words of one view share a block, words of one visual line share a line. */
        public final int block;
        public final int line;

        public Word(String text, RectF bounds, int block, int line) {
            this.text = text;
            this.bounds = bounds;
            this.block = block;
            this.line = line;
        }
    }

    public final Bitmap bitmap;
    public final List<Word> words;

    public SelectSession(Bitmap bitmap, List<Word> words) {
        this.bitmap = bitmap;
        this.words = words;
    }

    private static SelectSession sCurrent;

    public static synchronized void put(SelectSession session) {
        sCurrent = session;
    }

    /** The session the screen shows, null when there is none (process restarted). */
    public static synchronized SelectSession current() {
        return sCurrent;
    }

    /** Drops the snapshot, the screen is gone. */
    public static synchronized void clear() {
        sCurrent = null;
    }
}
