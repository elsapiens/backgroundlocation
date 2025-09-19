package com.elsapiens.backgroundlocation;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import java.util.List;

/**
 * BroadcastReceiver that handles service restart mechanism
 * This receiver is triggered periodically to check if the BackgroundLocationService is running
 * and restart it if necessary.
 */
public class ServiceRestartReceiver extends BroadcastReceiver {
    private static final String TAG = "ServiceRestartReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if ("com.elsapiens.backgroundlocation.RESTART_SERVICE".equals(intent.getAction())) {
            Log.d(TAG, "Service restart check triggered");
            
            // Check if the BackgroundLocationService is running
            if (!isServiceRunning(context, BackgroundLocationService.class)) {
                Log.i(TAG, "BackgroundLocationService is not running, attempting to restart");
                
                try {
                    // Restart the service
                    Intent serviceIntent = new Intent(context, BackgroundLocationService.class);
                    serviceIntent.putExtra("reference", "auto_restart");
                    serviceIntent.putExtra("interval", 3000L);
                    serviceIntent.putExtra("minDistance", 10.0f);
                    serviceIntent.putExtra("highAccuracy", true);
                    
                    context.startForegroundService(serviceIntent);
                    Log.i(TAG, "BackgroundLocationService restart initiated");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to restart BackgroundLocationService", e);
                }
            } else {
                Log.d(TAG, "BackgroundLocationService is already running");
            }
        }
    }
    
    /**
     * Check if a specific service is currently running
     * 
     * @param context The application context
     * @param serviceClass The service class to check
     * @return true if the service is running, false otherwise
     */
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