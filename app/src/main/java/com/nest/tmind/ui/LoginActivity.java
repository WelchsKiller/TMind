package com.nest.tmind.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import com.nest.tmind.R;
import com.nest.tmind.api.ApiModels;
import com.nest.tmind.api.MemberApiManager;
import com.nest.tmind.util.SessionManager;

/** APP-USR-001: 8자리 번호 키패드 로그인 → 토큰 저장 */
public class LoginActivity extends BaseSeniorActivity {

    public static final String EXTRA_FORCE_REGISTER = "force_register";

    private static final int CODE_LEN = 8;

    private final StringBuilder code = new StringBuilder();
    private TextView[] slots;
    private SessionManager session;
    private TextView keyConfirm;
    private boolean loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);
        boolean forceLogin = getIntent().getBooleanExtra(EXTRA_FORCE_REGISTER, false);

        if (session.hasValidAuth() && !forceLogin) {
            goDashboard();
            return;
        }
        if (forceLogin) {
            session.logoutForNewParticipant();
        } else if (!session.hasAccessToken()) {
            session.clearTokens();
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
        keyConfirm = findViewById(R.id.keyConfirm);
        keyConfirm.setOnClickListener(v -> confirm());
    }

    private void appendDigit(char d) {
        if (loading || code.length() >= CODE_LEN) return;
        code.append(d);
        refreshSlots();
    }

    private void clearCode() {
        if (loading) return;
        code.setLength(0);
        refreshSlots();
    }

    private void refreshSlots() {
        for (int i = 0; i < CODE_LEN; i++) {
            slots[i].setText(i < code.length() ? String.valueOf(code.charAt(i)) : "_");
        }
    }

    private void confirm() {
        if (loading) return;
        if (code.length() != CODE_LEN) {
            String msg = getString(R.string.profile_need_phone);
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            if (tts != null) tts.speak(msg);
            return;
        }
        final String phone = code.toString();
        setLoading(true);
        MemberApiManager.login(this, phone, new MemberApiManager.ResultCallback<ApiModels.TokenPair>() {
            @Override
            public void onSuccess(ApiModels.TokenPair data) {
                runOnUiThread(() -> {
                    setLoading(false);
                    if (!session.hasAccessToken()) {
                        Toast.makeText(LoginActivity.this, "토큰 저장에 실패했습니다. 다시 시도해 주세요.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    session.setProfile(phone, "", "", 0);
                    session.saveScreen("dashboard");
                    goDashboard();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
                    if (tts != null) tts.speak(message);
                });
            }
        });
    }

    private void setLoading(boolean on) {
        loading = on;
        if (keyConfirm != null) {
            keyConfirm.setEnabled(!on);
            keyConfirm.setAlpha(on ? 0.6f : 1f);
        }
    }

    private void goDashboard() {
        startActivity(new Intent(this, DashboardActivity.class));
        finish();
    }
}
