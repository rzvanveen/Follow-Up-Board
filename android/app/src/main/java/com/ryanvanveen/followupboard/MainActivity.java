package com.ryanvanveen.followupboard;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

/** The board itself: the same page as the desktop app, shown full-screen. */
public class MainActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setTextZoom(100);

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if ("file".equals(u.getScheme())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (ActivityNotFoundException ignored) { }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                applyFilterFromIntent(getIntent());
            }
        });
        web.loadUrl("file:///android_asset/index.html");

        CheckWorker.ensureChannel(this);
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        Alarms.scheduleAll(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyFilterFromIntent(intent);
    }

    /** Opening from a notification shows what's due. */
    private void applyFilterFromIntent(Intent i) {
        if (i != null && i.getStringExtra("filter") != null) {
            web.evaluateJavascript("window.fubSync&&fubSync.pull()", null);
            i.removeExtra("filter");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.fubSync&&fubSync.pull()", null);
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.__back&&window.__back()", v -> {
            if (!"true".equals(v)) finish();
        });
    }

    private void js(String code) {
        runOnUiThread(() -> { if (web != null) web.evaluateJavascript(code, null); });
    }

    /** Methods the page can call. */
    private class Bridge {
        @JavascriptInterface
        public String getConfig() { return Prefs.toJson(MainActivity.this); }

        @JavascriptInterface
        public void saveConfig(String json) {
            Prefs.saveConn(MainActivity.this, json);
            Alarms.scheduleAll(MainActivity.this);
        }

        @JavascriptInterface
        public void saveNotify(String json) {
            Prefs.saveNotify(MainActivity.this, json);
            Alarms.scheduleAll(MainActivity.this);
        }

        @JavascriptInterface
        public void testNotification() {
            CheckWorker.enqueue(MainActivity.this, CheckWorker.TEST);
        }

        @JavascriptInterface
        public void copyText(String text) {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Follow-ups", text));
        }

        @JavascriptInterface
        public void http(final String id, final String method, final String url, final String body) {
            new Thread(() -> {
                Http.Result r = Http.request(method, url, body == null || body.isEmpty() ? null : body);
                js("window.__nativeHttpDone(" + JSONObject.quote(id) + "," + r.status + "," + JSONObject.quote(r.body) + ")");
            }).start();
        }
    }
}
