package com.elsapiens.backgroundlocation;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * Background service for work hour location tracking
 */
public class WorkHourLocationService extends Service {
    private static final String TAG = "WorkHourLocationService";
    private static final String CHANNEL_ID = "WorkHourLocationChannel";
    private static final int NOTIFICATION_ID = 2001;
    
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private WorkHourLocationUploader uploader;
    
    private String engineerId;
    private long uploadInterval;
    private String serverUrl;
    private String authToken;
    private boolean enableOfflineQueue;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "WorkHourLocationService created");
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        createNotificationChannel();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "WorkHourLocationService started");
        
        if (intent != null) {
            engineerId = intent.getStringExtra("engineerId");
            uploadInterval = intent.getLongExtra("uploadInterval", 300000L); // 5 minutes default
            serverUrl = intent.getStringExtra("serverUrl");
            authToken = intent.getStringExtra("authToken");
            enableOfflineQueue = intent.getBooleanExtra("enableOfflineQueue", true);
            
            // Check permissions before starting foreground service
            if (!hasLocationPermissions()) {
                Log.e(TAG, "Location permissions not granted, stopping service");
                stopSelf();
                return START_NOT_STICKY;
            }
            
            // Start foreground service
            Notification notification = createNotification();
            startForeground(NOTIFICATION_ID, notification);
            
            // Initialize uploader
            BackgroundLocationPlugin plugin = getPluginInstance();
            if (plugin != null) {
                uploader = new WorkHourLocationUploader(
                    engineerId, uploadInterval, serverUrl, authToken, enableOfflineQueue, plugin
                );
            }
            
            // Start location updates
            startLocationUpdates();
        }
        
        return START_STICKY; // Restart if killed
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "WorkHourLocationService destroyed");
        
        stopLocationUpdates();
        
        if (uploader != null) {
            uploader.stop();
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Work Hour Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Tracks location during work hours");
            channel.setShowBadge(false);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    private Notification createNotification() {
        // Create an intent for when the notification is tapped
        Intent notificationIntent = new Intent();
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, 
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Work Hour Tracking")
            .setContentText("Tracking location during work hours for engineer: " + engineerId)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build();
    }
    
    private void startLocationUpdates() {
        LocationRequest locationRequest = new LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            uploadInterval // Use upload interval as location update interval
        )
        .setMinUpdateDistanceMeters(50.0f) // Only update if moved 50 meters
        .setMaxUpdateAgeMillis(uploadInterval)
        .setWaitForAccurateLocation(false)
        .build();
        
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                
                for (Location location : locationResult.getLocations()) {
                    Log.d(TAG, "Work hour location update: " + 
                        location.getLatitude() + ", " + location.getLongitude() + 
                        " accuracy: " + location.getAccuracy());
                    
                    // Add location to uploader queue
                    if (uploader != null) {
                        uploader.addLocationToQueue(location);
                    }
                }
            }
        };
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            Log.d(TAG, "Location updates started for work hour tracking");
        } else {
            Log.e(TAG, "Location permission not granted");
            stopSelf();
        }
    }
    
    private void stopLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            Log.d(TAG, "Location updates stopped");
        }
    }
    
    private boolean hasLocationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }
    
    // Helper method to get plugin instance (this would need to be implemented properly)
    private BackgroundLocationPlugin getPluginInstance() {
        // This is a simplified approach - in a real implementation, you'd need a proper way
        // to get the plugin instance, possibly through a singleton or dependency injection
        return BackgroundLocationPlugin.getCurrentInstance();
    }
}