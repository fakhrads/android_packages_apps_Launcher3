/*
 * SPDX-FileCopyrightText: 2026 Fakhri Adi Saputra
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.quickstep.textselect;

import android.app.Activity;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.launcher3.R;

/**
 * Shows the task snapshot frozen with its text selectable, and what can be done with a pick.
 * Started by the Select chip in overview once {@link AssistTextRequester} has found text.
 */
public class SelectTextActivity extends Activity implements SelectTextView.Listener {

    private SelectSession mSession;
    private SelectTextView mSelectView;
    private LinearLayout mBar;
    private TextView mHint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSession = SelectSession.current();
        if (mSession == null) {
            // The process was recreated or the screen was reopened from history
            finish();
            return;
        }
        final float density = getResources().getDisplayMetrics().density;
        final FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        mSelectView = new SelectTextView(this, mSession, this);
        root.addView(mSelectView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mHint = new TextView(this);
        mHint.setText(R.string.select_hint);
        mHint.setTextColor(Color.WHITE);
        mHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mHint.setGravity(Gravity.CENTER);
        mHint.setPadding((int) (16 * density), (int) (8 * density),
                (int) (16 * density), (int) (8 * density));
        mHint.setBackground(pill(0xCC202124, density));
        final FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        hintParams.topMargin = (int) (16 * density);
        root.addView(mHint, hintParams);

        mBar = new LinearLayout(this);
        mBar.setOrientation(LinearLayout.HORIZONTAL);
        mBar.setGravity(Gravity.CENTER);
        mBar.setVisibility(View.INVISIBLE);
        mBar.addView(action(R.string.select_copy, density, v -> copy()));
        mBar.addView(action(R.string.select_share, density, v -> share()));
        mBar.addView(action(R.string.select_search, density, v -> search()));
        mBar.addView(action(R.string.select_all, density, v -> mSelectView.selectAll()));
        final FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        barParams.bottomMargin = (int) (24 * density);
        root.addView(mBar, barParams);

        // Edge to edge is enforced, so keep the controls out from under the system bars
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            final android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsets.CONSUMED;
        });
        setContentView(root);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onDestroy() {
        // Not on a recreation, the same session is shown again
        if (isFinishing()) {
            SelectSession.clear();
        }
        super.onDestroy();
    }

    @Override
    public void onSelectionChanged(int first, int last) {
        final boolean any = first >= 0;
        mBar.setVisibility(any ? View.VISIBLE : View.INVISIBLE);
        mHint.setVisibility(any ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onDismissRequested() {
        finish();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private String selectedText() {
        final int first = mSelectView.firstSelected();
        final int last = mSelectView.lastSelected();
        if (first < 0) {
            return "";
        }
        final StringBuilder text = new StringBuilder();
        SelectSession.Word previous = null;
        for (int i = first; i <= last; i++) {
            final SelectSession.Word word = mSession.words.get(i);
            if (previous != null) {
                // Lines of one view are a wrapped paragraph, so they stay joined, views do not
                text.append(word.block == previous.block ? ' ' : '\n');
            }
            text.append(word.text);
            previous = word;
        }
        return text.toString();
    }

    private void copy() {
        final String text = selectedText();
        if (text.isEmpty()) {
            return;
        }
        final ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        clipboard.setPrimaryClip(ClipData.newPlainText(null, text));
        Toast.makeText(this, R.string.select_copied, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void share() {
        final String text = selectedText();
        if (text.isEmpty()) {
            return;
        }
        final Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(send, null));
        finish();
    }

    private void search() {
        final String text = selectedText();
        if (text.isEmpty()) {
            return;
        }
        final Intent web = new Intent(Intent.ACTION_WEB_SEARCH)
                .putExtra(SearchManager.QUERY, text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(web);
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                        "https://www.google.com/search?q=" + Uri.encode(text)))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException ignored) {
                return;
            }
        }
        finish();
    }

    private TextView action(int label, float density, View.OnClickListener listener) {
        final TextView view = new TextView(this);
        view.setText(label);
        view.setTextColor(Color.WHITE);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        view.setGravity(Gravity.CENTER);
        view.setPadding((int) (16 * density), (int) (10 * density),
                (int) (16 * density), (int) (10 * density));
        view.setBackground(pill(0xE6202124, density));
        view.setOnClickListener(listener);
        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginStart((int) (4 * density));
        params.setMarginEnd((int) (4 * density));
        view.setLayoutParams(params);
        return view;
    }

    private static GradientDrawable pill(int color, float density) {
        final GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(24 * density);
        return drawable;
    }
}
