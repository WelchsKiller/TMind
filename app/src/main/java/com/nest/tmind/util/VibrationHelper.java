package com.nest.tmind.util;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/** 이완·수면 유도용 진동 패턴 */
public final class VibrationHelper {

    private VibrationHelper() {
    }

    public static void relaxPattern(Context ctx) {
        vibrate(ctx, new long[]{0, 200, 150, 200, 150, 400}, new int[]{0, 80, 0, 60, 0, 40});
    }

    public static void softSleepPattern(Context ctx) {
        vibrate(ctx, new long[]{0, 120, 200, 120, 200, 120, 400},
                new int[]{0, 40, 0, 35, 0, 30, 0});
    }

    private static void vibrate(Context ctx, long[] timings, int[] amps) {
        Vibrator v = vibrator(ctx);
        if (v == null || !v.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (v.hasAmplitudeControl()) {
                    v.vibrate(VibrationEffect.createWaveform(timings, amps, -1));
                } else {
                    v.vibrate(VibrationEffect.createWaveform(timings, -1));
                }
            } else {
                v.vibrate(timings, -1);
            }
        } catch (Exception ignored) {
        }
    }

    private static Vibrator vibrator(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return vm != null ? vm.getDefaultVibrator() : null;
        }
        return (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
    }
}
