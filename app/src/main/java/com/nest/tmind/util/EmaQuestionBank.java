package com.nest.tmind.util;

import com.nest.tmind.R;

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
        /** 부정 정서 문항: 매우 그렇다=빨강(부정), 전혀=초록(긍정) */
        public final boolean negativeValence;

        public Item(String prompt, String key, String[] scaleLabels, boolean emotionScale) {
            this(prompt, key, scaleLabels, emotionScale, false);
        }

        public Item(String prompt, String key, String[] scaleLabels, boolean emotionScale, boolean negativeValence) {
            this.prompt = prompt;
            this.key = key;
            this.scaleLabels = scaleLabels;
            this.emotionScale = emotionScale;
            this.negativeValence = negativeValence;
        }
    }

    private static final String[] SCALE_INTENSITY = {
            "매우 많이 있었다", "많이 있었다", "어느 정도 있었다", "조금 있었다", "전혀 없었다"
    };

    private static final String[] SCALE_AGREE = {
            "매우 그렇다", "그런 편이다", "보통이다", "그렇지 않은 편이다", "전혀 그렇지 않다"
    };

    private static final String[] EMOJIS_AGREE = {"😊", "🙂", "😐", "🙁", "😢"};

    /** 화면 위→아래 고정: 초록 → 연두 → 노랑 → 주황 → 빨강 */
    private static final int[] COLORS_GOOD_TO_BAD = {
            0xFF2E7D32,
            0xFF8BC34A,
            0xFFFBC02D,
            0xFFFF9800,
            0xFFE53935
    };

    private EmaQuestionBank() {
    }

    /** /today currentType → 설문 세트. 모르면 null. */
    public static SessionType fromServerType(String currentType) {
        if (currentType == null || currentType.trim().isEmpty()) return null;
        switch (currentType.trim().toUpperCase()) {
            case "PM":
            case "AFTERNOON":
                return SessionType.AFTERNOON;
            case "AM":
            case "MORNING":
                return SessionType.MORNING;
            case "BASELINE":
                return SessionType.BASELINE;
            case "FOLLOWUP":
                return SessionType.FOLLOWUP;
            case "EVENT":
                return SessionType.EVENT;
            default:
                return null;
        }
    }

    /** 현재 시각 기준 일일 EMA (메인): 오전/오후. 추가는 EVENT 별도. */
    public static SessionType dailyTypeNow() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return hour < 12 ? SessionType.MORNING : SessionType.AFTERNOON;
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
        return EMOJIS_AGREE;
    }

    /** 서버가 '전혀 없었다'를 맨 위에 주면 true. 로컬 기본은 '매우 많이'가 위. */
    public static boolean isNoneFirst(String[] labels) {
        if (labels == null || labels.length == 0 || labels[0] == null) return false;
        String first = labels[0].replace(" ", "");
        return first.contains("전혀없") || first.contains("전혀그렇지");
    }

    /**
     * 화면 위→아래: NEGATIVE(기본)는 초록→빨강.
     * POSITIVE만 이모지 순서를 뒤집어 맨 아래(매우 그렇다)가 초록 웃음이 되게 한다.
     */
    public static int drawableResFor(Item item, int displayIndex) {
        return drawableResFor(item, displayIndex, null, null);
    }

    public static int drawableResFor(Item item, int displayIndex, String[] labels) {
        return drawableResFor(item, displayIndex, labels, null);
    }

    public static int drawableResFor(Item item, int displayIndex, String[] labels, String polarity) {
        if (displayIndex < 0 || displayIndex > 4) return R.drawable.emoji_3;
        int visual = isPositivePolarity(polarity) ? 4 - displayIndex : displayIndex;
        switch (visual) {
            case 0: return R.drawable.emoji_5;
            case 1: return R.drawable.emoji_4;
            case 2: return R.drawable.emoji_3;
            case 3: return R.drawable.emoji_2;
            default: return R.drawable.emoji_1;
        }
    }

    public static boolean isPositivePolarity(String polarity) {
        return polarity != null && "POSITIVE".equalsIgnoreCase(polarity.trim());
    }

    /** 1=낮음/전혀, 5=높음/매우. 선택지 표시 순서와 맞춰 계산한다. */
    public static int scoreFromDisplayIndex(String[] labels, int displayIndex) {
        if (displayIndex < 0 || displayIndex > 4) return 0;
        return isNoneFirst(labels) ? displayIndex + 1 : 5 - displayIndex;
    }

    public static int displayIndexFromScore(String[] labels, int score) {
        if (score < 1 || score > 5) return -1;
        return isNoneFirst(labels) ? score - 1 : 5 - score;
    }

    /** displayIndex 0~4 에 대한 구분 색상 (ARGB) — 레거시/텍스트용 */
    public static int colorFor(Item item, int displayIndex) {
        return colorFor(item, displayIndex, item != null ? item.scaleLabels : null);
    }

    public static int colorFor(Item item, int displayIndex, String[] labels) {
        return colorFor(item, displayIndex, labels, null);
    }

    public static int colorFor(Item item, int displayIndex, String[] labels, String polarity) {
        if (displayIndex < 0 || displayIndex > 4) return 0xFF757575;
        int visual = isPositivePolarity(polarity) ? 4 - displayIndex : displayIndex;
        return COLORS_GOOD_TO_BAD[visual];
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
                agree("지금 이 순간, 나는 행복하다", "happy", false),
                agree("지금 이 순간, 나는 답답하거나 좌절스럽다", "frustrated", true),
                agree("지금 이 순간, 나는 슬프다", "sad", true),
                agree("지금 이 순간, 나는 걱정된다", "worried", true),
                agree("지금 이 순간, 나는 안절부절못한다", "restless", true),
                agree("지금 이 순간, 나는 신나거나 들떠 있다", "excited", false),
                agree("지금 이 순간, 나는 차분하다", "calm", false),
                agree("지금 이 순간, 나는 지루하다", "bored", true),
                agree("지금 이 순간, 나는 몸과 마음이 쳐진다", "sluggish", true)
        };
    }

    private static Item intensity(String prompt, String key) {
        return new Item(prompt, key, SCALE_INTENSITY, false, false);
    }

    private static Item agree(String prompt, String key, boolean negative) {
        return new Item(prompt, key, SCALE_AGREE, true, negative);
    }

    private static Item[] concat(Item[] a, Item[] b) {
        Item[] out = new Item[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
