package com.nest.tmind.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.nest.tmind.R;
import com.nest.tmind.util.SessionManager;

/** 최초 1회: 이름·성별·나이 입력 후 자동 로그인 */
public class LoginActivity extends BaseSeniorActivity {

    private SessionManager session;
    private EditText etName, etAge;
    private RadioGroup rgGender;
    private RadioButton rbFemale, rbMale;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);
        if (session.isLoggedIn()) {
            goDashboard();
            return;
        }
        setContentView(R.layout.activity_login);

        etName = findViewById(R.id.etName);
        etAge = findViewById(R.id.etAge);
        rgGender = findViewById(R.id.rgGender);
        rbFemale = findViewById(R.id.rbFemale);
        rbMale = findViewById(R.id.rbMale);

        setupTtsFromViews(R.id.btnTts, R.id.tvTitle, R.id.tvHint);
        findViewById(R.id.btnStart).setOnClickListener(v -> confirm());

        rbFemale.setOnCheckedChangeListener((b, checked) -> {
            if (checked) highlightGender();
        });
        rbMale.setOnCheckedChangeListener((b, checked) -> {
            if (checked) highlightGender();
        });
        highlightGender();
    }

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
        String gender = genderId == R.id.rbFemale ? "F" : "M";
        session.setProfile(name, gender, age);
        session.saveScreen("dashboard");
        goDashboard();
    }

    private void goDashboard() {
        startActivity(new Intent(this, DashboardActivity.class));
        finish();
    }
}
