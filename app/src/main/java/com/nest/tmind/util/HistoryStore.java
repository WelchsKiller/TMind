package com.nest.tmind.util;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 설문·분석 결과 히스토리 로컬 저장 */
public final class HistoryStore {

    private static final String PREF = "tmind_history";
    private static final String KEY_EMA = "ema_history";
    private static final String KEY_ANALYSIS = "analysis_history";
    private static final int MAX = 50;

    private HistoryStore() {
    }

    public static void addEma(Context ctx, String sessionType, JSONObject answers) {
        try {
            JSONObject row = new JSONObject();
            row.put("ts", System.currentTimeMillis());
            row.put("sessionType", sessionType);
            row.put("answers", answers);
            prepend(ctx, KEY_EMA, row);
        } catch (Exception ignored) {
        }
    }

    public static void addAnalysis(Context ctx, String message, int bpm, int hrvMs) {
        addAnalysis(ctx, message, bpm, hrvMs, System.currentTimeMillis());
    }

    /**
     * 측정 시각(measureAt) 기준 중복 방지.
     * HRV 결과 화면에서 저장하고, 이후 분석결과 화면에서 같은 측정이면 메시지만 갱신.
     */
    public static void addAnalysis(Context ctx, String message, int bpm, int hrvMs, long measureAt) {
        try {
            long ts = measureAt > 0 ? measureAt : System.currentTimeMillis();
            SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(sp.getString(KEY_ANALYSIS, "[]"));

            // 동일 측정 시각(±2초)이면 메시지/수치 갱신만
            for (int i = 0; i < arr.length(); i++) {
                JSONObject old = arr.getJSONObject(i);
                long oldTs = old.optLong("ts", 0);
                if (Math.abs(oldTs - ts) < 2000L) {
                    old.put("message", message);
                    old.put("bpm", bpm);
                    old.put("hrvMs", hrvMs);
                    old.put("ts", ts);
                    sp.edit().putString(KEY_ANALYSIS, arr.toString()).apply();
                    return;
                }
            }

            JSONObject row = new JSONObject();
            row.put("ts", ts);
            row.put("message", message);
            row.put("bpm", bpm);
            row.put("hrvMs", hrvMs);
            prepend(ctx, KEY_ANALYSIS, row);
        } catch (Exception ignored) {
        }
    }

    public static List<JSONObject> loadEma(Context ctx) {
        return load(ctx, KEY_EMA);
    }

    public static List<JSONObject> loadAnalysis(Context ctx) {
        return load(ctx, KEY_ANALYSIS);
    }

    private static void prepend(Context ctx, String key, JSONObject row) throws Exception {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        JSONArray arr = new JSONArray(sp.getString(key, "[]"));
        JSONArray next = new JSONArray();
        next.put(row);
        for (int i = 0; i < arr.length() && next.length() < MAX; i++) {
            next.put(arr.getJSONObject(i));
        }
        sp.edit().putString(key, next.toString()).apply();
    }

    private static List<JSONObject> load(Context ctx, String key) {
        List<JSONObject> out = new ArrayList<>();
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(sp.getString(key, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                out.add(arr.getJSONObject(i));
            }
        } catch (Exception ignored) {
        }
        return out;
    }
}
