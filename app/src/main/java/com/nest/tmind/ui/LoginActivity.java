package com.nest.tmind.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.nest.tmind.R;
import com.nest.tmind.util.SessionManager;

/** APP-USR-001: 8자리 번호 로그인 */
public class LoginActivity extends BaseSeniorActivity {

    private static final int CODE_LEN = 8;
    private static final String DEMO_CODE = "012345678";

    private final StringBuilder code = new StringBuilder();
    private TextView[] slots;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);
        if (session.isLoggedIn()) {
            goDashboard();
            return;
        }
        setContentView(R.layout.activity_login);

        slots = new TextView[]{
                findViewById(R.id.slot1), findViewById(R.id.slot2),
                findViewById(R.id.slot3), findViewById(R.id.slot4),
                findViewById(R.id.slot5), findViewById(R.id.slot6),
                findViewById(R.id.slot7), findViewById(R.id.slot8)
        };

        setupTtsButton(R.id.btnTts, getString(R.string.login_tts));

        int[] numIds = {
                R.id.key1, R.id.key2, R.id.key3, R.id.key4, R.id.key5,
                R.id.key6, R.id.key7, R.id.key8, R.id.key9, R.id.key0
        };
        for (int i = 0; i < numIds.length; i++) {
            final String digit = String.valueOf(i == 9 ? 0 : i + 1);
            findViewById(numIds[i]).setOnClickListener(v -> appendDigit(digit.charAt(0)));
        }
        findViewById(R.id.keyClear).setOnClickListener(v -> clearCode());
        findViewById(R.id.keyConfirm).setOnClickListener(v -> confirm());
    }

    private void appendDigit(char d) {
        if (code.length() >= CODE_LEN) return;
        code.append(d);
        refreshSlots();
    }

    private void clearCode() {
        code.setLength(0);
        refreshSlots();
    }

    private void refreshSlots() {
        for (int i = 0; i < CODE_LEN; i++) {
            slots[i].setText(i < code.length() ? String.valueOf(code.charAt(i)) : "_");
        }
    }

    private void confirm() {
        if (code.length() != CODE_LEN) {
            tts.speak("8자리 번호를 모두 입력해 주세요");
            return;
        }
        String entered = code.toString();
        if (entered.equals(DEMO_CODE) || entered.length() == CODE_LEN) {
            session.setLoggedIn("김순자");
            session.saveScreen("dashboard");
            goDashboard();
        }
    }

    private void goDashboard() {
        startActivity(new Intent(this, DashboardActivity.class));
        finish();
    }
}
