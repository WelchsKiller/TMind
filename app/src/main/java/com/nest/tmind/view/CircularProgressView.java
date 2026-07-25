package com.nest.tmind.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.nest.tmind.R;

/** HRV 5분 원형 카운트다운 */
public class CircularProgressView extends View {

    private float progress = 1f;
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public CircularProgressView(Context context) {
        this(context, null);
    }

    public CircularProgressView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        bgPaint.setStyle(Paint.Style.STROKE);
        bgPaint.setStrokeWidth(dp(12));
        bgPaint.setColor(0xFFE0E0E0);
        fgPaint.setStyle(Paint.Style.STROKE);
        fgPaint.setStrokeWidth(dp(12));
        fgPaint.setColor(getResources().getColor(R.color.teal_primary, null));
        fgPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setProgress(float p) {
        progress = Math.max(0f, Math.min(1f, p));
        invalidate();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float pad = dp(16);
        rect.set(pad, pad, getWidth() - pad, getHeight() - pad);
        canvas.drawArc(rect, 0, 360, false, bgPaint);
        canvas.drawArc(rect, -90, 360 * progress, false, fgPaint);
    }
}
