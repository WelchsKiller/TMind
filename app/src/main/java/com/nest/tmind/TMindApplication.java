package com.nest.tmind;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.nest.tmind.ecg.LastEcgResult;
import com.nest.tmind.util.AesCrypto;
import com.nest.tmind.util.DataQueueManager;
import com.nest.tmind.util.ReminderScheduler;

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
        createReminderChannel();
        try {
            ReminderScheduler.scheduleDaily(this);
        } catch (Exception ignored) {
        }
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
