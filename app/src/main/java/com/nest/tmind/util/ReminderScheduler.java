package com.nest.tmind.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.nest.tmind.R;
import com.nest.tmind.ui.DashboardActivity;

import java.util.Calendar;

/** 오전/오후 미션 리마인드 알림 (exact alarm 권한 없어도 앱이 죽지 않음) */
public final class ReminderScheduler {

    private static final String TAG = "ReminderScheduler";

    public static final String CHANNEL_ID = "tmind_mission_reminders";
    public static final int NOTI_MORNING = 2001;
    public static final int NOTI_AFTERNOON = 2002;

    private ReminderScheduler() {
    }

    public static void scheduleDaily(Context context) {
        try {
            scheduleAt(context, 9, 0, NOTI_MORNING, "morning");
            scheduleAt(context, 15, 0, NOTI_AFTERNOON, "afternoon");
        } catch (Exception e) {
            Log.w(TAG, "scheduleDaily failed", e);
        }
    }

    private static void scheduleAt(Context context, int hour, int minute, int reqCode, String slot) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("slot", slot);
        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        long triggerAt = cal.getTimeInMillis();

        // Android 12+ : exact alarm은 별도 권한/설정 필요 → 가능하면 exact, 아니면 inexact
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
        } catch (SecurityException se) {
            Log.w(TAG, "exact alarm denied, fallback to inexact", se);
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
    }

    public static class ReminderReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String slot = intent != null ? intent.getStringExtra("slot") : "morning";
            String title = "TMind 미션 안내";
            String body = "afternoon".equals(slot)
                    ? "오후 미션 시간이에요. 오늘 할 일을 확인해 주세요."
                    : "오전 미션 시간이에요. 오늘 할 일을 시작해 볼까요?";

            Intent open = new Intent(context, DashboardActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(
                    context,
                    "afternoon".equals(slot) ? NOTI_AFTERNOON : NOTI_MORNING,
                    open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pi)
                    .setAutoCancel(true);

            try {
                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED
                        || Build.VERSION.SDK_INT < 33) {
                    NotificationManagerCompat.from(context).notify(
                            "afternoon".equals(slot) ? NOTI_AFTERNOON : NOTI_MORNING,
                            builder.build()
                    );
                }
            } catch (Exception e) {
                Log.w(TAG, "notify failed", e);
            }

            scheduleDaily(context);
        }
    }
}
