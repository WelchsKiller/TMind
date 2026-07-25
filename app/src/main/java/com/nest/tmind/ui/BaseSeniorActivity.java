package com.nest.tmind.ui;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.nest.tmind.R;
import com.nest.tmind.ecg.EcgBleService;
import com.nest.tmind.util.TtsHelper;

/** 시니어 공통: TTS + 시스템바 inset + BLE 범위 제한 */
public abstract class BaseSeniorActivity extends AppCompatActivity {

    protected TtsHelper tts;

    /** BLE는 HRV 측정 화면만 허용 */
    protected boolean needsBleConnection() {
        return false;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        tts = TtsHelper.getInstance(this);
        if (!needsBleConnection()) {
            EcgBleService.stopBleService(this);
        }
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        applySafeInsets();
        enlargeHeaderControls();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!needsBleConnection()) {
            EcgBleService.stopBleService(this);
        }
    }

    @Override
    protected void onPause() {
        if (tts != null) tts.stop();
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (tts != null) tts.stop();
        super.onStop();
    }

    private void applySafeInsets() {
        View root = findViewById(android.R.id.content);
        if (root == null) return;
        View content = root instanceof ViewGroup && ((ViewGroup) root).getChildCount() > 0
                ? ((ViewGroup) root).getChildAt(0)
                : root;
        final int padL = content.getPaddingLeft();
        final int padT = content.getPaddingTop();
        final int padR = content.getPaddingRight();
        final int padB = content.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(padL + bars.left, padT + bars.top, padR + bars.right, padB + bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(content);
    }

    private void enlargeHeaderControls() {
        ImageButton ttsBtn = findViewById(R.id.btnTts);
        if (ttsBtn != null) {
            ViewGroup.LayoutParams lp = ttsBtn.getLayoutParams();
            if (lp != null) {
                float d = getResources().getDisplayMetrics().density;
                lp.width = (int) (64 * d);
                lp.height = (int) (64 * d);
                ttsBtn.setLayoutParams(lp);
                ttsBtn.setMinimumWidth((int) (64 * d));
                ttsBtn.setMinimumHeight((int) (64 * d));
            }
        }
        ImageButton back = findViewById(R.id.btnBack);
        if (back != null) {
            ViewGroup.LayoutParams lp = back.getLayoutParams();
            if (lp != null) {
                float d = getResources().getDisplayMetrics().density;
                lp.width = (int) (56 * d);
                lp.height = (int) (56 * d);
                back.setLayoutParams(lp);
            }
        }
    }

    protected void setupTtsButton(int buttonId, String readText) {
        ImageButton btn = findViewById(buttonId);
        if (btn != null) {
            btn.setOnClickListener(v -> {
                if (tts != null) tts.speak(readText);
            });
        }
    }

    protected void setupTtsFromViews(int buttonId, int... textViewIds) {
        ImageButton btn = findViewById(buttonId);
        if (btn == null) return;
        btn.setOnClickListener(v -> {
            StringBuilder sb = new StringBuilder();
            for (int id : textViewIds) {
                View view = findViewById(id);
                if (view instanceof TextView) {
                    CharSequence t = ((TextView) view).getText();
                    if (t != null && t.length() > 0) {
                        if (sb.length() > 0) sb.append(". ");
                        sb.append(t);
                    }
                }
            }
            if (tts != null) tts.speak(sb.toString());
        });
    }
}
