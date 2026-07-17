package com.elsapiens.backgroundlocation;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import java.util.List;

/**
 * Periodic watchdog (fired by AlarmManager) that restarts the tracking service if the
 * system killed it while a session is still wanted.
 *
 * Restart is attempted only when the persisted state says tracking is active AND the
 * permissions required for a background start are present. Blindly restarting used to
 * spin up a service that immediately stopped itself without startForeground(), which
 * crashed the app every 30 minutes.
 */
public class ServiceRestartReceiver extends BroadcastReceiver {
    private static final String TAG = "ServiceRestartReceiver";

    public static final String ACTION_RESTART = "com.elsapiens.backgroundlocation.RESTART_SERVICE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_RESTART.equals(intent.getAction())) {
            return;
        }

        TrackingStateStore stateStore = new TrackingStateStore(new SharedPrefsKeyValueStore(context));
        if (!stateStore.isTaskTrackingActive()) {
            Log.d(TAG, "No active tracking session; skipping restart");
            return;
        }

        LocationPermissionManager permissions = new LocationPermissionManager(context);
        if (!permissions.hasForegroundLocationPermission()) {
            Log.w(TAG, "Location permission missing; cannot restart tracking service");
            return;
        }
        if (!permissions.hasBackgroundLocationPermission()) {
            // A foreground-service-location start from the background requires
            // "Allow all the time"; without it the start would throw.
            Log.w(TAG, "Background location permission missing; cannot restart from background");
            return;
        }

        if (isServiceRunning(context, BackgroundLocationService.class)) {
            Log.d(TAG, "BackgroundLocationService is already running");
            return;
        }

        try {
            // Parameters are restored by the service from the state store.
            Intent serviceIntent = new Intent(context, BackgroundLocationService.class);
            context.startForegroundService(serviceIntent);
            Log.i(TAG, "BackgroundLocationService restart initiated");
        } catch (Exception e) {
            Log.e(TAG, "Failed to restart BackgroundLocationService", e);
        }
    }

    private boolean isServiceRunning(Context context, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            List<ActivityManager.RunningServiceInfo> runningServices = manager.getRunningServices(Integer.MAX_VALUE);
            for (ActivityManager.RunningServiceInfo service : runningServices) {
                if (serviceClass.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }
}
