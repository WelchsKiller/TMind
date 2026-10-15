package com.nest.tmind.ecg;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * 측정 세션 동안 수집된 HR/RR의 평균값.
 * 결과 화면은 순간값이 아닌 이 평균을 우선 표시한다.
 */
public final class MeasureSessionStats {

    /** 5분 측정, 프레임 100ms. 유효 비율은 이 길이를 기준으로 한다. */
    private static final int SESSION_FRAMES = 5 * 60 * 10;
    /** 기기 심박이 잡힌 시간이 이 비율 미만이면 재측정. 0%도 여기에 포함된다. */
    private static final double MIN_VALID_RATIO = 0.50;

    private static final ArrayList<Integer> hrSamples = new ArrayList<>();
    private static final ArrayList<Integer> rrSamples = new ArrayList<>();
    private static final ArrayList<Integer> hrvSamples = new ArrayList<>();
    private static int contactFrames;

    private MeasureSessionStats() {
    }

    public static synchronized void reset() {
        hrSamples.clear();
        rrSamples.clear();
        hrvSamples.clear();
        contactFrames = 0;
    }

    /** 측정 중 프레임 하나. 패치에서 심박이 들어온 구간만 유효로 센다. */
    public static synchronized void addMeasureFrame(boolean contact) {
        if (contact) contactFrames++;
    }

    /** 5분 중 심박이 잡힌 비율. 데이터가 없으면 0. */
    public static synchronized double validRatio() {
        return contactFrames / (double) SESSION_FRAMES;
    }

    public static synchronized boolean hasEnoughValidData() {
        return validRatio() >= MIN_VALID_RATIO;
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
