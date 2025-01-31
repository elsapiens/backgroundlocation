package com.elsapiens.backgroundlocation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.getcapacitor.Bridge;
import com.getcapacitor.JSObject;

public class LocationBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "LocationBroadcastReceiver";


    public LocationBroadcastReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if ("BackgroundLocationUpdate".equals(intent.getAction())) {
            String reference = intent.getStringExtra("reference");
            int index = intent.getIntExtra("index", 0);
            double latitude = intent.getDoubleExtra("latitude", 0);
            double longitude = intent.getDoubleExtra("longitude", 0);
            float accuracy = intent.getFloatExtra("accuracy", 0);
            float speed = intent.getFloatExtra("speed", 0);
            long timestamp = intent.getLongExtra("timestamp", 0);
            LocationItem locationItem = new LocationItem(reference, index, latitude, longitude, accuracy, speed, timestamp);
            LocationBroadcastReceiver.sendUpdateToCapacitor(locationItem, context);

        }
    }
    private static void sendUpdateToCapacitor(LocationItem locationItem, Context context) {
        BackgroundLocationPlugin pluginInstance = BackgroundLocationPlugin.getInstance();
        if (pluginInstance != null) {


            pluginInstance.pushUpdateToCapacitor(locationItem);
        } else {
            Log.e(TAG, "Plugin instance is null, cannot send event to Angular.");
        }
    }
}