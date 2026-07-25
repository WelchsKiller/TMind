package com.nest.tmind.ecg;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

public class EcgSurfaceView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    // ====== [필드] 결과 화면용 정지 모드 ======
    private volatile boolean staticMode = false;
    private float[] staticSamples = null;
    private int staticFs = 250;

    public EcgSurfaceView(Context c) {
        this(c, null);
    }

    public EcgSurfaceView(Context c, AttributeSet a) {
        super(c, a);
        getHolder().addCallback(this);
        initPaints();
    }

    private volatile boolean drawGrid = true;
    private volatile boolean drawAxisLabels = true;

    public void setDrawGrid(boolean enabled) {
        this.drawGrid = enabled;
        repaintGrid();
    }

    public void setDrawAxisLabels(boolean enabled) {
        this.drawAxisLabels = enabled;
        repaintGrid();
    }

    private volatile float paperSpeedMmPerSec = 12.5f; // 기본 12.5
    private volatile float gainMmPerMv = 10f;
    private float pxPerMmX, pxPerMmY;

    private Thread renderThread;
    private volatile boolean running = false;
    private Bitmap back;
    private Canvas backC;
    private volatile int sampleRateHz = 250;
    private final ConcurrentLinkedQueue<Float> q = new ConcurrentLinkedQueue<>();
    private float sweepX = 0f;
    private Float lastY = null;
    private long lastGridRepaintMs = 0;

    // 모서리 라운드용
    private volatile float cornerRadiusPx = 0f;
    private final Path cornerPath = new Path();
    private final RectF drawRect = new RectF();
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ====== 표시용 스무딩용 필드 (스트리밍용) ======
    private float displaySmoothAlpha = 0.35f;   // 0.0~1.0, 작을수록 더 부드럽게
    private Float lastSmoothY = null;

    public void setSampleRateHz(int sr) {
        sampleRateHz = Math.max(50, Math.min(1000, sr));
    }

    public void setSpeed(float mmps) {
        paperSpeedMmPerSec = Math.max(4f, Math.min(50f, mmps));
        repaintGrid();
    }

    public void setGain(float mmPerMv) {
        gainMmPerMv = Math.max(2f, mmPerMv);
        repaintGrid();
    }

    private volatile float waveCenterRatio = 0.5f;

    public void setWaveCenterRatio(float ratio) {
        waveCenterRatio = Math.max(0.35f, Math.min(0.65f, ratio));
        repaintGrid();
    }

    public void setSeniorGridStyle() {
        gridThin.setColor(Color.parseColor("#E8F0F0"));
        gridBold.setColor(Color.parseColor("#C5DEDE"));
        repaintGrid();
    }

    public void setFilters(boolean hpf, boolean lpf) { /* not used */ }

    public void addSamples(float[] mv) {
        if (mv == null || mv.length == 0) return;

        // 🔹 여기서 먼저 곡선용 중간 샘플을 생성
        float[] smooth = roundifySegment(mv);

        for (float v : smooth) {
            q.add(v);
        }
    }


    @Override
    public void surfaceCreated(SurfaceHolder h) {
        computeMmToPx();
        alloc();
        startLoop();
    }

    @Override
    public void surfaceChanged(SurfaceHolder h, int f, int w, int hgt) {
        rebuildCornerPath();
        computeMmToPx();
        alloc();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder h) {
        stopLoop();
    }

    private void startLoop() {
        running = true;
        renderThread = new Thread(this, "ECG");
        renderThread.start();
    }

    private void stopLoop() {
        running = false;
        if (renderThread != null) try {
            renderThread.join();
        } catch (InterruptedException ignored) {
        }
    }

    private void computeMmToPx() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float xdpi = dm.xdpi > 0 ? dm.xdpi : dm.densityDpi;
        float ydpi = dm.ydpi > 0 ? dm.ydpi : dm.densityDpi;
        pxPerMmX = xdpi / 25.4f;
        pxPerMmY = ydpi / 25.4f;
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private void alloc() {
        alloc(getWidth(), getHeight());
    }

    private void alloc(int w, int h) {
        if (w <= 0 || h <= 0) return;
        back = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        backC = new Canvas(back);
        drawGrid(backC);
        sweepX = 0f;
        lastY = null;
        lastSmoothY = null;
    }

    private void ensureBackBuffer(int w, int h) {
        if (w <= 0 || h <= 0) return;
        if (back != null && back.getWidth() == w && back.getHeight() == h) return;
        alloc(w, h);
    }

    private float getWaveCenterY(int viewHeight) {
        return viewHeight * waveCenterRatio;
    }

    private void repaintGrid() {
        lastGridRepaintMs = 0;
    }

    private final Paint gridThin = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridBold = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wave = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clear = new Paint();
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);

    private void initPaints() {
        gridThin.setColor(Color.rgb(235, 204, 204));
        gridThin.setStrokeWidth(1f);
        gridBold.setColor(Color.rgb(220, 110, 110));
        gridBold.setStrokeWidth(2f);
        axisText.setColor(Color.DKGRAY);
        axisText.setTextSize(28f);

        // ECG 파형 스타일
        wave.setColor(Color.parseColor("#1064D9"));
        wave.setStrokeWidth(6f);
        wave.setStyle(Paint.Style.STROKE);
        wave.setStrokeCap(Paint.Cap.ROUND);          // 선 끝 둥글게
        wave.setStrokeJoin(Paint.Join.ROUND);        // 꺾이는 부분 둥글게
        wave.setPathEffect(new CornerPathEffect(dpToPx(4f))); // 전체적으로 곡선 느낌

        bg.setColor(Color.WHITE);  // 배경색
        bg.setStyle(Paint.Style.FILL);
        // clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    private void drawGrid(Canvas c) {
        c.drawColor(Color.WHITE, PorterDuff.Mode.SRC);
        int w = c.getWidth(), h = c.getHeight();

        if (drawGrid) {
            float mm = pxPerMmX, big = 5f * mm;
            for (float x = 0; x <= w; x += mm) c.drawLine(x, 0, x, h, gridThin);
            for (float y = 0; y <= h; y += mm) c.drawLine(0, y, w, y, gridThin);
            for (float x = 0; x <= w; x += big) c.drawLine(x, 0, x, h, gridBold);
            for (float y = 0; y <= h; y += big) c.drawLine(0, y, w, y, gridBold);

            float baseline = getWaveCenterY(h);
            Paint base = new Paint(gridBold);
            base.setStrokeWidth(2.5f);
            c.drawLine(0, baseline, w, baseline, base);
        }

        if (drawAxisLabels) {
            float baseline = getWaveCenterY(h);
            for (float mv = -2f; mv <= 2f; mv += 0.5f) {
                float y = baseline - mv * gainMmPerMv * pxPerMmY;
                c.drawText(String.format("%.1f mV", mv), 10, y - 6, axisText);
            }
            float mm = pxPerMmX, big = 5f * mm;
            float secPerBig = (5f) / paperSpeedMmPerSec;
            for (int i = 0; i <= (int) (w / big); i++) {
                float x = i * big;
                c.drawText(String.format("%.1f s", i * secPerBig), x + 6, h - 8, axisText);
            }
        }
        lastGridRepaintMs = SystemClock.uptimeMillis();
    }

    @Override
    public void run() {
        long last = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            if (now - last < 12_000_000) { // 약 12ms
                try {
                    Thread.sleep(2);
                } catch (Exception ignored) {
                }
                continue;
            }
            last = now;

            SurfaceHolder holder = getHolder();
            Canvas sc = holder.lockCanvas();
            if (sc == null) continue;

            try {
                int cw = sc.getWidth();
                int ch = sc.getHeight();

                if (staticMode) {
                    sc.drawColor(Color.WHITE, PorterDuff.Mode.SRC);
                    drawStatic(sc);
                } else {
                    ensureBackBuffer(cw, ch);
                    if (backC == null) continue;
                    renderWave(backC);
                    sc.drawColor(Color.WHITE, PorterDuff.Mode.SRC);
                    if (cornerRadiusPx > 0f) {
                        sc.save();
                        rebuildCornerPathForSize(cw, ch);
                        sc.clipPath(cornerPath);
                    }
                    sc.drawBitmap(back, 0, 0, null);
                    if (cornerRadiusPx > 0f) {
                        sc.drawPath(cornerPath, borderPaint);
                        sc.restore();
                    }
                }
            } finally {
                holder.unlockCanvasAndPost(sc);
            }
        }
    }

    private float mvToY(float mv, float centerY) {
        return centerY - mv * gainMmPerMv * pxPerMmY;
    }

    // 스트리밍용 y 스무딩 (IIR 1차 로우패스)
    private float smoothStreamingY(float yRaw) {
        if (lastSmoothY == null) {
            lastSmoothY = yRaw;
            return yRaw;
        }
        float y = lastSmoothY + displaySmoothAlpha * (yRaw - lastSmoothY);
        lastSmoothY = y;
        return y;
    }

    // EcgSurfaceView 안의 기존 renderWave 전체를 이걸로 교체
    private void renderWave(Canvas c) {
        if (backC == null || back == null) {
            alloc();
            if (backC == null) return;
        }

        int w = c.getWidth(), h = c.getHeight();
        float cy = getWaveCenterY(h);

        if (sweepX >= w) {
            drawGrid(c);
            sweepX = 0f;
            lastY = null;
        }

        if (SystemClock.uptimeMillis() - lastGridRepaintMs > 20_000) {
            drawGrid(c);
            lastGridRepaintMs = SystemClock.uptimeMillis();
        }

        float pxPerSec = paperSpeedMmPerSec * pxPerMmX;
        float dx = pxPerSec / sampleRateHz;
        int max = Math.min(q.size(), Math.max(1, (int) ((w - sweepX) / dx)));

        RectF band = new RectF(sweepX, 0, Math.min(w, sweepX + max * dx + 3), h);
        c.drawRect(band, bg);

        // 그리드 다시
        if (drawGrid) {
            float mm = pxPerMmX, big = 5f * mm;
            for (float x = band.left - (band.left % mm); x <= band.right; x += mm)
                c.drawLine(x, band.top, x, band.bottom, gridThin);
            for (float y = band.top - (band.top % mm); y <= band.bottom; y += mm)
                c.drawLine(band.left, y, band.right, y, gridThin);
            for (float x = band.left - (band.left % big); x <= band.right; x += big)
                c.drawLine(x, band.top, x, band.bottom, gridBold);
            for (float y = band.top - (band.top % big); y <= band.bottom; y += big)
                c.drawLine(band.left, y, band.right, y, gridBold);
        }

        if (max <= 0) return;

        // 이번 프레임에서 그릴 샘플들을 한 번에 꺼내서 y 배열로 만든다
        float[] xs = new float[max];
        float[] ys = new float[max];

        float x = sweepX;
        int n = 0;
        for (int i = 0; i < max; i++) {
            Float mv = q.poll();
            if (mv == null) break;

            float y = mvToY(mv, cy);
            xs[n] = x;
            ys[n] = y;

            x += dx;
            n++;
        }

        if (n == 0) {
            sweepX = x;
            return;
        }

        // 🔥 여기서 y값들을 화면용으로 아주 부드럽게 만든다
        //     passes를 4~6 사이로 바꿔보면서 느낌 맞추기
        smoothYs(ys, n, 5);

        Path path = new Path();
        path.moveTo(xs[0], ys[0]);
        for (int i = 1; i < n; i++) {
            path.lineTo(xs[i], ys[i]);
        }

        c.drawPath(path, wave);

        lastY = ys[n - 1];
        sweepX = x;
    }



    public void clearStreaming() {
        q.clear();
        sweepX = 0f;
        lastY = null;
        lastSmoothY = null;
        if (backC != null) {
            drawGrid(backC);
        }
    }

    public void setWaveColor(int color) {
        wave.setColor(color);
    }

    /**
     * 결과 화면: 정지 파형을 한 번에 채워 그립니다.
     */
    public void showStaticWave(float[] samples, int fs) {
        if (samples == null || samples.length == 0) return;

        staticFs = Math.max(50, Math.min(1000, fs));

        // 🔹 결과 화면용 파형도 중간 샘플을 채워서 곡선처럼 보이게
        float[] smooth = roundifySegment(samples);
        staticSamples = smooth;

        staticMode = true;

        SurfaceHolder sh = getHolder();
        Canvas sc = sh.lockCanvas();
        if (sc != null) {
            drawStatic(sc);
            sh.unlockCanvasAndPost(sc);
        }
    }

    /**
     * 화면 표시용으로 y 값들을 아주 강하게 부드럽게 만든다.
     * (원본 데이터는 건드리지 않고, 그리기 직전에만 사용)
     */
    private void smoothYs(float[] ys, int n, int passes) {
        if (ys == null || n <= 2) return;

        float[] tmp = new float[n];

        for (int it = 0; it < passes; it++) {
            // 양 끝은 그대로 둔다
            tmp[0] = ys[0];
            tmp[n - 1] = ys[n - 1];

            // 3-탭 필터 (1,4,1)/6 를 여러 번 적용하면 상당히 둥글어진다
            for (int i = 1; i < n - 1; i++) {
                float v0 = ys[i - 1];
                float v1 = ys[i];
                float v2 = ys[i + 1];
                tmp[i] = (v0 + 4f * v1 + v2) / 6f;
            }

            System.arraycopy(tmp, 0, ys, 0, n);
        }
    }


    /**
     * 스트리밍(러닝) 모드로 복귀
     */
    public void clearStaticWave() {
        staticMode = false;
        staticSamples = null;
    }

    /**
     * 결과 화면용: 전체 폭에 리샘플 + 스무딩해서 그리기
     */
    // EcgSurfaceView 안의 기존 drawStatic 전체를 이걸로 교체
    private void drawStatic(Canvas c) {
        c.drawColor(Color.WHITE);
        computeMmToPx();
        drawGrid(c);

        if (staticSamples == null || staticSamples.length == 0) return;

        int w = c.getWidth(), h = c.getHeight();
        float cy = getWaveCenterY(h);
        int N = Math.max(2, staticSamples.length);

        float[] xs = new float[w];
        float[] ys = new float[w];

        // 원본 파형을 화면 폭에 맞게 리샘플
        for (int i = 0; i < w; i++) {
            double t = (i / (double) (w - 1)) * (N - 1);
            int i0 = (int) Math.floor(t);
            int i1 = Math.min(N - 1, i0 + 1);
            float frac = (float) (t - i0);

            float mv = staticSamples[i0] * (1 - frac) + staticSamples[i1] * frac;
            float y = mvToY(mv, cy);

            float x = i + 0.5f;
            y = (float) Math.floor(y) + 0.5f;

            xs[i] = x;
            ys[i] = y;
        }

        // 🔥 정지 파형도 강하게 부드럽게
        smoothYs(ys, w, 6);   // 결과 화면은 조금 더 세게 (원하면 4~8 사이로 조정)

        Path p = new Path();
        p.moveTo(xs[0], ys[0]);
        for (int i = 1; i < w; i++) {
            p.lineTo(xs[i], ys[i]);
        }

        c.drawPath(p, wave);
    }



    public void setCornerRadiusDp(float dp) {
        this.cornerRadiusPx = dp * getResources().getDisplayMetrics().density;
        rebuildCornerPath();
        repaintGrid();
    }

    private void rebuildCornerPath() {
        rebuildCornerPathForSize(getWidth(), getHeight());
    }

    private void rebuildCornerPathForSize(int w, int h) {
        drawRect.set(0, 0, w, h);
        cornerPath.reset();
        cornerPath.addRoundRect(drawRect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW);

        borderPaint.setAntiAlias(true);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2f * getResources().getDisplayMetrics().density);
        borderPaint.setStrokeJoin(Paint.Join.ROUND);
        borderPaint.setColor(0x1A000000); // 옅은 라인
    }

    /**
     * 원본 파형 사이에 중간 샘플을 여러 개 넣어서 곡선을 만드는 함수
     * (표시용으로만 사용, 원본 분석데이터에는 영향 X)
     */
    private float[] roundifySegment(float[] src) {
        if (src == null || src.length < 2) return src;

        ArrayList<Float> out = new ArrayList<>();

        // 두 점 사이 차이가 클수록 중간샘플을 더 많이 넣는다
        final float STEP_FACTOR = 0.6f;   // 0.3~1.0 사이에서 조절 가능

        for (int i = 0; i < src.length - 1; i++) {
            float a = src[i];
            float b = src[i + 1];

            out.add(a); // 현재 점은 항상 추가

            float diff = Math.abs(b - a);

            // 최소 1개, 최대 8개 정도 중간 샘플
            int steps = 1 + (int) Math.min(8f, diff * STEP_FACTOR);

            for (int s = 1; s < steps; s++) {
                float t = (float) s / steps;

                // 코사인 보간으로 양 끝이 둥글게 이어지도록
                float tt = (1f - (float) Math.cos((float) Math.PI * t)) * 0.5f;
                float y = a + (b - a) * tt;

                out.add(y);
            }
        }

        // 마지막 점 추가
        out.add(src[src.length - 1]);

        float[] res = new float[out.size()];
        for (int i = 0; i < res.length; i++) {
            res[i] = out.get(i);
        }
        return res;
    }

}
