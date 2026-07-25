package com.nest.tmind.ecg;

import android.content.Context;

import java.util.ArrayList;
import java.util.Arrays;

/** Smart_EKG MeasureResultActivity 분석 로직 이식 */
public final class EcgResultAnalyzer {

    public static class Result {
        public float[] spike;
        public int fs;
        public int hrBpm;
        public int hrvMs;
        public int rrMs;
        public int stressScore;
        public long measuredAt;
        public boolean valid;
    }

    public static Result analyze(Context ctx) {
        Result r = new Result();
        r.measuredAt = System.currentTimeMillis();
        r.fs = EcgCapture.i().fs();
        if (r.fs <= 0) r.fs = 250;

        float[] raw = EcgCapture.i().consumeAll();
        boolean fromWave = false;

        if (raw != null && raw.length >= Math.max(r.fs, 400)) {
            Biquad hp = Biquad.highpass(r.fs, 0.67, 0.707);
            Biquad notch = Biquad.notch(r.fs, 60.0, 30.0);
            Biquad lp = Biquad.lowpass(r.fs, 18.0, 0.707);

            double[] x = new double[raw.length];
            for (int i = 0; i < raw.length; i++) {
                double v = raw[i];
                v = hp.process(v);
                v = notch.process(v);
                v = lp.process(v);
                x[i] = v;
            }

            ArrayList<Integer> rloc = detectRPeaks(x, r.fs);
            if (rloc != null && rloc.size() >= 2) {
                float[] avgBeat = averageBeat(x, r.fs, 0.20, 0.40, 200, rloc);
                if (avgBeat != null) {
                    avgBeat = smooth(avgBeat, 3);
                    r.rrMs = computeAvgRrMs(rloc, r.fs);
                    r.hrBpm = (r.rrMs > 0) ? Math.round(60000f / r.rrMs) : 0;
                    r.hrvMs = computeHrvMs(rloc, r.fs);
                    final int STORED_LEN = 400;
                    r.spike = makeIconSpikeFrom(avgBeat, STORED_LEN);
                    fromWave = true;
                }
            }
        }

        // 세션 중 기기 HR 평균이 있으면 결과 표시에 우선 사용
        int sessionHr = MeasureSessionStats.averageHr();
        int sessionHrv = MeasureSessionStats.averageHrvMs();
        if (sessionHr > 0) {
            r.hrBpm = sessionHr;
            int sessionRr = MeasureSessionStats.averageRrMs();
            if (sessionRr > 0) r.rrMs = sessionRr;
            else r.rrMs = Math.round(60000f / sessionHr);
        }
        if (sessionHrv > 0) {
            r.hrvMs = sessionHrv;
        }

        // 파형 분석 실패해도 세션 평균이 있으면 결과로 저장 (재측정/-- 공백 방지)
        if (!fromWave && (sessionHr > 0 || sessionHrv > 0)) {
            r.spike = new float[0];
            fromWave = true;
        }

        if (!fromWave && r.hrBpm <= 0 && r.hrvMs <= 0) {
            r.valid = false;
            return r;
        }

        int hrvForScore = 0;
        r.stressScore = 0;
        if (r.hrvMs > 0) {
            hrvForScore = Math.max(20, Math.min(160, r.hrvMs));
            float scoreF = ((160f - hrvForScore) / (160f - 20f)) * 100f;
            r.stressScore = Math.round(Math.max(0f, Math.min(100f, scoreF)));
        }

        r.valid = true;
        LastEcgResult.updateAndSave(ctx, r.spike, r.fs, r.hrBpm, r.hrvMs, r.rrMs,
                r.stressScore, r.measuredAt);
        return r;
    }

