package com.nest.tmind.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.nest.tmind.R;

/** 음성 녹음 실시간 파형 */
public class VoiceWaveformView extends View {

    private float[] levels = new float[0];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public VoiceWaveformView(Context context) {
        this(context, null);
    }

    public VoiceWaveformView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paint.setColor(getResources().getColor(R.color.teal_primary, null));
        paint.setStrokeWidth(4f);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setLevels(float[] lv) {
        levels = lv != null ? lv : new float[0];
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (levels.length == 0) return;
        float w = getWidth();
        float h = getHeight();
        float mid = h / 2f;
        float step = w / levels.length;
        for (int i = 0; i < levels.length; i++) {
            float amp = levels[i] * mid * 0.9f;
            float x = i * step + step / 2f;
            canvas.drawLine(x, mid - amp, x, mid + amp, paint);
        }
    }
}
