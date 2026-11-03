package com.ryanvanveen.followupboard;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.Calendar;

/** Asks the Google Sheet what's due and shows a notification if anything is. */
public class CheckWorker extends Worker {
    static final String CHANNEL = "followups";
    static final String TEST = "test";

    public CheckWorker(@NonNull Context c, @NonNull WorkerParameters p) { super(c, p); }

    static void enqueue(Context c, String mode) {
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(CheckWorker.class)
            .setInputData(new Data.Builder().putString("mode", mode).build())
            .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build();
        WorkManager.getInstance(c).enqueueUniqueWork("check-" + mode, ExistingWorkPolicy.REPLACE, req);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        String mode = getInputData().getString("mode");
        if (mode == null) mode = Alarms.MORNING;
        boolean test = TEST.equals(mode);

        if (!test && (!Prefs.on(c) || Alarms.isWeekend(Calendar.getInstance()))) return Result.success();
        String url = Prefs.url(c), key = Prefs.key(c);
        if (url.isEmpty()) {
            if (test) show(c, "Not connected yet", "Open Settings in the app and add your sheet's link and key.", null, 0);
            return Result.success();
        }

        try {
            String full = url + (url.contains("?") ? "&" : "?") + "action=summary&key=" + URLEncoder.encode(key, "UTF-8");
            Http.Result r = Http.request("GET", full, null);
            if (r.status == 0) return getRunAttemptCount() < 4 ? Result.retry() : Result.success();
            JSONObject j = new JSONObject(r.body);
            if (!j.optBoolean("ok")) {
                if (test) show(c, "Couldn't read your sheet", "Check the link and access key in Settings.", null, 0);
                return Result.success();
            }
            int due = j.optInt("dueCount"), overdue = j.optInt("overdueCount"), noDate = j.optInt("noDateCount");
            JSONArray items = j.optJSONArray("due");

            String[] lines = new String[items == null ? 0 : items.length()];
            for (int i = 0; i < lines.length; i++) {
                JSONObject it = items.getJSONObject(i);
                JSONArray ppl = it.optJSONArray("people");
                StringBuilder who = new StringBuilder();
                if (ppl != null) for (int k = 0; k < ppl.length(); k++) { if (k > 0) who.append("/"); who.append(ppl.optString(k)); }
                int days = it.optInt("daysOverdue");
                lines[i] = (who.length() > 0 ? who + ": " : "") + it.optString("title") + (days > 0 ? "  (" + days + "d overdue)" : "  (today)");
            }

            if (due == 0) {
                if (test) show(c, "Notifications are working", "Nothing is due right now." + (noDate > 0 ? " " + noDate + " items still need a date." : ""), null, 0);
                return Result.success();
            }
            String title;
            if (Alarms.REMINDER.equals(mode)) title = "Still waiting: " + due + " follow-up" + (due > 1 ? "s" : "");
            else title = due + " follow-up" + (due > 1 ? "s" : "") + " due" + (overdue > 0 ? " (" + overdue + " overdue)" : "");
            String text = lines.length > 0 ? lines[0] + (due > 1 ? "  +" + (due - 1) + " more" : "") : "Open the board to follow up.";
            if (noDate > 0 && !Alarms.REMINDER.equals(mode)) {
                String[] more = new String[lines.length + 1];
                System.arraycopy(lines, 0, more, 0, lines.length);
                more[lines.length] = noDate + " item" + (noDate > 1 ? "s" : "") + " still need a follow-up date";
                lines = more;
            }
            show(c, title, text, lines, due);
            return Result.success();
        } catch (Exception e) {
            return getRunAttemptCount() < 4 ? Result.retry() : Result.success();
        }
    }

    static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, "Follow-ups due", NotificationManager.IMPORTANCE_HIGH);
                ch.setDescription("Weekday morning check and afternoon reminder");
                nm.createNotificationChannel(ch);
            }
        }
    }

    static void show(Context c, String title, String text, String[] lines, int count) {
        ensureChannel(c);
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;

        Intent open = new Intent(c, MainActivity.class).putExtra("filter", "overdue")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_followup)
            .setColor(0xFFE0301E)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER);
        if (count > 0) b.setNumber(count);
        if (lines != null && lines.length > 0) {
            NotificationCompat.InboxStyle s = new NotificationCompat.InboxStyle();
            for (String l : lines) s.addLine(l);
            b.setStyle(s);
        }
        try { NotificationManagerCompat.from(c).notify(7001, b.build()); } catch (SecurityException ignored) { }
    }
}
