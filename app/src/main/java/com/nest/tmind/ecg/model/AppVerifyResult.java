package com.nest.tmind.ecg.model;

// AppVerifyResult.java
public class AppVerifyResult {
    public String dateTime;          // "2025.11.12 13:21"
    public int successRate;          // 95 (%)
    public double appRunSeconds;        // 3 (초)
    public int uiResponseMs;         // 300 (ms)
    public int connectionStability;  // 99 (%)

    public AppVerifyResult(String dateTime, int successRate,
                           double appRunSeconds, int uiResponseMs,
                           int connectionStability) {
        this.dateTime = dateTime;
        this.successRate = successRate;
        this.appRunSeconds = appRunSeconds;
        this.uiResponseMs = uiResponseMs;
        this.connectionStability = connectionStability;
    }
}
