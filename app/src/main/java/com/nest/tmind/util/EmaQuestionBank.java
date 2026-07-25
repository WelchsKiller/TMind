package com.nest.tmind.util;

import java.util.Calendar;

/**
 * EMA 문항 뱅크 (Baseline / 오전 / Event / 오후 / Followup).
 * 일일 미션은 시간대에 따라 오전·이벤트·오후 세트를 사용한다.
 */
public final class EmaQuestionBank {

    public enum SessionType {
        BASELINE,
        MORNING,
        EVENT,
        AFTERNOON,
        FOLLOWUP
    }

    public static final class Item {
        public final String prompt;
        public final String key;
        public final String[] scaleLabels;
        public final boolean emotionScale;

        public Item(String prompt, String key, String[] scaleLabels, boolean emotionScale) {
            this.prompt = prompt;
            this.key = key;
            this.scaleLabels = scaleLabels;
            this.emotionScale = emotionScale;
        }
    }

    private static final String[] SCALE_INTENSITY = {
            "매우 많이 있었다", "많이 있었다", "어느 정도 있었다", "조금 있었다", "전혀 없었다"
    };

    private static final String[] SCALE_AGREE = {
            "매우 그렇다", "그런 편이다", "보통이다", "그렇지 않은 편이다", "전혀 그렇지 않다"
    };

    private static final String[] EMOJIS_AGREE = {"😊", "🙂", "😐", "🙁", "😢"};
    private static final String[] EMOJIS_INTENSITY = {"😣", "😟", "😐", "🙂", "😌"};

    /** 통증/감정 스케일 색상: 표시 인덱스 0→4 (녹→적 또는 적→녹은 emotionScale에 따름) */
    private static final int[] COLORS_GOOD_TO_BAD = {
            0xFF2E7D32, // green
            0xFF8BC34A, // light green
            0xFFFBC02D, // yellow
            0xFFFF9800, // orange
            0xFFE53935  // red
    };

    private static final int[] COLORS_BAD_TO_GOOD = {
            0xFFE53935,
            0xFFFF9800,
            0xFFFBC02D,
            0xFF8BC34A,
            0xFF2E7D32
    };

    private EmaQuestionBank() {
    }

    /** 현재 시각 기준 일일 EMA 세션 타입 */
    public static SessionType dailyTypeNow() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) return SessionType.MORNING;
        if (hour < 17) return SessionType.EVENT;
        return SessionType.AFTERNOON;
    }

    public static Item[] itemsFor(SessionType type) {
        switch (type) {
            case BASELINE:
                return baseline();
            case MORNING:
                return morning();
            case EVENT:
                return event();
            case AFTERNOON:
                return afternoon();
            case FOLLOWUP:
                return followup();
            default:
                return morning();
        }
    }

    public static String[] emojisFor(Item item) {
        return item.emotionScale ? EMOJIS_AGREE : EMOJIS_INTENSITY;
    }

    /** displayIndex 0~4 에 대한 구분 색상 (ARGB) */
    public static int colorFor(Item item, int displayIndex) {
        if (displayIndex < 0 || displayIndex > 4) return 0xFF757575;
        // emotion(동의): 매우 그렇다=긍정(녹) … 전혀=부정(적)
        // intensity: 매우 많이=부정(적) … 전혀 없음=긍정(녹)
        int[] palette = item.emotionScale ? COLORS_GOOD_TO_BAD : COLORS_BAD_TO_GOOD;
        return palette[displayIndex];
    }

    private static Item[] baseline() {
        return concat(
                new Item[]{
                        intensity("지난 7일간, 잠을 자는 데 어려움이 얼마나 있었나요?", "sleep_diff"),
                        intensity("지난 7일간, 피곤하거나 기력이 없다고 느낀 정도는?", "fatigue"),
                        intensity("지난 7일간, 기분이 가라앉거나 우울한 정도는?", "low_mood"),
                        intensity("지난 7일간, 걱정되거나 불안한 정도는?", "anxiety"),
                        intensity("지난 7일간, 외롭거나 사람들과 거리감이 든 정도는?", "lonely"),
                        intensity("지난 7일간, 전반적인 컨디션은 어떠했나요?", "overall")
                },
                emotionBlock()
        );
    }

    private static Item[] morning() {
        return concat(
                new Item[]{
                        intensity("어젯밤 잠을 자는 데 어려움이 얼마나 있었나요?", "sleep_diff"),
                        intensity("지금 졸리거나 깨어 있기 힘든 느낌이 얼마나 있나요?", "sleepy_now")
                },
                emotionBlock()
        );
    }

    private static Item[] event() {
        return concat(
                new Item[]{
                        intensity("지금 기력이 없다고 느끼는 정도는?", "no_energy"),
                        intensity("지금 기분이 가라앉거나 우울한 정도는?", "low_mood_now"),
                        intensity("지금 걱정되거나 불안한 정도는?", "anxiety_now"),
                        intensity("지금 외롭거나 사람들과 거리감이 드는 정도는?", "lonely_now")
                },
                emotionBlock()
        );
    }

    private static Item[] afternoon() {
        return concat(
                new Item[]{
                        intensity("오늘 하루, 피곤하거나 기력이 없다고 느낀 정도는?", "fatigue_today"),
                        intensity("오늘 하루, 기분이 가라앉거나 우울한 정도는?", "low_mood_today"),
                        intensity("오늘 하루, 걱정되거나 불안한 정도는?", "anxiety_today"),
                        intensity("오늘 하루, 외롭거나 사람들과 거리감이 든 정도는?", "lonely_today")
                },
                emotionBlock()
        );
    }

    private static Item[] followup() {
        return concat(
                new Item[]{
                        intensity("어젯밤 잠을 자는 데 어려움이 얼마나 있었나요?", "sleep_diff"),
                        intensity("지금 졸리거나 깨어 있기 힘든 느낌이 얼마나 있나요?", "sleepy_now"),
                        intensity("지금 피곤하거나 기력이 없다고 느끼는 정도는?", "fatigue"),
                        intensity("지금 기분이 가라앉거나 우울한 정도는?", "low_mood"),
                        intensity("지금 걱정되거나 불안한 정도는?", "anxiety"),
                        intensity("지금 외롭거나 사람들과 거리감이 드는 정도는?", "lonely")
                },
                emotionBlock()
        );
    }

    private static Item[] emotionBlock() {
        return new Item[]{
                agree("지금 이 순간, 나는 행복하다", "happy"),
                agree("지금 이 순간, 나는 답답하거나 좌절스럽다", "frustrated"),
                agree("지금 이 순간, 나는 슬프다", "sad"),
                agree("지금 이 순간, 나는 걱정된다", "worried"),
                agree("지금 이 순간, 나는 안절부절못한다", "restless"),
                agree("지금 이 순간, 나는 신나거나 들떠 있다", "excited"),
                agree("지금 이 순간, 나는 차분하다", "calm"),
                agree("지금 이 순간, 나는 지루하다", "bored"),
                agree("지금 이 순간, 나는 몸과 마음이 쳐진다", "sluggish")
        };
    }

    private static Item intensity(String prompt, String key) {
        return new Item(prompt, key, SCALE_INTENSITY, false);
    }

    private static Item agree(String prompt, String key) {
        return new Item(prompt, key, SCALE_AGREE, true);
    }

    private static Item[] concat(Item[] a, Item[] b) {
        Item[] out = new Item[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
