package com.nest.tmind.api;

import java.util.List;

public final class ApiModels {

    private ApiModels() {
    }

    public static final class ApiResponse<T> {
        public String code;
        public T data;
        public String message;

        public boolean isSuccess() {
            if (data == null) return false;
            // 서버가 code 를 생략하거나 S00000 인 경우 모두 성공으로 처리
            return code == null || code.isEmpty() || "S00000".equals(code);
        }
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
        /** epoch milliseconds */
        public final long startedAt;

        public StartSessionRequest(boolean isEvent, long startedAt) {
            this.isEvent = isEvent;
            this.startedAt = startedAt;
        }
    }

    public static final class StartSessionData {
        public long sessionId;
        public String hrvStatus;
        public Boolean hrvDone;
        public Boolean emaDone;
        public Boolean diaryDone;
    }

    public static final class TodayResponse {
        public String currentType;
        public String missedType;
        public Long currentSessionId;
        public Boolean hrvDone;
        public Boolean emaDone;
        public Boolean diaryDone;
        /** VALID / SKIPPED / null */
        public String hrvStatus;
        public int totalCount;
        public int completedCount;
        public boolean eventActive;
        public String eventGuideText;
        /** 참여 기간 중 완료 세션이 있는 날 수 (서버 추가 예정) */
        public Integer participatedDays;
        /** 연구 N일차 (서버 추가 예정) */
        public Integer studyDayIndex;
        /** 서버 기준 오늘 날짜 yyyy-MM-dd (서버 추가 예정) */
        public String serverDate;
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

    /** HRV multipart 의 data 파트 JSON */
    public static final class HrvUploadData {
        public final long measuredAt;
        public final boolean measurementValid;
        public final int bpm;
        public final int hrvMs;
        public final int rrMs;
        public final int stressScore;
        public final int fs;
        /** Hybrid 암호화 시 서버 공개키 keyId */
        public final String keyId;

        public HrvUploadData(long measuredAt, boolean measurementValid,
                             int bpm, int hrvMs, int rrMs, int stressScore, int fs) {
            this(measuredAt, measurementValid, bpm, hrvMs, rrMs, stressScore, fs, null);
        }

        public HrvUploadData(long measuredAt, boolean measurementValid,
                             int bpm, int hrvMs, int rrMs, int stressScore, int fs, String keyId) {
            this.measuredAt = measuredAt;
            this.measurementValid = measurementValid;
            this.bpm = bpm;
            this.hrvMs = hrvMs;
            this.rrMs = rrMs;
            this.stressScore = stressScore;
            this.fs = fs;
            this.keyId = keyId;
        }
    }

    public static final class PublicKeyResponse {
        public String keyId;
        /** PEM 또는 Base64 DER */
        public String publicKey;
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
        public final Float correctedValence;
        public final Float correctedArousal;
        public final long occurredAt;

        public FeedbackRequest(String matchResult, String reasonCode,
                               Float correctedValence, Float correctedArousal, long occurredAt) {
            this.matchResult = matchResult;
            this.reasonCode = reasonCode;
            this.correctedValence = correctedValence;
            this.correctedArousal = correctedArousal;
            this.occurredAt = occurredAt;
        }
    }

    public static final class SubmitEmaListRequest {
        public final List<SubmitEmaRequest> responses;
        public final float valence;
        public final float arousal;
        public final long submittedAt;

        public SubmitEmaListRequest(List<SubmitEmaRequest> responses,
                                    float valence, float arousal, long submittedAt) {
            this.responses = responses;
            this.valence = valence;
            this.arousal = arousal;
            this.submittedAt = submittedAt;
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
