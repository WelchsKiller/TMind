package com.nest.tmind.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Russell circumplex 사분면 UI.
 * 가로=쾌–불쾌, 세로=각성–비각성 (Russell circumplex).
 */
public class RussellCircumplexView extends View {

    public interface OnPointChangedListener {
        void onPointChanged(float valence, float arousal);
    }

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emojiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float valence = 0f;
    private float arousal = 0f;
    private boolean hasPoint = false;
    private boolean editable = false;
    private OnPointChangedListener listener;

    private float[] trailValence;
    private float[] trailArousal;
    private int[] trailColors;

    public RussellCircumplexView(Context context) {
        this(context, null);
    }

    public RussellCircumplexView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);

        gridPaint.setColor(0xFFA8B8B0);
        gridPaint.setStyle(Paint.Style.FILL);

        axisPaint.setColor(0xFF3D524A);
        axisPaint.setStrokeWidth(dp(2.4f));
        axisPaint.setStyle(Paint.Style.STROKE);

        labelPaint.setColor(0xFF1F2A26);
        labelPaint.setTextSize(dp(14));
        labelPaint.setFakeBoldText(true);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        hintPaint.setColor(0xFF3D524A);
        hintPaint.setTextSize(dp(12));
        hintPaint.setFakeBoldText(true);
        hintPaint.setTextAlign(Paint.Align.CENTER);

        emojiPaint.setTextAlign(Paint.Align.CENTER);
        emojiPaint.setAlpha(72);

        pointPaint.setColor(0xFF2F9E7A);
        pointPaint.setStyle(Paint.Style.FILL);
        ripplePaint.setStyle(Paint.Style.FILL);
        trailPaint.setStyle(Paint.Style.FILL);
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
    }

    public void setOnPointChangedListener(OnPointChangedListener listener) {
        this.listener = listener;
    }

    public void setPoint(float valence, float arousal) {
        this.valence = clamp(valence, -1.2f, 1.2f);
        this.arousal = clamp(arousal, -1.2f, 1.2f);
        this.hasPoint = true;
        invalidate();
    }

    public void clearPoint() {
        hasPoint = false;
        invalidate();
    }

    public boolean hasPoint() {
        return hasPoint;
    }

    public float getValence() {
        return valence;
    }

    public float getArousal() {
        return arousal;
    }

    public void setTrail(float[] valenceArr, float[] arousalArr, int[] colors) {
        this.trailValence = valenceArr;
        this.trailArousal = arousalArr;
        this.trailColors = colors;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!editable) return super.onTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN
                || action == MotionEvent.ACTION_MOVE
                || action == MotionEvent.ACTION_UP) {
            float[] va = xyToVa(event.getX(), event.getY());
            valence = va[0];
            arousal = va[1];
            hasPoint = true;
            invalidate();
            if (listener != null && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN)) {
                listener.onPointChanged(valence, arousal);
            }
            if (action == MotionEvent.ACTION_DOWN) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int mode = MeasureSpec.getMode(heightMeasureSpec);
        int h;
        if (mode == MeasureSpec.EXACTLY) {
            h = MeasureSpec.getSize(heightMeasureSpec);
        } else {
            h = w;
            if (mode == MeasureSpec.AT_MOST) {
                h = Math.min(h, MeasureSpec.getSize(heightMeasureSpec));
            }
        }
        int side = Math.min(w, h);
        setMeasuredDimension(side, side);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float pad = dp(48);
        float left = pad;
        float top = pad;
        float right = getWidth() - pad;
        float bottom = getHeight() - pad;
        float cx = (left + right) / 2f;
        float cy = (top + bottom) / 2f;
        float half = Math.min(right - left, bottom - top) / 2f;

        drawQuadrantEmojis(canvas, cx, cy, half);
        drawDotGrid(canvas, cx, cy, half);

        canvas.drawLine(cx - half, cy, cx + half, cy, axisPaint);
        canvas.drawLine(cx, cy - half, cx, cy + half, axisPaint);

        // 축 끝단 설명 (Russell: 쾌–불쾌 / 각성–비각성)
        hintPaint.setTextAlign(Paint.Align.CENTER);
        hintPaint.setTextSize(dp(13));
        canvas.drawText("각성", cx, cy - half - dp(4), hintPaint);
        canvas.drawText("비각성", cx, cy + half + dp(18), hintPaint);

        hintPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("불쾌", cx - half - dp(4), cy - dp(6), hintPaint);
        hintPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("쾌", cx + half + dp(4), cy - dp(6), hintPaint);

        if (trailValence != null && trailArousal != null) {
            for (int i = 0; i < trailValence.length; i++) {
                float[] xy = vaToXy(trailValence[i], trailArousal[i], cx, cy, half);
                int color = (trailColors != null && i < trailColors.length)
                        ? trailColors[i] : 0xAA2F9E7A;
                trailPaint.setColor(color);
                canvas.drawCircle(xy[0], xy[1], dp(7), trailPaint);
            }
        }

        if (hasPoint) {
            float[] xy = vaToXy(valence, arousal, cx, cy, half);
            drawRipple(canvas, xy[0], xy[1]);
            canvas.drawCircle(xy[0], xy[1], dp(9), pointPaint);
        }
    }

    /** 사분면별 감정 이모지 — 끝단 배치, 배경용으로 옅게 */
    private void drawQuadrantEmojis(Canvas canvas, float cx, float cy, float half) {
        float q = half * 0.78f;
        emojiPaint.setTextSize(dp(36));
        emojiPaint.setAlpha(255);
        // 배경용으로 전체 사분면 이모지를 옅게 (약 28% 불투명)
        int save = canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 72);
        canvas.drawText("😄", cx + q, cy - q + dp(12), emojiPaint);
        canvas.drawText("😣", cx - q, cy - q + dp(12), emojiPaint);
        canvas.drawText("😔", cx - q, cy + q + dp(12), emojiPaint);
        canvas.drawText("😌", cx + q, cy + q + dp(12), emojiPaint);
        canvas.restoreToCount(save);
    }

    private void drawDotGrid(Canvas canvas, float cx, float cy, float half) {
        int steps = 10;
        float step = half / steps;
        float r = dp(1.6f);
        for (int i = -steps; i <= steps; i++) {
            for (int j = -steps; j <= steps; j++) {
                if (i == 0 || j == 0) continue;
                canvas.drawCircle(cx + i * step, cy - j * step, r, gridPaint);
            }
        }
    }

    private void drawRipple(Canvas canvas, float x, float y) {
        float[] radii = {dp(30), dp(20), dp(12)};
        int[] alphas = {0x28, 0x48, 0x70};
        for (int i = 0; i < radii.length; i++) {
            ripplePaint.setColor((alphas[i] << 24) | 0x2F9E7A);
            canvas.drawCircle(x, y, radii[i], ripplePaint);
        }
    }

    private float[] vaToXy(float v, float a, float cx, float cy, float half) {
        float nv = clamp(v, -1.2f, 1.2f) / 1.2f;
        float na = clamp(a, -1.2f, 1.2f) / 1.2f;
        return new float[]{cx + nv * half, cy - na * half};
    }

    private float[] xyToVa(float x, float y) {
        float pad = dp(48);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float half = (Math.min(getWidth(), getHeight()) / 2f) - pad;
        if (half <= 0) return new float[]{0f, 0f};
        float nv = clamp((x - cx) / half, -1f, 1f);
        float na = clamp((cy - y) / half, -1f, 1f);
        return new float[]{nv * 1.2f, na * 1.2f};
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