    private static float[] makeIconSpikeFrom(float[] avgBeat, int totalLenPx) {
        if (avgBeat == null || avgBeat.length == 0) return new float[0];
        int srcLen = avgBeat.length;
        int outLen = (totalLenPx > 0) ? totalLenPx : srcLen;
        float[] out = new float[outLen];
        float mean = 0f;
        for (float v : avgBeat) mean += v;
        mean /= srcLen;
        for (int i = 0; i < outLen; i++) {
            double t = i * (srcLen - 1) / (double) (outLen - 1);
            int i0 = (int) Math.floor(t);
            int i1 = Math.min(srcLen - 1, i0 + 1);
            double frac = t - i0;
            float s0 = avgBeat[i0] - mean;
            float s1 = avgBeat[i1] - mean;
            out[i] = (float) (s0 * (1.0 - frac) + s1 * frac);
        }
        return out;
    }

    private static ArrayList<Integer> detectRPeaks(double[] x, int fs) {
        ArrayList<Integer> idx = new ArrayList<>();
        double ema = 0, noise = 0;
        int refr = Math.round(0.260f * fs);
        int last = -refr;
        for (int i = 0; i < x.length; i++) {
            ema = 0.995 * ema + 0.005 * x[i];
            double z = x[i] - ema;
            noise = 0.995 * noise + 0.005 * Math.abs(z);
            double thr = Math.max(0.08, noise * 3.0);
            if (z > thr && i - last > refr) {
                int win = Math.max(1, Math.round(0.03f * fs));
                int m = i, mEnd = Math.min(x.length - 1, i + win);
                for (int j = i; j <= mEnd; j++) if (x[j] > x[m]) m = j;
                idx.add(m);
                last = m;
            }
        }
        return idx;
    }

    private static float[] averageBeat(double[] x, int fs, double preSec, double postSec,
                                       int outLen, ArrayList<Integer> rloc) {
        int pre = (int) Math.round(preSec * fs);
        int post = (int) Math.round(postSec * fs);
        if (rloc == null || rloc.size() < 2) return null;
        float[] acc = new float[outLen];
        int cnt = 0;
        for (int r : rloc) {
            int a = r - pre, b = r + post;
            if (a < 0 || b >= x.length) continue;
            int win = Math.max(1, (int) Math.round(0.03 * fs));
            int rr = r;
            for (int j = Math.max(0, r - win); j <= Math.min(x.length - 1, r + win); j++)
                if (x[j] > x[rr]) rr = j;
            a = rr - pre;
            b = rr + post;
            if (a < 0 || b >= x.length) continue;
            for (int k = 0; k < outLen; k++) {
                double t = a + (b - a) * (k / (double) (outLen - 1));
                int i0 = (int) Math.floor(t);
                int i1 = Math.min(x.length - 1, i0 + 1);
                double frac = t - i0;
                acc[k] += (float) (x[i0] * (1 - frac) + x[i1] * frac);
            }
            cnt++;
        }
        if (cnt == 0) return null;
        for (int k = 0; k < outLen; k++) acc[k] /= cnt;
        float mean = 0f;
        for (float v : acc) mean += v;
        mean /= outLen;
        for (int k = 0; k < outLen; k++) acc[k] -= mean;
        return acc;
    }

    private static int computeAvgRrMs(ArrayList<Integer> rloc, int fs) {
        if (rloc == null || rloc.size() < 2) return 0;
        ArrayList<Double> rrMs = new ArrayList<>();
        for (int i = 1; i < rloc.size(); i++) {
            int d = rloc.get(i) - rloc.get(i - 1);
            if (d <= 0) continue;
            double ms = 1000.0 * d / fs;
            // 생리적 RR만 평균에 포함 (HR 약 40~200 bpm)
            if (ms < 300.0 || ms > 1500.0) continue;
            rrMs.add(ms);
        }
        if (rrMs.isEmpty()) return 0;
        return (int) Math.round(trimmedMean(rrMs));
    }

