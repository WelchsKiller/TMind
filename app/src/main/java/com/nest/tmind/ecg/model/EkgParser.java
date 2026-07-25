package com.nest.tmind.ecg.model;

import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.function.Consumer;

/** SmartPatch @W 프레임 파서 v3 (줄바꿈 없이 붙어서 와도 파싱) */
public class EkgParser {

    public static class EkgFrame {
        public final int[] wave;            // 25 samples, signed 16-bit
        @Nullable public final Integer hr;  // bpm
        @Nullable public final Integer rr;  // ms
        public EkgFrame(int[] w, @Nullable Integer hr, @Nullable Integer rr) {
            this.wave = w; this.hr = hr; this.rr = rr;
        }
    }

    private static final ArrayList<Byte> buf = new ArrayList<>(8192);

    /** Notify 바이트 누적 → '@W' 고정길이 파싱 → 프레임 방출 */
    public static synchronized void feedAscii(byte[] chunk, Consumer<EkgFrame> sink) {
        if (chunk == null || chunk.length == 0) return;
        for (byte b : chunk) buf.add(b);

        int i = 0;
        while (true) {
            // 1) '@' 위치 찾기
            while (i < buf.size() && buf.get(i) != '@') i++;
            if (i >= buf.size()) { if (i > 0) buf.subList(0, i).clear(); break; }
            if (i + 1 >= buf.size()) break;

            byte cmd = buf.get(i + 1);
            if (cmd != 'W' && cmd != 'w') { i++; continue; }

            int p = i + 2;
            final int needWaveChars = 25 * 3;
            if (buf.size() < p + needWaveChars) break;

            // 2) 파형 25개 복호화 (6-bit ASCII → 16-bit signed)
            int[] wave = new int[25];
            boolean bad = false;
            for (int s = 0; s < 25; s++) {
                if (p + 3 > buf.size()) { bad = true; break; }
                int c0 = six(buf.get(p++)), c1 = six(buf.get(p++)), c2 = six(buf.get(p++));
                if (c0 < 0 || c1 < 0 || c2 < 0) { bad = true; break; }
                int v = c0 | (c1 << 6) | (c2 << 12);
                if (v >= 32768) v -= 65536;
                wave[s] = v;
            }
            if (bad) break;

            // 3) 꼬리: stat 유무/값과 무관하게 HR(2), RR(3) 최대한 추출
            Integer hr = null, rr = null;
            int rem = buf.size() - p;

            if (rem >= 6) { // [stat][hr2][rr3]
                int stat = six(buf.get(p)); p += 1;
                int h0 = six(buf.get(p)), h1 = six(buf.get(p + 1)); p += 2;
                int r0 = six(buf.get(p)), r1 = six(buf.get(p + 1)), r2 = six(buf.get(p + 2)); p += 3;
                if (h0 >= 0 && h1 >= 0) hr = (h0 | (h1 << 6));
                if (r0 >= 0 && r1 >= 0 && r2 >= 0) rr = (r0 | (r1 << 6) | (r2 << 12));
            } else if (rem == 5) { // [hr2][rr3]
                int h0 = six(buf.get(p)), h1 = six(buf.get(p + 1)); p += 2;
                int r0 = six(buf.get(p)), r1 = six(buf.get(p + 1)), r2 = six(buf.get(p + 2)); p += 3;
                if (h0 >= 0 && h1 >= 0) hr = (h0 | (h1 << 6));
                if (r0 >= 0 && r1 >= 0 && r2 >= 0) rr = (r0 | (r1 << 6) | (r2 << 12));
            } else {
                if (rem >= 2) { // hr2 후보
                    int h0 = six(buf.get(p)), h1 = six(buf.get(p + 1));
                    if (h0 >= 0 && h1 >= 0) { hr = (h0 | (h1 << 6)); p += 2; rem -= 2; }
                }
                if (rem >= 3) { // rr3 후보
                    int r0 = six(buf.get(p)), r1 = six(buf.get(p + 1)), r2 = six(buf.get(p + 2));
                    if (r0 >= 0 && r1 >= 0 && r2 >= 0) { rr = (r0 | (r1 << 6) | (r2 << 12)); }
                }
            }

            // 4) 개행/공백 스킵
            while (p < buf.size()) {
                byte b = buf.get(p);
                if (b == '\r' || b == '\n' || b == ' ') p++; else break;
            }

            // 5) 프레임 방출 & 소비
            sink.accept(new EkgFrame(wave, hr, rr));
            buf.subList(0, p).clear();
            i = 0;
        }
        if (buf.size() > 65536) buf.clear(); // runaway 보호
    }

    private static int six(byte b) {
        int v = ((b & 0xFF) - 0x20) & 0x3F; // 0..63
        return (v >= 0 && v <= 63) ? v : -1;
    }
}
