package com.nest.tmind.util;

import com.nest.tmind.ecg.LastEcgResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 3미션 분석 결과 → 이상 유형 분류 → 맞춤 개입 추천.
 * (스트레스/피로·불면/HRV감소/운동부족/자율신경불균형)
 */
public final class InterventionClassifier {

    public enum Abnormality {
        STRESS,
        FATIGUE_INSOMNIA,
        HRV_DECREASE,
        LOW_ACTIVITY,
        AUTONOMIC_IMBALANCE,
        BALANCED
    }

    public enum ContentType {
        BREATHING,      // 심호흡 훈련
        MEDITATION,     // 명상
        YOGA,           // 요가
        STRESS_VIDEO,   // 스트레스 관리 영상
        MUSIC_RELAX,    // 이완 음악
        VIBRATION,      // 진동 이완
        STRETCH_VIDEO,  // 스트레칭 영상
        SLEEP_MUSIC     // 수면 유도 음악
    }

    public static final class Recommendation {
        public final Abnormality type;
        public final ContentType content;
        public final String titleResKey;
        public final String descResKey;

        public Recommendation(Abnormality type, ContentType content,
                              String titleResKey, String descResKey) {
            this.type = type;
            this.content = content;
            this.titleResKey = titleResKey;
            this.descResKey = descResKey;
        }
    }

    private InterventionClassifier() {
    }

    public static Abnormality classify() {
        if (!LastEcgResult.hasValid()) return Abnormality.BALANCED;

        int stress = LastEcgResult.lastStressScore;
        int hrv = LastEcgResult.lastHrvMs;
        int hr = LastEcgResult.lastHrBpm;

        RussellEmotionCalculator.Point p = RussellEmotionCalculator.fromHrvStress(stress, hrv, hr);

        if (hrv > 0 && hrv < 40) return Abnormality.HRV_DECREASE;
        if (stress >= 70 || (p != null && p.valence < -0.35f && p.arousal > 0.2f)) {
            return Abnormality.STRESS;
        }
        if (stress >= 55 && hr > 0 && hr < 60
                || (p != null && p.arousal < -0.4f && p.valence < 0f)) {
            return Abnormality.FATIGUE_INSOMNIA;
        }
        if (hr > 0 && hr < 55 && stress < 40) return Abnormality.LOW_ACTIVITY;
        if (stress >= 45 && stress < 70 && hrv > 0 && hrv < 70) {
            return Abnormality.AUTONOMIC_IMBALANCE;
        }
        return Abnormality.BALANCED;
    }

    public static List<Recommendation> recommendationsFor(Abnormality type) {
        List<Recommendation> list = new ArrayList<>();
        switch (type) {
            case STRESS:
                list.add(new Recommendation(type, ContentType.MUSIC_RELAX,
                        "interv_music_title", "interv_music_desc"));
                list.add(new Recommendation(type, ContentType.VIBRATION,
                        "interv_vib_title", "interv_vib_desc"));
                list.add(new Recommendation(type, ContentType.STRESS_VIDEO,
                        "interv_stress_video_title", "interv_stress_video_desc"));
                list.add(new Recommendation(type, ContentType.BREATHING,
                        "interv_breath_title", "interv_breath_desc"));
                break;
            case FATIGUE_INSOMNIA:
                list.add(new Recommendation(type, ContentType.VIBRATION,
                        "interv_soft_vib_title", "interv_soft_vib_desc"));
                list.add(new Recommendation(type, ContentType.SLEEP_MUSIC,
                        "interv_sleep_music_title", "interv_sleep_music_desc"));
                list.add(new Recommendation(type, ContentType.MEDITATION,
                        "interv_meditation_title", "interv_meditation_desc"));
                break;
            case HRV_DECREASE:
                list.add(new Recommendation(type, ContentType.MEDITATION,
                        "interv_meditation_title", "interv_meditation_desc"));
                list.add(new Recommendation(type, ContentType.YOGA,
                        "interv_yoga_title", "interv_yoga_desc"));
                list.add(new Recommendation(type, ContentType.BREATHING,
                        "interv_breath_title", "interv_breath_desc"));
                break;
            case LOW_ACTIVITY:
                list.add(new Recommendation(type, ContentType.STRETCH_VIDEO,
                        "interv_stretch_title", "interv_stretch_desc"));
                list.add(new Recommendation(type, ContentType.YOGA,
                        "interv_yoga_title", "interv_yoga_desc"));
                break;
            case AUTONOMIC_IMBALANCE:
                list.add(new Recommendation(type, ContentType.YOGA,
                        "interv_yoga_title", "interv_yoga_desc"));
                list.add(new Recommendation(type, ContentType.BREATHING,
                        "interv_breath_title", "interv_breath_desc"));
                list.add(new Recommendation(type, ContentType.STRETCH_VIDEO,
                        "interv_stretch_title", "interv_stretch_desc"));
                break;
            default:
                list.add(new Recommendation(type, ContentType.BREATHING,
                        "interv_breath_title", "interv_breath_desc"));
                list.add(new Recommendation(type, ContentType.MEDITATION,
                        "interv_meditation_title", "interv_meditation_desc"));
                break;
        }
        return list;
    }
}
