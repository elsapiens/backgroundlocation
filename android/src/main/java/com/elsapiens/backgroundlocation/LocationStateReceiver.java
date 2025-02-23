package com.elsapiens.backgroundlocation;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.util.Log;

public class LocationStateReceiver extends BroadcastReceiver {
  private static final String TAG = "LocationStateReceiver";
  private static long lastEventTime = 0;
  private static final long EVENT_THRESHOLD_MS = 500; // 1 second threshold
  private boolean isLocationEnabled = false;

  @Override
  public void onReceive(Context context, Intent intent) {
    long currentTime = System.currentTimeMillis();
    boolean isLocationEnabledTmp = isLocationEnabled(context);
    if (intent.getAction() != null && intent.getAction().matches(LocationManager.PROVIDERS_CHANGED_ACTION)) {
      if (currentTime - lastEventTime < EVENT_THRESHOLD_MS && isLocationEnabledTmp == this.isLocationEnabled) {
        return;
      }
      lastEventTime = currentTime;
      isLocationEnabled = isLocationEnabledTmp;
      if (isLocationEnabled(context)) {
        restartLocationService(context);
      }
      sendEnabledToCapacitor(context, isLocationEnabled);
    }
  }

  private boolean isLocationEnabled(Context context) {
    LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    return locationManager != null && (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
  }
  private void restartLocationService(Context context) {
    if (!isServiceRunning(context, BackgroundLocationService.class)) {
      Intent serviceIntent = new Intent(context, BackgroundLocationService.class);
      context.startForegroundService(serviceIntent);
    }
  }
  private static boolean isServiceRunning(Context context, Class<?> serviceClass) {
    ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    if (manager != null) {
      for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
        if (serviceClass.getName().equals(service.service.getClassName())) {
          return true;
        }
      }
    }
    return false;
  }

  private static void sendEnabledToCapacitor(Context context, boolean enabled) {
    BackgroundLocationPlugin pluginInstance = BackgroundLocationPlugin.getInstance();
    if (pluginInstance != null) {
      pluginInstance.pushLocationStateToCapacitor(enabled);
    } else {
      Log.e(TAG, "Plugin instance is null, cannot send event to Capacitor.");
    }
  }
}
