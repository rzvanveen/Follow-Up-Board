package com.ryanvanveen.followupboard;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Minimal HTTP client that follows Google's redirects (POST becomes GET after a 302, like a browser). */
final class Http {
    static final class Result {
        final int status;
        final String body;
        Result(int status, String body) { this.status = status; this.body = body; }
    }

    static Result request(String method, String url, String body) {
        try {
            String m = method;
            String b = body;
            for (int hop = 0; hop < 6; hop++) {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                c.setRequestMethod(m);
                c.setRequestProperty("User-Agent", "FollowUpBoard-Android");
                if ("POST".equals(m) && b != null) {
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "text/plain;charset=utf-8");
                    try (OutputStream os = c.getOutputStream()) { os.write(b.getBytes(StandardCharsets.UTF_8)); }
                }
                int code = c.getResponseCode();
                if (code >= 300 && code < 400) {
                    String loc = c.getHeaderField("Location");
                    c.disconnect();
                    if (loc == null) return new Result(code, "");
                    url = new URL(new URL(url), loc).toString();
                    if (code != 307 && code != 308) { m = "GET"; b = null; }
                    continue;
                }
                InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                String text = "";
                if (in != null) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    in.close();
                    text = out.toString("UTF-8");
                }
                c.disconnect();
                return new Result(code, text);
            }
            return new Result(0, "");
        } catch (Exception e) {
            return new Result(0, "");
        }
    }

    private Http() { }
}
