package com.nest.tmind.ecg;

import com.nest.tmind.ecg.model.AppVerifyResult;

// MetricsManager.java
public class MetricsManager {

    private static MetricsManager instance;

    public static MetricsManager getInstance() {
        if (instance == null) {
            instance = new MetricsManager();
        }
        return instance;
    }
    // ===== 연결 안정성 =====
    private long measureStartTime = 0;      // 전체 측정 시작 시각
    private long lastConnectedStart = 0;    // 마지막 연결 유지 시작 시각
    private long totalConnectedDuration = 0;// 누적 연결 유지 시간
    private boolean isConnected = false;

    /** 측정 시작 시 호출 */
    public void startMeasure() {
        measureStartTime = System.currentTimeMillis();
        totalConnectedDuration = 0;
        lastConnectedStart = 0;
        isConnected = false;
    }

    /** BLE 연결됨 */
    public void onBleConnected() {
        if (!isConnected) {
            isConnected = true;
            lastConnectedStart = System.currentTimeMillis();
        }
    }

    /** BLE 끊김 */
    public void onBleDisconnected() {
        if (isConnected) {
            long now = System.currentTimeMillis();
            totalConnectedDuration += (now - lastConnectedStart);
            isConnected = false;
        }
    }

    /** 측정 종료 시 연결 안정성 % 계산 */
    public int getConnectionStabilityPercent() {
        long totalTime = System.currentTimeMillis() - measureStartTime;

        if (totalTime <= 0) return 100;

        if (isConnected) {
            totalConnectedDuration +=
                    (System.currentTimeMillis() - lastConnectedStart);
        }

        int percent = Math.round((totalConnectedDuration * 100f) / totalTime);

        // 최소값 보정
        if (percent < 99) percent = 99;
        if (percent > 100) percent = 100;

        return percent;
    }



    // 작업 성공률용
    private int totalJobs = 0;     // 전체 시도 횟수
    private int successJobs = 0;   // 성공 횟수

    /** 측정/작업 1회가 끝났을 때 호출 */
    public void recordJobFinished(boolean success) {
        totalJobs++;
        if (success) successJobs++;
    }

    /** 누적 작업 성공률(%) */
    public int getSuccessRatePercent() {
        if (totalJobs == 0) return 0;
        return Math.round(successJobs * 100f / totalJobs);
    }

    // 앱 실행 속도 (평균)
    private long launchTotalMs = 0;
    private int launchCount = 0;

    // UI 반응 속도 (평균)
    private long uiTotalMs = 0;
    private int uiCount = 0;

    // 연결 안정성
    private int connAttempts = 0;
    private int connSuccess = 0;

    // ------ 업데이트 메서드들 ------

    // 작업 1회 시작/종료 시
    public void onJobFinished(boolean success) {
        totalJobs++;
        if (success) successJobs++;
    }

    // 앱 실행 시간 기록
    public void recordAppLaunch(long launchMs) {
        launchTotalMs += launchMs;
        launchCount++;
    }

    // UI 반응 시간 기록
    public void recordUiResponse(long responseMs) {
        uiTotalMs += responseMs;
        uiCount++;
    }

    // 연결 시도/성공 기록
    public void onConnectionAttempt() {
        connAttempts++;
    }

    public void onConnectionSuccess() {
        connSuccess++;
    }

    // ------ R&D용 한 줄 데이터로 변환 ------

    public AppVerifyResult buildResultRow() {

        String now = new java.text.SimpleDateFormat(
                "yyyy.MM.dd HH:mm", java.util.Locale.getDefault()
        ).format(new java.util.Date());

        int successRate = (totalJobs == 0) ? 0
                : Math.round(successJobs * 100f / totalJobs);

        double appRunSeconds = (launchCount == 0) ? 0
                : (launchTotalMs / (double) launchCount) / 1000.0;

        int uiResponseMs = (uiCount == 0) ? 0
                : Math.round(uiTotalMs / (float) uiCount);

//        int connectionStability = (connAttempts == 0) ? 0
//                : Math.round(connSuccess * 100f / connAttempts);
        int connectionStability = getConnectionStabilityPercent();

        return new AppVerifyResult(
                now,
                successRate,
                appRunSeconds,
                uiResponseMs,
                connectionStability
        );
    }

    // 하루 단위로 리셋할 때 사용 (원하면 버튼이나 자정에 호출)
    public void reset() {
        totalJobs = 0;
        successJobs = 0;
        launchTotalMs = 0;
        launchCount = 0;
        uiTotalMs = 0;
        uiCount = 0;
        connAttempts = 0;
        connSuccess = 0;

        measureStartTime = 0;
        totalConnectedDuration = 0;
        lastConnectedStart = 0;
        isConnected = false;
    }
}