    /** 상·하위 10% 제거 후 평균 (이상치로 인한 과소/과대 HR 방지) */
    private static double trimmedMean(ArrayList<Double> values) {
        if (values == null || values.isEmpty()) return 0;
        double[] arr = new double[values.size()];
        for (int i = 0; i < values.size(); i++) arr[i] = values.get(i);
        Arrays.sort(arr);
        int n = arr.length;
        int trim = Math.max(0, n / 10);
        if (n - 2 * trim < 1) {
            trim = 0;
        }
        double sum = 0;
        int cnt = 0;
        for (int i = trim; i < n - trim; i++) {
            sum += arr[i];
            cnt++;
        }
        return cnt == 0 ? 0 : sum / cnt;
    }

    private static int computeHrvMs(ArrayList<Integer> rloc, int fs) {
        if (rloc == null || rloc.size() < 3) return 0;
        ArrayList<Double> rrMsList = new ArrayList<>();
        for (int i = 1; i < rloc.size(); i++) {
            int dSamples = rloc.get(i) - rloc.get(i - 1);
            if (dSamples <= 0) continue;
            double dMs = 1000.0 * dSamples / fs;
            if (dMs < 300.0 || dMs > 2000.0) continue;
            rrMsList.add(dMs);
        }
        if (rrMsList.size() < 2) return 0;
        double[] tmp = new double[rrMsList.size()];
        for (int i = 0; i < rrMsList.size(); i++) tmp[i] = rrMsList.get(i);
        Arrays.sort(tmp);
        double median = (tmp.length % 2 == 1)
                ? tmp[tmp.length / 2]
                : (tmp[tmp.length / 2 - 1] + tmp[tmp.length / 2]) / 2.0;
        ArrayList<Double> filtered = new ArrayList<>();
        for (double v : rrMsList) {
            if (v < median * 0.5 || v > median * 1.8) continue;
            filtered.add(v);
        }
        if (filtered.size() < 2) filtered = rrMsList;
        if (filtered.size() < 2) return 0;
        double sum = 0.0;
        for (double v : filtered) sum += v;
        double mean = sum / filtered.size();
        double var = 0.0;
        for (double v : filtered) {
            double diff = v - mean;
            var += diff * diff;
        }
        var /= filtered.size();
        int sdnnMs = (int) Math.round(Math.sqrt(var));
        if (sdnnMs < 0) sdnnMs = 0;
        if (sdnnMs > 300) sdnnMs = 300;
        return sdnnMs;
    }

    private static float[] smooth(float[] s, int n) {
        if (n <= 1) return s;
        float[] o = Arrays.copyOf(s, s.length);
        for (int i = 1; i < s.length - 1; i++)
            o[i] = (s[i - 1] + s[i] + s[i + 1]) / 3f;
        return o;
    }

    private static class Biquad {
        double b0, b1, b2, a1, a2, z1, z2;

        Biquad(double b0, double b1, double b2, double a0, double a1, double a2) {
            this.b0 = b0 / a0;
            this.b1 = b1 / a0;
            this.b2 = b2 / a0;
            this.a1 = a1 / a0;
            this.a2 = a2 / a0;
        }

        double process(double x) {
            double y = b0 * x + z1;
            z1 = b1 * x - a1 * y + z2;
            z2 = b2 * x - a2 * y;
            return y;
        }

        static Biquad lowpass(int fs, double fc, double q) {
            double w0 = 2 * Math.PI * fc / fs, c = Math.cos(w0), s = Math.sin(w0), a = s / (2 * q);
            return new Biquad((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + a, -2 * c, 1 - a);
        }

        static Biquad highpass(int fs, double fc, double q) {
            double w0 = 2 * Math.PI * fc / fs, c = Math.cos(w0), s = Math.sin(w0), a = s / (2 * q);
            return new Biquad((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + a, -2 * c, 1 - a);
        }

        static Biquad notch(int fs, double f0, double q) {
            double w0 = 2 * Math.PI * f0 / fs, c = Math.cos(w0), s = Math.sin(w0), a = s / (2 * q);
            return new Biquad(1, -2 * c, 1, 1 + a, -2 * c, 1 - a);
        }
    }
}
