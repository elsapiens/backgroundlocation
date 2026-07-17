package com.elsapiens.backgroundlocation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Bridges service broadcasts (location updates, status changes, typed errors) to the
 * plugin so they surface as JavaScript events. Registered once by the plugin — the
 * services must NOT register their own instance, or every event fires twice.
 */
public class LocationBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "LocationBroadcastReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) {
            return;
        }
        BackgroundLocationPlugin plugin = BackgroundLocationPlugin.getInstance();
        if (plugin == null) {
            Log.w(TAG, "Plugin instance is null, cannot forward " + action + " to Capacitor.");
            return;
        }

        switch (action) {
            case BackgroundLocationService.ACTION_LOCATION_UPDATE:
                plugin.pushUpdateToCapacitor(readLocationItem(intent));
                break;
            case BackgroundLocationService.ACTION_LOCATION_DISABLED:
                plugin.pushLocationStateToCapacitor(false);
                break;
            case BackgroundLocationService.ACTION_ERROR:
                plugin.pushErrorToCapacitor(
                    intent.getStringExtra("code"),
                    intent.getStringExtra("message"),
                    intent.getStringExtra("source"),
                    intent.getBooleanExtra("fatal", false));
                break;
            default:
                break;
        }
    }

    private LocationItem readLocationItem(Intent intent) {
        return new LocationItem(
            intent.getStringExtra("reference"),
            intent.getIntExtra("index", 0),
            intent.getDoubleExtra("latitude", 0),
            intent.getDoubleExtra("longitude", 0),
            intent.getDoubleExtra("altitude", 0),
            intent.getFloatExtra("accuracy", 0),
            intent.getFloatExtra("speed", 0),
            intent.getFloatExtra("heading", 0),
            intent.getFloatExtra("altitudeAccuracy", 0),
            intent.getFloatExtra("totalDistance", 0),
            intent.getLongExtra("timestamp", 0));
    }
}
