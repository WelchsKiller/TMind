package com.nest.tmind.util;

import java.util.Map;

/**
 * Russell circumplex 좌표 계산.
 * Ground truth: Nandy et al. (2023) 9문항 가중치 합.
 * HRV 예측: stress(0~100) → (valence, arousal) 시각 좌표 (언어 라벨 없음).
 */
public final class RussellEmotionCalculator {

    public static final class Point {
        public final float valence;
        public final float arousal;

        public Point(float valence, float arousal) {
            this.valence = valence;
            this.arousal = arousal;
        }
    }

    /** Emotional Item 가중치 (Nandy et al., 2023) */
    private static final String[] ITEM_KEYS = {
            "happy", "frustrated", "sad", "worried", "restless",
            "excited", "calm", "bored", "sluggish"
    };
    private static final float[] VALENCE_W = {
            0.89f, -0.60f, -0.81f, -0.07f, -0.04f,
            0.70f, 0.78f, -0.35f, -0.22f
    };
    private static final float[] AROUSAL_W = {
            0.17f, 0.40f, -0.40f, -0.32f, 0.29f,
            0.71f, -0.68f, -0.78f, -0.50f
    };

    /** EMA 가중 합을 사분면 표시용 [-1,1]로 나눌 때 사용 */
    public static final float EMA_DISPLAY_SCALE = 5.0f;

    private RussellEmotionCalculator() {
    }

    /**
     * 9문항 응답(1~5) → ground-truth 좌표.
     * 하나라도 누락(<=0)이면 null.
     */
    public static Point fromEmaAnswers(Map<String, Integer> answers) {
        if (answers == null) return null;
        float v = 0f;
        float a = 0f;
        for (int i = 0; i < ITEM_KEYS.length; i++) {
            Integer score = answers.get(ITEM_KEYS[i]);
            if (score == null || score < 1 || score > 5) return null;
            v += VALENCE_W[i] * score;
            a += AROUSAL_W[i] * score;
        }
        return new Point(v, a);
    }

    /** EMA 원시 좌표를 사분면 표시용 [-1,1] 근처로 정규화 */
    public static Point toDisplay(Point raw) {
        if (raw == null) return null;
        return new Point(
                clamp(raw.valence / EMA_DISPLAY_SCALE, -1.2f, 1.2f),
                clamp(raw.arousal / EMA_DISPLAY_SCALE, -1.2f, 1.2f)
        );
    }

    /**
     * HRV 기반 정서 예측 좌표 (언어 표현 없이 사분면용).
     * stress 낮음 → 긍정·저각성(차분), stress 높음 → 부정·고각성(긴장).
     */
    public static Point fromHrvStress(int stressScore) {
        int s = Math.max(0, Math.min(100, stressScore));
        float valence = clamp((40f - s) / 40f, -1f, 1f);
        float arousal = clamp((s - 50f) / 50f, -1f, 1f);
        return new Point(valence, arousal);
    }

    public static Point fromHrvStress(int stressScore, int hrvMs, int hrBpm) {
        Point base = fromHrvStress(stressScore);
        if (hrvMs <= 0 && hrBpm <= 0) return base;
        float v = base.valence;
        float a = base.arousal;
        if (hrvMs > 0) {
            float hrvNorm = clamp((hrvMs - 20f) / 140f, 0f, 1f);
            v = clamp(v * 0.7f + (hrvNorm * 2f - 1f) * 0.3f, -1f, 1f);
        }
        if (hrBpm > 0) {
            float hrNorm = clamp((hrBpm - 55f) / 65f, 0f, 1f);
            a = clamp(a * 0.7f + (hrNorm * 2f - 1f) * 0.3f, -1f, 1f);
        }
        return new Point(v, a);
    }

    public static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
