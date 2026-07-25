package com.nest.tmind.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 재부팅 후 미션 알림 재등록 */
public class BootReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            ReminderScheduler.scheduleDaily(context);
        }
    }
}
