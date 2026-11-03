package com.ryanvanveen.followupboard;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

/** Schedules the weekday morning check and the afternoon reminder. */
final class Alarms {
    static final String MORNING = "morning";
    static final String REMINDER = "reminder";

    static void scheduleAll(Context c) {
        schedule(c, MORNING, Prefs.morning(c), 101);
        schedule(c, REMINDER, Prefs.reminder(c), 102);
    }

    private static void schedule(Context c, String mode, String hhmm, int req) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(c, AlarmReceiver.class).putExtra("mode", mode);
        PendingIntent pi = PendingIntent.getBroadcast(c, req, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        if (!Prefs.on(c) || Prefs.url(c).isEmpty()) return;

        long when = nextWeekday(hhmm);
        boolean exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms();
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
    }

    /** Next Monday-to-Friday occurrence of hh:mm that is still in the future. */
    static long nextWeekday(String hhmm) {
        int h = 8, m = 0;
        try {
            String[] p = hhmm.split(":");
            h = Integer.parseInt(p[0]);
            m = Integer.parseInt(p[1]);
        } catch (Exception ignored) { }
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, h);
        cal.set(Calendar.MINUTE, m);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis() + 1000) cal.add(Calendar.DAY_OF_YEAR, 1);
        while (isWeekend(cal)) cal.add(Calendar.DAY_OF_YEAR, 1);
        return cal.getTimeInMillis();
    }

    static boolean isWeekend(Calendar cal) {
        int d = cal.get(Calendar.DAY_OF_WEEK);
        return d == Calendar.SATURDAY || d == Calendar.SUNDAY;
    }

    private Alarms() { }
}
