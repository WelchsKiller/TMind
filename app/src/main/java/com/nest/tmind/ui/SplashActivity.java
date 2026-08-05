package com.nest.tmind.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.nest.tmind.R;
import com.nest.tmind.util.SessionManager;

/** 최초 실행 로딩 → 로그인(또는 대시보드) */
public class SplashActivity extends BaseSeniorActivity {

    private static final long SPLASH_MS = 1600L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean navigated;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        handler.postDelayed(this::goNext, SPLASH_MS);
    }

    private void goNext() {
        if (navigated || isFinishing()) return;
        navigated = true;
        SessionManager session = new SessionManager(this);
        Intent next = session.isLoggedIn()
                ? new Intent(this, DashboardActivity.class)
                : new Intent(this, LoginActivity.class);
        startActivity(next);
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
