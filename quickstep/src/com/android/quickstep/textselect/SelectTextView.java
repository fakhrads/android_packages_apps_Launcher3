/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.quickstep.textselect;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;

/**
 * The frozen snapshot with its words on top. A touch picks the word under the finger, dragging
 * extends the pick to every word in between, in reading order.
 */
final class SelectTextView extends View {

    interface Listener {
        /** Called whenever the picked range changes, with {@code -1} for nothing picked. */
        void onSelectionChanged(int first, int last);

        /** Called when the snapshot is touched where there is no word and nothing is picked. */
        void onDismissRequested();
    }

    private final SelectSession mSession;
    private final List<SelectSession.Word> mWords;
    private final Listener mListener;

    private final Matrix mToView = new Matrix();
    private final Paint mBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint mScrimPaint = new Paint();
    private final Paint mOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mTmp = new RectF();
    private final float mDensity;
    private final float mCorner;

    private int mAnchor = -1;
    private int mCurrent = -1;
    private boolean mDragging;

    SelectTextView(Context context, SelectSession session, Listener listener) {
        super(context);
        mSession = session;
        mWords = session.words;
        mListener = listener;
        mDensity = context.getResources().getDisplayMetrics().density;
        mCorner = 4 * mDensity;
        mScrimPaint.setColor(0x59000000);
        mOutlinePaint.setStyle(Paint.Style.STROKE);
        mOutlinePaint.setStrokeWidth(Math.max(1f, mDensity));
        mOutlinePaint.setColor(0x66FFFFFF);
        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setColor(context.getColor(android.R.color.system_accent1_300) & 0x00FFFFFF
                | 0x99000000);
    }

    int firstSelected() {
        return mAnchor < 0 ? -1 : Math.min(mAnchor, mCurrent);
    }

    int lastSelected() {
        return mAnchor < 0 ? -1 : Math.max(mAnchor, mCurrent);
    }

    void selectAll() {
        mAnchor = 0;
        mCurrent = mWords.size() - 1;
        invalidate();
        mListener.onSelectionChanged(firstSelected(), lastSelected());
    }

    void clearSelection() {
        mAnchor = -1;
        mCurrent = -1;
        invalidate();
        mListener.onSelectionChanged(-1, -1);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Fit the snapshot inside the view, centred, the way it sat on the screen
        final float bw = mSession.bitmap.getWidth();
        final float bh = mSession.bitmap.getHeight();
        final float scale = Math.min(w / bw, h / bh);
        mToView.reset();
        mToView.postScale(scale, scale);
        mToView.postTranslate((w - bw * scale) / 2f, (h - bh * scale) / 2f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawBitmap(mSession.bitmap, mToView, mBitmapPaint);
        canvas.drawRect(0, 0, getWidth(), getHeight(), mScrimPaint);
        final int first = firstSelected();
        final int last = lastSelected();
        for (int i = 0; i < mWords.size(); i++) {
            mToView.mapRect(mTmp, mWords.get(i).bounds);
            mTmp.inset(-1.5f * mDensity, 0);
            if (i >= first && i <= last && first >= 0) {
                canvas.drawRoundRect(mTmp, mCorner, mCorner, mFillPaint);
            } else {
                canvas.drawRoundRect(mTmp, mCorner, mCorner, mOutlinePaint);
            }
        }
    }

    /** The word nearest to a point, or -1 when none is within reach. */
    private int wordAt(float x, float y) {
        final float reach = 28 * mDensity;
        int best = -1;
        float bestDistance = reach * reach;
        for (int i = 0; i < mWords.size(); i++) {
            mToView.mapRect(mTmp, mWords.get(i).bounds);
            final float dx = x < mTmp.left ? mTmp.left - x : (x > mTmp.right ? x - mTmp.right : 0);
            final float dy = y < mTmp.top ? mTmp.top - y : (y > mTmp.bottom ? y - mTmp.bottom : 0);
            final float d = dx * dx + dy * dy;
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                final int word = wordAt(event.getX(), event.getY());
                if (word >= 0) {
                    mAnchor = word;
                    mCurrent = word;
                    mDragging = true;
                    invalidate();
                    mListener.onSelectionChanged(firstSelected(), lastSelected());
                } else if (mAnchor >= 0) {
                    clearSelection();
                } else {
                    mListener.onDismissRequested();
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!mDragging) {
                    return true;
                }
                final int word = wordAt(event.getX(), event.getY());
                if (word >= 0 && word != mCurrent) {
                    mCurrent = word;
                    invalidate();
                    mListener.onSelectionChanged(firstSelected(), lastSelected());
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mDragging = false;
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }
}
