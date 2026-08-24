package com.nest.tmind.api;

import java.util.List;

public final class ApiModels {

    private ApiModels() {
    }

    public static final class ApiResponse<T> {
        public String code;
        public T data;
        public String message;
    }

    public static final class TokenPair {
        public String accessToken;
        public String refreshToken;
    }

    public static final class MemberLoginRequest {
        public final String code;

        public MemberLoginRequest(String code) {
            this.code = code;
        }
    }

    public static final class RefreshRequest {
        public final String refreshToken;

        public RefreshRequest(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }

    public static final class StartSessionRequest {
        public final boolean isEvent;

        public StartSessionRequest(boolean isEvent) {
            this.isEvent = isEvent;
        }
    }

    public static final class StartSessionData {
        public long sessionId;
    }

    public static final class TodayResponse {
        public String currentType;
        public String missedType;
        public int totalCount;
        public int completedCount;
        public boolean eventActive;
    }

    public static final class ParticipationResponse {
        public TodayStatus today;
        public PeriodStatus period;
    }

    public static final class TodayStatus {
        public boolean amDone;
        public boolean pmDone;
        public int eventCount;
        public int dailyScore;
    }

    public static final class PeriodStatus {
        public String startDate;
        public String endDate;
        public int totalDays;
        public int totalScore;
        public int participatedDays;
        public List<DayStatus> days;
    }

    public static final class DayStatus {
        public String date;
        public boolean amDone;
        public boolean pmDone;
        public boolean eventDone;
    }

    public static final class FcmTokenRequest {
        public final String fcmToken;

        public FcmTokenRequest(String fcmToken) {
            this.fcmToken = fcmToken;
        }
    }

    public static final class SkipHrvRequest {
        public final String skipReason;

        public SkipHrvRequest(String skipReason) {
            this.skipReason = skipReason;
        }
    }

    public static final class SavePredictionRequest {
        public final float predictedValence;
        public final float predictedArousal;
        public final String predictedText;

        public SavePredictionRequest(float predictedValence, float predictedArousal, String predictedText) {
            this.predictedValence = predictedValence;
            this.predictedArousal = predictedArousal;
            this.predictedText = predictedText;
        }
    }

    public static final class FeedbackRequest {
        public final String matchResult;
        public final String reasonCode;

        public FeedbackRequest(String matchResult, String reasonCode) {
            this.matchResult = matchResult;
            this.reasonCode = reasonCode;
        }
    }

    public static final class SubmitEmaListRequest {
        public final List<SubmitEmaRequest> responses;

        public SubmitEmaListRequest(List<SubmitEmaRequest> responses) {
            this.responses = responses;
        }
    }

    public static final class SubmitEmaRequest {
        public final long questionId;
        public final int answerValue;

        public SubmitEmaRequest(long questionId, int answerValue) {
            this.questionId = questionId;
            this.answerValue = answerValue;
        }
    }

    public static final class QuestionResponse {
        public long questionId;
        public int orderNo;
        public String text;
        public List<String> options;
    }
}
