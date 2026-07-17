package com.elsapiens.backgroundlocation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.util.Log;

/**
 * Forwards device location-services on/off changes to JavaScript while the app is
 * alive. Registered at runtime by the plugin for PROVIDERS_CHANGED.
 *
 * Service recovery on re-enable is handled by the tracking service itself (it stays
 * alive and resumes) and by {@link ServiceRestartReceiver}; this receiver only reports
 * status so the app can show an "enable location" prompt.
 */
public class LocationStateReceiver extends BroadcastReceiver {
  private static final String TAG = "LocationStateReceiver";
  private static long lastEventTime = 0;
  private static final long EVENT_THRESHOLD_MS = 500;
  private static boolean lastReportedState = false;
  private static boolean hasReported = false;

  @Override
  public void onReceive(Context context, Intent intent) {
    if (intent.getAction() == null || !intent.getAction().equals(LocationManager.PROVIDERS_CHANGED_ACTION)) {
      return;
    }
    long currentTime = System.currentTimeMillis();
    boolean enabled = isLocationEnabled(context);
    // PROVIDERS_CHANGED fires once per provider; debounce duplicates.
    if (hasReported && currentTime - lastEventTime < EVENT_THRESHOLD_MS && enabled == lastReportedState) {
      return;
    }
    lastEventTime = currentTime;
    lastReportedState = enabled;
    hasReported = true;
    sendEnabledToCapacitor(enabled);
  }

  private boolean isLocationEnabled(Context context) {
    LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    return locationManager != null && (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
  }

  private static void sendEnabledToCapacitor(boolean enabled) {
    BackgroundLocationPlugin pluginInstance = BackgroundLocationPlugin.getInstance();
    if (pluginInstance != null) {
      pluginInstance.pushLocationStateToCapacitor(enabled);
    } else {
      Log.e(TAG, "Plugin instance is null, cannot send event to Capacitor.");
    }
  }
}
