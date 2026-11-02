package com.nest.tmind.ecg;

/** 2차 IIR. 화면 표시·R파 검출에 같은 계수를 쓴다. */
public final class Biquad {
    private double b0, b1, b2, a1, a2, z1, z2;

    public Biquad(double b0, double b1, double b2, double a0, double a1, double a2) {
        this.b0 = b0 / a0;
        this.b1 = b1 / a0;
        this.b2 = b2 / a0;
        this.a1 = a1 / a0;
        this.a2 = a2 / a0;
    }

    public double process(double x) {
        double y = b0 * x + z1;
        z1 = b1 * x - a1 * y + z2;
        z2 = b2 * x - a2 * y;
        return y;
    }

    public void reset() {
        z1 = 0;
        z2 = 0;
    }

    public static Biquad lowpass(int fs, double fc, double q) {
        double w0 = 2 * Math.PI * fc / fs, c = Math.cos(w0), s = Math.sin(w0), a = s / (2 * q);
        return new Biquad((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + a, -2 * c, 1 - a);
    }

    public static Biquad highpass(int fs, double fc, double q) {
        double w0 = 2 * Math.PI * fc / fs, c = Math.cos(w0), s = Math.sin(w0), a = s / (2 * q);
        return new Biquad((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + a, -2 * c, 1 - a);
    }

    public static Biquad notch(int fs, double f0, double q) {
        double w0 = 2 * Math.PI * f0 / fs, c = Math.cos(w0), s = Math.sin(w0), a = s / (2 * q);
        return new Biquad(1, -2 * c, 1, 1 + a, -2 * c, 1 - a);
    }
}
