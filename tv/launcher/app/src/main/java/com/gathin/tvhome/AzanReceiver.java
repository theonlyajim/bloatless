package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

/**
 * The prayer-time alarm (Solat.ACTION_AZAN): opens AzanActivity when the TV is on and the alarm is on
 * time, then sets the next one. Also sets the alarm again after boot, a clock or time zone change, an
 * update of the launcher, and on the periodic check (Solat.ACTION_CHECK).
 */
public final class AzanReceiver extends BroadcastReceiver {
    /** An alarm that arrives later than this (the TV was in standby) is skipped. */
    private static final long STALE_MS = 3 * 60_000L;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (Solat.ACTION_AZAN.equals(action)) {
            int index = intent.getIntExtra(Solat.EXTRA_INDEX, -1);
            long at = intent.getLongExtra(Solat.EXTRA_AT, 0);
            long late = System.currentTimeMillis() - at;
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            boolean on = pm == null || pm.isInteractive();
            Config cfg = Config.load(ctx);
            boolean wanted = cfg.azanOn && index >= 0 && index < cfg.azanPrayers.length && cfg.azanPrayers[index];
            if (wanted && index < Solat.NAMES.length && late > -60_000 && late < STALE_MS && on) {
                Log.i(Solat.TAG, "azan " + Solat.NAMES[index]);
                Intent show = new Intent(ctx, AzanActivity.class)
                        .putExtra(Solat.EXTRA_INDEX, index)
                        .putExtra(Solat.EXTRA_AT, at)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                try {
                    ctx.startActivity(show);
                } catch (Exception e) {
                    Log.w(Solat.TAG, "can't show azan: " + e);
                }
            } else {
                Log.i(Solat.TAG, "azan skipped: wanted=" + wanted + " index=" + index + " late=" + late + "ms on=" + on);
            }
        }
        PendingResult result = goAsync();
        Solat.scheduleAsync(ctx, result::finish);
    }
}
