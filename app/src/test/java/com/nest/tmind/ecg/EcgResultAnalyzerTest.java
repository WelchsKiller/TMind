package com.nest.tmind.ecg;

import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertTrue;

public class EcgResultAnalyzerTest {

    @Test
    public void falsePeaksDoNotInflateSdnn() {
        int fs = 250;
        ArrayList<Integer> peaks = new ArrayList<>();
        int cursor = 0;
        peaks.add(cursor);
        for (int i = 0; i < 80; i++) {
            int trueRr = 200 + ((i % 5) - 2) * 5;
            if (i % 6 == 0) {
                cursor += 90;
                peaks.add(cursor);
                cursor += trueRr - 90;
                peaks.add(cursor);
            } else {
                cursor += trueRr;
                peaks.add(cursor);
            }
        }

        double[] nn = EcgResultAnalyzer.normalNnMs(peaks, fs);
        double mean = 0;
        for (double v : nn) mean += v;
        mean /= nn.length;
        double var = 0;
        for (double v : nn) {
            double d = v - mean;
            var += d * d;
        }
        double sdnn = Math.sqrt(var / (nn.length - 1));

        assertTrue(nn.length >= 40);
        assertTrue(mean > 740 && mean < 860);
        assertTrue(sdnn > 5 && sdnn < 80);
    }
}
