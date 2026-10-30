package com.nest.tmind.ecg;

import android.content.Context;
import android.util.Log;

import com.nest.tmind.util.ResourceManager;

import java.util.ArrayList;
import java.util.Arrays;

/** Smart_EKG MeasureResultActivity 분석 로직 이식 (+ 리소스 적응형 연산) */
public final class EcgResultAnalyzer {

    private static final String TAG = "EcgResultAnalyzer";
    /** 패치 미부착·무신호는 이보다 피크가 거의 없다. 5분 실측이면 훨씬 많다. */
    private static final int MIN_R_PEAKS = 20;
    /** 접촉이 있어야 캡처되므로, 15초 미만이면 측정 실패로 본다. */
    private static final int MIN_SIGNAL_SEC = 15;

    public static class Result {
        public float[] spike;
        public int fs;
        public int hrBpm;
        public int hrvMs;
        public int rrMs;
        public int stressScore;
        public long measuredAt;
        public boolean valid;
        public String resourceLevel;
    }

    public static Result analyze(Context ctx) {
        Result r = new Result();
        r.measuredAt = System.currentTimeMillis();
        r.fs = EcgCapture.i().fs();
        if (r.fs <= 0) r.fs = 250;

        ResourceManager.Level level = ResourceManager.getLevel(ctx);
        r.resourceLevel = level.name();
        // 임상 HRV 는 샘플을 건너뛰면 R파 위치가 흔들려 SDNN 이 커진다. 항상 원본 샘플링으로 계산한다.
        int stride = 1;
        boolean heavy = ResourceManager.useHeavyFilters(level);
        int spikeLen = ResourceManager.spikeLength(level);
        Log.i(TAG, "analyze resource=" + level.name()
                + " battery=" + ResourceManager.batteryPercent(ctx)
                + " stride=" + stride);

        float[] raw = EcgCapture.i().consumeAll();
        LastEcgResult.setLastRawSignal(raw);
        int minSamples = Math.max(r.fs * MIN_SIGNAL_SEC, 400);
        if (raw == null || raw.length < minSamples) {
            Log.i(TAG, "analyze invalid: rawSamples=" + (raw == null ? 0 : raw.length)
                    + " need>=" + minSamples);
            r.valid = false;
            return r;
        }

        float[] sampled = downsample(raw, stride);
        // 결과 화면 표시용: 필터/평균 박동 아이콘이 아니라 Raw 최근 구간
        float[] displayWave = extractDisplaySegment(sampled, Math.max(r.fs * 4, spikeLen));

        Biquad hp = Biquad.highpass(r.fs, 0.67, 0.707);
        Biquad notch = heavy ? Biquad.notch(r.fs, 60.0, 30.0) : null;
        Biquad lp = Biquad.lowpass(r.fs, 18.0, 0.707);

        double[] x = new double[sampled.length];
        for (int i = 0; i < sampled.length; i++) {
            double v = sampled[i];
            v = hp.process(v);
            if (notch != null) v = notch.process(v);
            v = lp.process(v);
            x[i] = v;
        }

        if (!isSignalAlive(x)) {
            Log.i(TAG, "analyze invalid: flat/no ECG energy");
            r.valid = false;
            return r;
        }

        int fsEff = Math.max(1, r.fs / stride);
        ArrayList<Integer> rloc = detectRPeaks(x, fsEff);
        int peakCount = rloc != null ? rloc.size() : 0;
        if (peakCount < MIN_R_PEAKS) {
            Log.i(TAG, "analyze invalid: rPeaks=" + peakCount + " need>=" + MIN_R_PEAKS);
            r.valid = false;
            return r;
        }

        // UI/prefs 파형은 Raw 구간. SDNN·HR 은 아래 필터 신호의 R-peak 로만 계산.
        r.spike = displayWave;
        r.rrMs = computeAvgRrMs(rloc, fsEff);
        r.hrBpm = (r.rrMs > 0) ? Math.round(60000f / r.rrMs) : 0;
        r.hrvMs = computeHrvMs(rloc, fsEff);

        if (r.hrBpm < 40 || r.hrBpm > 180 || r.hrvMs <= 0) {
            Log.i(TAG, "analyze invalid: hr=" + r.hrBpm + " hrv=" + r.hrvMs);
            r.valid = false;
            return r;
        }

        if (r.spike == null) r.spike = new float[0];

        r.stressScore = 0;
        int hrvForScore = Math.max(20, Math.min(160, r.hrvMs));
        float scoreF = ((160f - hrvForScore) / (160f - 20f)) * 100f;
        r.stressScore = Math.round(Math.max(0f, Math.min(100f, scoreF)));

        r.valid = true;
        LastEcgResult.updateAndSave(ctx, r.spike, r.fs, r.hrBpm, r.hrvMs, r.rrMs,
                r.stressScore, r.measuredAt);
        return r;
    }

