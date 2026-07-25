package com.nest.tmind.ecg;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * 측정 세션 동안 수집된 HR/RR의 평균값.
 * 결과 화면은 순간값이 아닌 이 평균을 우선 표시한다.
 */
public final class MeasureSessionStats {

    private static final ArrayList<Integer> hrSamples = new ArrayList<>();
    private static final ArrayList<Integer> rrSamples = new ArrayList<>();
    private static final ArrayList<Integer> hrvSamples = new ArrayList<>();

    private MeasureSessionStats() {
    }

    public static synchronized void reset() {
        hrSamples.clear();
        rrSamples.clear();
        hrvSamples.clear();
    }

    /** 생리적 범위의 HR만 누적 (40~180 bpm) */
    public static synchronized void addHr(int hrBpm) {
        if (hrBpm >= 40 && hrBpm <= 180) {
            hrSamples.add(hrBpm);
        }
    }

    public static synchronized void addRrMs(int rrMs) {
        if (rrMs >= 333 && rrMs <= 1500) {
            rrSamples.add(rrMs);
        }
    }

    public static synchronized void addHrvMs(int hrvMs) {
        if (hrvMs > 0 && hrvMs <= 300) {
            hrvSamples.add(hrvMs);
        }
    }

    public static synchronized int averageHr() {
        return averageOf(hrSamples, 40, 180);
    }

    public static synchronized int averageRrMs() {
        return averageOf(rrSamples, 333, 1500);
    }

    public static synchronized int averageHrvMs() {
        return averageOf(hrvSamples, 1, 300);
    }

    public static synchronized int sampleCount() {
        return hrSamples.size();
    }

    private static int averageOf(ArrayList<Integer> list, int min, int max) {
        if (list == null || list.isEmpty()) return 0;
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        Arrays.sort(arr);
        int n = arr.length;
        int trim = Math.max(0, n / 10);
        if (n - 2 * trim < 1) trim = 0;
        long sum = 0;
        int cnt = 0;
        for (int i = trim; i < n - trim; i++) {
            int v = arr[i];
            if (v < min || v > max) continue;
            sum += v;
            cnt++;
        }
        if (cnt == 0) return 0;
        return (int) Math.round(sum / (double) cnt);
    }
}
