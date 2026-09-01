package com.nest.tmind.ecg;

import java.util.Random;

/**
 * BLE 기기 없이 테스트할 때 쓰는 합성 ECG 생성기.
 *
 * 실제 기기와 같은 경로를 태우기 위해 지표를 직접 만들지 않고 파형만 만든다.
 * 생성한 샘플을 {@link EcgCapture} 에 적재하면 이후 HR·HRV·RR·스트레스 산출과
 * 원본 신호 보관은 {@link EcgResultAnalyzer} 가 실측과 동일하게 처리한다.
 */
public final class EcgSampleGenerator {

    public static final int DEFAULT_FS = 250;
    public static final int DEFAULT_DURATION_SEC = 5 * 60;

    /** 한 번에 EcgCapture 로 넘길 샘플 수. 실측 BLE 패킷과 비슷한 크기로 쪼갠다. */
    private static final int CHUNK = 250;

    private EcgSampleGenerator() {
    }

    /**
     * 목표 심박수·SDNN 을 가진 5분 신호를 만들어 캡처 버퍼를 채운다.
     *
     * @param targetHrBpm 목표 평균 심박수(bpm)
     * @param targetSdnnMs 목표 RR 표준편차(ms)
     */
    public static void fillCapture(int targetHrBpm, int targetSdnnMs) {
        fillCapture(targetHrBpm, targetSdnnMs, DEFAULT_FS, DEFAULT_DURATION_SEC);
    }

    public static void fillCapture(int targetHrBpm, int targetSdnnMs, int fs, int durationSec) {
        EcgCapture capture = EcgCapture.i();
        capture.consumeAll();
        capture.setFs(fs);
        float[] signal = generate(targetHrBpm, targetSdnnMs, fs, durationSec);
        for (int offset = 0; offset < signal.length; offset += CHUNK) {
            int len = Math.min(CHUNK, signal.length - offset);
            float[] chunk = new float[len];
            System.arraycopy(signal, offset, chunk, 0, len);
            capture.add(chunk);
        }
    }

    /**
     * PQRST 파형을 가우시안 합으로 합성하고 기저선 변동·잡음을 섞는다.
     * RR 간격은 목표 SDNN 만큼 흔들어 HRV 가 실제처럼 나오게 한다.
     */
    public static float[] generate(int targetHrBpm, int targetSdnnMs, int fs, int durationSec) {
        int hr = clampInt(targetHrBpm, 40, 180);
        int sdnn = clampInt(targetSdnnMs, 0, 200);
        int total = Math.max(fs, fs * durationSec);
        float[] out = new float[total];

        Random rnd = new Random();
        double meanRrSec = 60.0 / hr;
        double sdnnSec = sdnn / 1000.0;

        // 기저선 변동: 호흡에 의한 저주파 흔들림
        double baselineHz = 0.2 + rnd.nextDouble() * 0.1;
        double baselinePhase = rnd.nextDouble() * Math.PI * 2;

        double beatSec = meanRrSec;
        while (beatSec < durationSec) {
            writeBeat(out, fs, beatSec);
            double jitter = rnd.nextGaussian() * sdnnSec;
            // RR 이 생리적 범위를 벗어나면 분석기가 걸러내므로 미리 제한한다
            double rr = clampDouble(meanRrSec + jitter, 0.34, 1.45);
            beatSec += rr;
        }

        for (int i = 0; i < total; i++) {
            double t = i / (double) fs;
            double baseline = 0.05 * Math.sin(2 * Math.PI * baselineHz * t + baselinePhase);
            double noise = rnd.nextGaussian() * 0.01;
            out[i] += (float) (baseline + noise);
        }
        return out;
    }

    /** R 피크가 rSec 에 오도록 PQRST 성분을 더한다. 진폭 단위는 mV. */
    private static void writeBeat(float[] out, int fs, double rSec) {
        addGaussian(out, fs, rSec - 0.20, 0.12, 0.025);   // P
        addGaussian(out, fs, rSec - 0.03, -0.15, 0.010);  // Q
        addGaussian(out, fs, rSec, 1.00, 0.012);          // R
        addGaussian(out, fs, rSec + 0.03, -0.25, 0.012);  // S
        addGaussian(out, fs, rSec + 0.20, 0.30, 0.040);   // T
    }

    private static void addGaussian(float[] out, int fs, double centerSec,
                                    double amplitude, double widthSec) {
        int center = (int) Math.round(centerSec * fs);
        int half = (int) Math.ceil(widthSec * 4 * fs);
        int from = Math.max(0, center - half);
        int to = Math.min(out.length - 1, center + half);
        double denom = 2 * widthSec * widthSec;
        for (int i = from; i <= to; i++) {
            double dt = (i - center) / (double) fs;
            out[i] += (float) (amplitude * Math.exp(-(dt * dt) / denom));
        }
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double clampDouble(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
