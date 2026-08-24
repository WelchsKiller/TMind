package com.nest.tmind;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.google.firebase.messaging.FirebaseMessaging;
import com.nest.tmind.api.MemberApiManager;
import com.nest.tmind.ecg.LastEcgResult;
import com.nest.tmind.util.AesCrypto;
import com.nest.tmind.util.DataQueueManager;
import com.nest.tmind.util.ReminderScheduler;
import com.nest.tmind.util.SessionManager;

public class TMindApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            AesCrypto.ensureKey();
        } catch (Exception ignored) {
        }
        LastEcgResult.loadFromPrefs(this);
        new DataQueueManager(this).flushIfOnline();
        refreshFcmToken();
        createReminderChannel();
        try {
            ReminderScheduler.scheduleDaily(this);
        } catch (Exception ignored) {
        }
    }

    private void refreshFcmToken() {
        SessionManager session = new SessionManager(this);
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            if (token == null || token.isEmpty()) return;
            session.setLastFcmToken(token);
            if (session.isLoggedIn()) {
                MemberApiManager.registerFcmToken(this, token);
            }
        });
    }

    private void createReminderChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    ReminderScheduler.CHANNEL_ID,
                    "미션 알림",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            ch.setDescription("아침·저녁 미션 안내");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }
}
