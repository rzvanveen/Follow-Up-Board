package com.ryanvanveen.followupboard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires at the scheduled time, hands the check to WorkManager, and books the next one. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String mode = intent.getStringExtra("mode");
        CheckWorker.enqueue(context, mode == null ? Alarms.MORNING : mode);
        Alarms.scheduleAll(context);
    }
}
