package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The azan's card: a small glass card naming the prayer, over whatever is on screen. The sound is
 * AzanService's (Bloatless 3.0.3), so it plays even where Android keeps this card from opening over
 * another app. Any button stops the azan; so does leaving the card. The card closes when the azan ends.
 */
public final class AzanActivity extends Activity {
    /** The card on screen; null otherwise. Main thread only. */
    private static AzanActivity shown;
    private boolean done;

    /** Closes the card, if it shows (the azan ended). */
    static void close() {
        if (shown != null) shown.end(false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        int index = getIntent().getIntExtra(Solat.EXTRA_INDEX, 1);
        if (index < 0 || index >= Solat.NAMES.length) index = 1;
        long at = getIntent().getLongExtra(Solat.EXTRA_AT, System.currentTimeMillis());
        shown = this;
        buildCard(index, at);
        // The receiver could not start the sound: from here, in front, it can.
        if (AzanService.current == null && !getIntent().getBooleanExtra(EXTRA_PLAYING, false)) AzanService.start(this, index, at);
    }

    /** Set by AzanReceiver when the service was started. */
    static final String EXTRA_PLAYING = "playing";

    private void buildCard(int index, long at) {
        float d = getResources().getDisplayMetrics().density;
        Typeface outfit = null;
        try {
            outfit = ResourcesCompat.getFont(this, R.font.outfit);
        } catch (Exception ignored) {
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x99000000);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = (int) (36 * d);
        card.setPadding(pad * 2, pad, pad * 2, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(28 * d);
        bg.setColor(0xCC1C1C1E);
        bg.setStroke((int) Math.max(1, d), 0x33FFFFFF);
        card.setBackground(bg);

        boolean h24 = android.text.format.DateFormat.is24HourFormat(this);
        DateFormat fmt = new SimpleDateFormat(h24 ? "HH:mm" : "h:mm a", Locale.ROOT);
        fmt.setTimeZone(Solat.MY);

        card.addView(text("Telah masuk waktu", 20, 0xB3FFFFFF, outfit, 400, d));
        TextView name = text(Solat.NAMES[index], 64, Color.WHITE, outfit, 300, d);
        name.setPadding(0, (int) (6 * d), 0, (int) (2 * d));
        card.addView(name);
        card.addView(text(fmt.format(new Date(at)).toUpperCase(Locale.ROOT), 26, 0xE6FFFFFF, outfit, 400, d));
        TextView hint = text("Tekan mana-mana butang untuk tutup", 15, 0x80FFFFFF, outfit, 400, d);
        hint.setPadding(0, (int) (22 * d), 0, 0);
        card.addView(hint);

        root.addView(card, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        setContentView(root);
    }

    private TextView text(String s, float sp, int color, Typeface face, int weight, float d) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        if (face != null) t.setTypeface(face);
        if (android.os.Build.VERSION.SDK_INT >= 26) t.setFontVariationSettings("'wght' " + weight);
        return t;
    }

    /** Any button stops the azan; the press goes no further. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) end(true);
        return true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        end(true);   // Home pressed, or another app came in front
    }

    @Override
    protected void onDestroy() {
        if (shown == this) shown = null;
        super.onDestroy();
    }

    private void end(boolean stopAzan) {
        if (done) return;
        done = true;
        if (shown == this) shown = null;
        if (stopAzan) AzanService.stopNow();
        if (!isFinishing()) finish();
        overridePendingTransition(0, 0);
    }
}
