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

    /** 측정 중 심박이 한 번이라도 들어왔으면 true. 한 번도 없으면 측정률 0%. */
    public static synchronized boolean hasMeasurementData() {
        return contactFrames > 0 || !hrSamples.isEmpty();
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
        // 안정 시 사람에게 나올 수 있는 구간만 모은다. 잡음으로 튀는 값은 버린다.
        if (hrvMs > 0 && hrvMs <= 200) {
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
        return averageOf(hrvSamples, 1, 200);
    }

    /**
     * 측정 중 모인 RR 간격으로 SDNN 을 직접 구한다.
     * 1초마다 잘린 SDNN 평균보다 결과 화면 값으로 더 맞다.
     */
    public static synchronized int sdnnFromRr() {
        if (rrSamples.size() < 8) return 0;
        double sum = 0;
        for (int v : rrSamples) sum += v;
        double mean = sum / rrSamples.size();
        double var = 0;
        for (int v : rrSamples) {
            double d = v - mean;
            var += d * d;
        }
        var /= (rrSamples.size() - 1);
        int sdnn = (int) Math.round(Math.sqrt(var));
        if (sdnn <= 0 || sdnn > 200) return 0;
        return sdnn;
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
