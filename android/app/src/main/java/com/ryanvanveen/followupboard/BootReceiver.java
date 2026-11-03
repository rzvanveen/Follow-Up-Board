package com.ryanvanveen.followupboard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Alarms are cleared on restart, app update and clock changes, so put them back. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Alarms.scheduleAll(context);
    }
}
