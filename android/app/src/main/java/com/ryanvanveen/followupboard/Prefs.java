package com.ryanvanveen.followupboard;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/** Connection and notification settings, shared by the screen and the background checks. */
final class Prefs {
    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("fub", Context.MODE_PRIVATE);
    }

    static String url(Context c) { return sp(c).getString("url", ""); }
    static String key(Context c) { return sp(c).getString("key", ""); }
    static boolean on(Context c) { return sp(c).getBoolean("on", true); }
    static String morning(Context c) { return sp(c).getString("morning", "08:00"); }
    static String reminder(Context c) { return sp(c).getString("reminder", "13:00"); }

    static void saveConn(Context c, String json) {
        try {
            JSONObject o = new JSONObject(json);
            sp(c).edit().putString("url", o.optString("url", "")).putString("key", o.optString("key", "")).apply();
        } catch (Exception ignored) { }
    }

    static void saveNotify(Context c, String json) {
        try {
            JSONObject o = new JSONObject(json);
            sp(c).edit()
                .putBoolean("on", o.optBoolean("on", true))
                .putString("morning", o.optString("morning", "08:00"))
                .putString("reminder", o.optString("reminder", "13:00"))
                .apply();
        } catch (Exception ignored) { }
    }

    static String toJson(Context c) {
        try {
            JSONObject o = new JSONObject();
            o.put("url", url(c));
            o.put("key", key(c));
            o.put("on", on(c));
            o.put("morning", morning(c));
            o.put("reminder", reminder(c));
            o.put("version", BuildConfig.VERSION_NAME);
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private Prefs() { }
}