    /** 패치 미부착·평탄 잡음은 에너지가 거의 없다. */
    private static boolean isSignalAlive(double[] x) {
        if (x == null || x.length < 64) return false;
        double mean = 0;
        for (double v : x) mean += v;
        mean /= x.length;
        double var = 0;
        for (double v : x) {
            double d = v - mean;
            var += d * d;
        }
        var /= x.length;
        return Math.sqrt(var) >= 0.02;
    }

    /** 결과 화면용 Raw 구간. 진폭 정규화 없이 DC만 제거해 용지 이득(mm/mV)으로 그린다. */
    private static float[] extractDisplaySegment(float[] sampled, int maxLen) {
        if (sampled == null || sampled.length < 16) return null;
        int len = Math.min(sampled.length, Math.max(64, maxLen));
        int start = sampled.length - len;
        float[] out = new float[len];
        System.arraycopy(sampled, start, out, 0, len);
        float mean = 0f;
        for (float v : out) mean += v;
        mean /= len;
        for (int i = 0; i < len; i++) out[i] -= mean;
        return out;
    }

    private static float[] downsample(float[] raw, int stride) {
        if (raw == null || stride <= 1) return raw;
        int n = (raw.length + stride - 1) / stride;
        float[] out = new float[n];
        for (int i = 0, j = 0; i < raw.length; i += stride, j++) {
            out[j] = raw[i];
        }
        return out;
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
        // 안정 시 T파가 R파로 잡히면 짧은 간격과 긴 간격이 섞여 SDNN 이 수백 ms 가 된다.
        // 400ms 안에는 다음 QRS 가 오지 않는 안정 심박(150bpm 이하)만 본다.
        int refr = Math.round(0.400f * fs);
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
        double[] nn = normalNnMs(rloc, fs);
        if (nn.length == 0) return 0;
        ArrayList<Double> values = new ArrayList<>();
        for (double v : nn) values.add(v);
        return (int) Math.round(trimmedMean(values));
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

    /**
     * 정상 박동(NN)만 남긴 RR(ms).
     * 중앙값의 ±20% 밖, 또는 직전 정상 박동과 20% 넘게 차이 나는 간격은
     * T파·잡음으로 보고 버린다. 예전 50%~180% 구간은 그 잡음을 살려 SDNN 이 300에 걸렸다.
     */
    static double[] normalNnMs(ArrayList<Integer> rloc, int fs) {
        if (rloc == null || rloc.size() < 3 || fs <= 0) return new double[0];
        ArrayList<Double> rrMsList = new ArrayList<>();
        for (int i = 1; i < rloc.size(); i++) {
            int dSamples = rloc.get(i) - rloc.get(i - 1);
            if (dSamples <= 0) continue;
            double dMs = 1000.0 * dSamples / fs;
            if (dMs < 333.0 || dMs > 1500.0) continue;
            rrMsList.add(dMs);
        }
        if (rrMsList.size() < 2) return new double[0];
        double[] tmp = new double[rrMsList.size()];
        for (int i = 0; i < rrMsList.size(); i++) tmp[i] = rrMsList.get(i);
        Arrays.sort(tmp);
        double median = (tmp.length % 2 == 1)
                ? tmp[tmp.length / 2]
                : (tmp[tmp.length / 2 - 1] + tmp[tmp.length / 2]) / 2.0;
        ArrayList<Double> band = new ArrayList<>();
        for (double v : rrMsList) {
            if (v < median * 0.80 || v > median * 1.20) continue;
            band.add(v);
        }
        if (band.size() < 2) return new double[0];
        ArrayList<Double> nn = new ArrayList<>();
        double prev = band.get(0);
        nn.add(prev);
        for (int i = 1; i < band.size(); i++) {
            double v = band.get(i);
            if (Math.abs(v - prev) / prev <= 0.20) {
                nn.add(v);
                prev = v;
            }
        }
        if (nn.size() < 8) return new double[0];
        double[] out = new double[nn.size()];
        for (int i = 0; i < nn.size(); i++) out[i] = nn.get(i);
        return out;
    }

    private static int computeHrvMs(ArrayList<Integer> rloc, int fs) {
        double[] nn = normalNnMs(rloc, fs);
        if (nn.length < 8) return 0;
        double sum = 0.0;
        for (double v : nn) sum += v;
        double mean = sum / nn.length;
        double var = 0.0;
        for (double v : nn) {
            double diff = v - mean;
            var += diff * diff;
        }
        var /= (nn.length - 1);
        int sdnnMs = (int) Math.round(Math.sqrt(var));
        if (sdnnMs < 0) sdnnMs = 0;
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
