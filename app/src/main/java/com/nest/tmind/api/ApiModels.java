package com.nest.tmind.api;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class ApiModels {

    private ApiModels() {
    }

    public static final class ApiResponse<T> {
        public String code;
        public T data;
        public String message;

        public boolean isSuccess() {
            if (code == null || code.isEmpty()) {
                return data != null;
            }
            return "S00000".equals(code);
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

    /**
     * Apidog 문서에는 isEvent 만 있으나 서버 검증은 startedAt 도 요구한다(누락 시 E00601).
     */
    public static final class StartSessionRequest {
        @SerializedName("isEvent")
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

    /**
     * HRV 업로드의 data 파트(application/json). signal 파트와 함께 multipart 로 보낸다.
     * 지표는 모두 선택이고 서버는 범위를 검사하지 않는다. 유효 판정 주체는 앱이다.
     */
    public static final class HrvSaveRequest {
        /** 필수. epoch milliseconds. 같은 값이면 재전송, 다르면 재측정으로 처리된다. */
        public final long measuredAt;
        public final Integer bpm;
        public final Integer hrvMs;
        public final Integer rrMs;
        public final Integer stressScore;
        public final Integer fs;
        /** 유효 측정일 때만 전송 */
        public final String abnormalityType;
        /** 필수. false 면 서버가 예측을 받지 않고 세션을 보류로 닫는다. */
        public final boolean measurementValid;

        public HrvSaveRequest(long measuredAt, Integer bpm, Integer hrvMs, Integer rrMs,
                              Integer stressScore, Integer fs, String abnormalityType,
                              boolean measurementValid) {
            this.measuredAt = measuredAt;
            this.bpm = bpm;
            this.hrvMs = hrvMs;
            this.rrMs = rrMs;
            this.stressScore = stressScore;
            this.fs = fs;
            this.abnormalityType = abnormalityType;
            this.measurementValid = measurementValid;
        }
    }

    /** 문서상 본문은 좌표 두 개뿐이다. 세션당 1회만 저장 가능하며 재저장은 409. */
    public static final class SavePredictionRequest {
        /** 필수. -1.2 ~ 1.2 */
        public final float predictedValence;
        /** 필수. -1.2 ~ 1.2 */
        public final float predictedArousal;

        public SavePredictionRequest(float predictedValence, float predictedArousal) {
            this.predictedValence = predictedValence;
            this.predictedArousal = predictedArousal;
        }
    }

    public static final class FeedbackRequest {
        public final String matchResult;
        /** 선택 필드 */
        public final String reasonCode;
        /** 필수. -1.2 ~ 1.2 */
        public final float correctedValence;
        /** 필수. -1.2 ~ 1.2 */
        public final float correctedArousal;
        /** 필수. epoch milliseconds */
        public final long occurredAt;

        public FeedbackRequest(String matchResult, String reasonCode,
                               float correctedValence, float correctedArousal, long occurredAt) {
            this.matchResult = matchResult;
            this.reasonCode = reasonCode;
            this.correctedValence = correctedValence;
            this.correctedArousal = correctedArousal;
            this.occurredAt = occurredAt;
        }
    }

    public static final class SubmitEmaListRequest {
        public final List<SubmitEmaRequest> responses;
        /** 답변에서 계산한 러셀 좌표. 서버가 정답 라벨로 사용한다. */
        public final float valence;
        public final float arousal;
        /** epoch milliseconds. 문서에는 없지만 서버 검증이 요구한다. */
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
        /** 일부 서버 응답 호환 */
        public long id;
        public int orderNo;
        public String text;
        public List<String> options;
    }
}
