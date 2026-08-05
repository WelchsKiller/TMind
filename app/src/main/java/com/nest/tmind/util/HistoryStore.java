package com.nest.tmind.util;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** 설문·분석 결과 히스토리 — AES-256 암호화 로컬 저장 */
public final class HistoryStore {

    private static final String PREF = "tmind_history";
    private static final String KEY_EMA = "ema_history_enc";
    private static final String KEY_ANALYSIS = "analysis_history_enc";
    private static final String KEY_EMA_LEGACY = "ema_history";
    private static final String KEY_ANALYSIS_LEGACY = "analysis_history";
    private static final int MAX = 50;

    private HistoryStore() {
    }

    public static void addEma(Context ctx, String sessionType, JSONObject answers) {
        try {
            JSONObject row = new JSONObject();
            row.put("ts", System.currentTimeMillis());
            row.put("sessionType", sessionType);
            row.put("answers", answers);
            prepend(ctx, KEY_EMA, KEY_EMA_LEGACY, row);
        } catch (Exception ignored) {
        }
    }

    public static void addAnalysis(Context ctx, String message, int bpm, int hrvMs) {
        addAnalysis(ctx, message, bpm, hrvMs, System.currentTimeMillis(), Float.NaN, Float.NaN);
    }

    public static void addAnalysis(Context ctx, String message, int bpm, int hrvMs, long measureAt) {
        addAnalysis(ctx, message, bpm, hrvMs, measureAt, Float.NaN, Float.NaN);
    }

    public static void addAnalysis(Context ctx, String message, int bpm, int hrvMs,
                                   long measureAt, float valence, float arousal) {
        try {
            long ts = measureAt > 0 ? measureAt : System.currentTimeMillis();
            JSONArray arr = loadArray(ctx, KEY_ANALYSIS, KEY_ANALYSIS_LEGACY);

            for (int i = 0; i < arr.length(); i++) {
                JSONObject old = arr.getJSONObject(i);
                long oldTs = old.optLong("ts", 0);
                if (Math.abs(oldTs - ts) < 2000L) {
                    old.put("message", message);
                    old.put("bpm", bpm);
                    old.put("hrvMs", hrvMs);
                    old.put("ts", ts);
                    if (!Float.isNaN(valence)) old.put("valence", valence);
                    if (!Float.isNaN(arousal)) old.put("arousal", arousal);
                    saveArray(ctx, KEY_ANALYSIS, KEY_ANALYSIS_LEGACY, arr);
                    return;
                }
            }

            JSONObject row = new JSONObject();
            row.put("ts", ts);
            row.put("message", message);
            row.put("bpm", bpm);
            row.put("hrvMs", hrvMs);
            if (!Float.isNaN(valence)) row.put("valence", valence);
            if (!Float.isNaN(arousal)) row.put("arousal", arousal);
            prepend(ctx, KEY_ANALYSIS, KEY_ANALYSIS_LEGACY, row);
        } catch (Exception ignored) {
        }
    }

    public static List<JSONObject> loadEma(Context ctx) {
        return toList(loadArray(ctx, KEY_EMA, KEY_EMA_LEGACY));
    }

    public static List<JSONObject> loadAnalysis(Context ctx) {
        return toList(loadArray(ctx, KEY_ANALYSIS, KEY_ANALYSIS_LEGACY));
    }

    public static List<JSONObject> loadDailyLatestAnalysis(Context ctx, int days) {
        List<JSONObject> all = loadAnalysis(ctx);
        List<JSONObject> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int d = days - 1; d >= 0; d--) {
            long dayStart = startOfDay(now - d * 24L * 60L * 60L * 1000L);
            long dayEnd = dayStart + 24L * 60L * 60L * 1000L;
            JSONObject best = null;
            for (JSONObject row : all) {
                long ts = row.optLong("ts", 0);
                if (ts >= dayStart && ts < dayEnd) {
                    if (best == null || ts > best.optLong("ts", 0)) {
                        best = row;
                    }
                }
            }
            if (best != null) {
                try {
                    JSONObject copy = new JSONObject(best.toString());
                    copy.put("dayIndex", days - 1 - d);
                    copy.put("dayStart", dayStart);
                    out.add(copy);
                } catch (Exception ignored) {
                }
            }
        }
        return out;
    }

    public static JSONObject findEmaByTs(Context ctx, long ts) {
        if (ts <= 0) return null;
        for (JSONObject row : loadEma(ctx)) {
            if (Math.abs(row.optLong("ts", 0) - ts) < 2) return row;
        }
        for (JSONObject row : loadEma(ctx)) {
            if (Math.abs(row.optLong("ts", 0) - ts) < 2000L) return row;
        }
        return null;
    }

    private static long startOfDay(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static void prepend(Context ctx, String encKey, String legacyKey, JSONObject row)
            throws Exception {
        JSONArray arr = loadArray(ctx, encKey, legacyKey);
        JSONArray next = new JSONArray();
        next.put(row);
        for (int i = 0; i < arr.length() && next.length() < MAX; i++) {
            next.put(arr.getJSONObject(i));
        }
        saveArray(ctx, encKey, legacyKey, next);
    }

    private static void saveArray(Context ctx, String encKey, String legacyKey, JSONArray arr) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        sp.edit()
                .putString(encKey, AesCrypto.encryptSafe(ctx, arr.toString()))
                .remove(legacyKey)
                .apply();
    }

    private static JSONArray loadArray(Context ctx, String encKey, String legacyKey) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String enc = sp.getString(encKey, null);
        if (enc != null) {
            try {
                return new JSONArray(AesCrypto.decryptOrPlain(enc));
            } catch (Exception e) {
                return new JSONArray();
            }
        }
        String legacy = sp.getString(legacyKey, null);
        if (legacy != null) {
            try {
                JSONArray arr = new JSONArray(legacy);
                saveArray(ctx, encKey, legacyKey, arr);
                return arr;
            } catch (Exception e) {
                return new JSONArray();
            }
        }
        return new JSONArray();
    }

    private static List<JSONObject> toList(JSONArray arr) {
        List<JSONObject> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            try {
                out.add(arr.getJSONObject(i));
            } catch (Exception ignored) {
            }
        }
        return out;
    }
}
