package com.nest.tmind.util;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.os.BatteryManager;

/**
 * 학습·추론 시 배터리·발열 상태에 따라 연산 강도 조절.
 * HIGH = 정상, MEDIUM = 절전 분석, LOW = 최소 연산.
 */
public final class ResourceManager {

    public enum Level {
        HIGH(1.0f, "정상"),
        MEDIUM(0.6f, "절전"),
        LOW(0.3f, "최소");

        public final float factor;
        public final String label;

        Level(float factor, String label) {
            this.factor = factor;
            this.label = label;
        }
    }

    private ResourceManager() {
    }

    public static Level getLevel(Context ctx) {
        int battery = batteryPercent(ctx);
        int thermal = thermalStatus(ctx);
        boolean powerSave = isPowerSave(ctx);

        // 발열 심각 또는 배터리 매우 낮음 → 최소
        if (thermal >= PowerManager.THERMAL_STATUS_SEVERE
                || battery > 0 && battery <= 15
                || (powerSave && battery <= 25)) {
            return Level.LOW;
        }
        // 발열 주의 또는 배터리 중간 이하 → 절전
        if (thermal >= PowerManager.THERMAL_STATUS_MODERATE
                || battery > 0 && battery <= 30
                || powerSave) {
            return Level.MEDIUM;
        }
        return Level.HIGH;
    }

    /** 분석 샘플 다운샘플 간격 (1=전부, 2=절반, 4=1/4) */
    public static int sampleStride(Level level) {
        switch (level) {
            case LOW:
                return 4;
            case MEDIUM:
                return 2;
            default:
                return 1;
        }
    }

    /** notch/고급 필터 사용 여부 */
    public static boolean useHeavyFilters(Level level) {
        return level == Level.HIGH;
    }

    /** 스파이크 아이콘 길이 */
    public static int spikeLength(Level level) {
        switch (level) {
            case LOW:
                return 100;
            case MEDIUM:
                return 200;
            default:
                return 400;
        }
    }

    public static int batteryPercent(Context ctx) {
        try {
            BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            if (bm == null) return -1;
            return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } catch (Exception e) {
            return -1;
        }
    }

    public static int thermalStatus(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return PowerManager.THERMAL_STATUS_NONE;
        }
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            if (pm == null) return PowerManager.THERMAL_STATUS_NONE;
            return pm.getCurrentThermalStatus();
        } catch (Exception e) {
            return PowerManager.THERMAL_STATUS_NONE;
        }
    }

    public static boolean isPowerSave(Context ctx) {
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isPowerSaveMode();
        } catch (Exception e) {
            return false;
        }
    }
}
