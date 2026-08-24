package com.nest.tmind.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.nest.tmind.R;
import com.nest.tmind.api.ApiModels;
import com.nest.tmind.api.MemberApiManager;
import com.nest.tmind.util.SessionManager;

/** 최초 등록: 휴대폰 8자리 + 이름·성별·나이 → 이후 자동 로그인 */
public class LoginActivity extends BaseSeniorActivity {

    private SessionManager session;
    private EditText etPhone, etName, etAge;
    private RadioGroup rgGender;
    private RadioButton rbFemale, rbMale;
    private ScrollView loginScroll;
    private View btnStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);
        if (session.isLoggedIn() && !getIntent().getBooleanExtra(EXTRA_FORCE_REGISTER, false)) {
            goDashboard();
            return;
        }
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        setContentView(R.layout.activity_login);

        loginScroll = findViewById(R.id.loginScroll);
        etPhone = findViewById(R.id.etPhone);
        etName = findViewById(R.id.etName);
        etAge = findViewById(R.id.etAge);
        rgGender = findViewById(R.id.rgGender);
        rbFemale = findViewById(R.id.rbFemale);
        rbMale = findViewById(R.id.rbMale);

        btnStart = findViewById(R.id.btnStart);
        setupKeyboardScroll();
        setupTtsFromViews(R.id.btnTts, R.id.tvTitle, R.id.tvHint);
        btnStart.setOnClickListener(v -> confirm());

        rbFemale.setOnCheckedChangeListener((b, checked) -> {
            if (checked) highlightGender();
        });
        rbMale.setOnCheckedChangeListener((b, checked) -> {
            if (checked) highlightGender();
        });
        highlightGender();
    }

    private void setupKeyboardScroll() {
        if (loginScroll == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(loginScroll, (v, insets) -> {
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), ime.bottom);
            return insets;
        });
        View.OnFocusChangeListener scrollToField = (v, hasFocus) -> {
            if (hasFocus) scrollToView(v);
        };
        etPhone.setOnFocusChangeListener(scrollToField);
        etName.setOnFocusChangeListener(scrollToField);
        etAge.setOnFocusChangeListener(scrollToField);
        rgGender.setOnFocusChangeListener(scrollToField);
        rbFemale.setOnClickListener(v -> scrollToView(rgGender));
        rbMale.setOnClickListener(v -> scrollToView(rgGender));
    }

    private void scrollToView(View target) {
        if (loginScroll == null || target == null) return;
        loginScroll.post(() -> {
            int y = target.getTop();
            View parent = target.getParent() instanceof View ? (View) target.getParent() : null;
            while (parent != null && parent != loginScroll.getChildAt(0)) {
                y += parent.getTop();
                parent = parent.getParent() instanceof View ? (View) parent.getParent() : null;
            }
            loginScroll.smoothScrollTo(0, Math.max(0, y - 24));
        });
    }

    public static final String EXTRA_FORCE_REGISTER = "force_register";

    private void highlightGender() {
        int selected = R.drawable.bg_btn_primary;
        int normal = R.drawable.bg_btn_outline;
        int selectedText = getResources().getColor(R.color.white, getTheme());
        int normalText = getResources().getColor(R.color.text_primary, getTheme());
        boolean female = rbFemale.isChecked();
        boolean male = rbMale.isChecked();
        rbFemale.setBackgroundResource(female ? selected : normal);
        rbFemale.setTextColor(female ? selectedText : normalText);
        rbMale.setBackgroundResource(male ? selected : normal);
        rbMale.setTextColor(male ? selectedText : normalText);
    }

    private void confirm() {
        String phone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
        if (phone.length() != 8) {
            Toast.makeText(this, R.string.profile_need_phone, Toast.LENGTH_SHORT).show();
            if (tts != null) tts.speak(getString(R.string.profile_need_phone));
            return;
        }
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.profile_need_name, Toast.LENGTH_SHORT).show();
            if (tts != null) tts.speak(getString(R.string.profile_need_name));
            return;
        }
        int genderId = rgGender.getCheckedRadioButtonId();
        if (genderId != R.id.rbFemale && genderId != R.id.rbMale) {
            Toast.makeText(this, R.string.profile_need_gender, Toast.LENGTH_SHORT).show();
            if (tts != null) tts.speak(getString(R.string.profile_need_gender));
            return;
        }
        String ageStr = etAge.getText() != null ? etAge.getText().toString().trim() : "";
        int age;
        try {
            age = Integer.parseInt(ageStr);
        } catch (Exception e) {
            age = -1;
        }
        if (age < 1 || age > 120) {
            Toast.makeText(this, R.string.profile_need_age, Toast.LENGTH_SHORT).show();
            if (tts != null) tts.speak(getString(R.string.profile_need_age));
            return;
        }
        final int finalAge = age;
        String gender = genderId == R.id.rbFemale ? "F" : "M";
        setLoading(true);
        MemberApiManager.loginAndConsent(this, phone, new MemberApiManager.ResultCallback<ApiModels.TokenPair>() {
            @Override
            public void onSuccess(ApiModels.TokenPair data) {
                runOnUiThread(() -> {
                    setLoading(false);
                    session.setProfile(phone, name, gender, finalAge);
                    session.saveScreen("dashboard");
                    goDashboard();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    setLoading(false);
                    Toast.makeText(LoginActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void setLoading(boolean loading) {
        if (btnStart != null) {
            btnStart.setEnabled(!loading);
            btnStart.setAlpha(loading ? 0.6f : 1f);
        }
    }

    private void goDashboard() {
        startActivity(new Intent(this, DashboardActivity.class));
        finish();
    }
}
