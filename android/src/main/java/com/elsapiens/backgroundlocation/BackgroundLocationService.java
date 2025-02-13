package com.elsapiens.backgroundlocation;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import com.google.android.gms.location.*;

public class BackgroundLocationService extends Service {
    private static final String CHANNEL_ID = "location_service_channel";
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private SQLiteDatabaseHelper db;
    private String reference; // Store reference passed from the plugin
    private int lastIndex = 0; // Track the last index
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        db = new SQLiteDatabaseHelper(this);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    lastIndex = db.getNextIndexForReference(reference); // Update the last index
                    db.insertLocation(reference, lastIndex, location.getLatitude(), location.getLongitude(), location.getAltitude(), location.getAccuracy(), location.getSpeed(), location.getBearing(), location.getVerticalAccuracyMeters(), location.getTime());
                    Log.d("BackgroundLocation", "Location update: " + location.getLatitude() + ", " + location.getLongitude());
                    sendLocationUpdate(location, lastIndex);
                }
            }
        };
        if (hasLocationPermissions()) {
            requestPermissionsManually();
            stopSelf(); // Stop service if permissions are missing
            return;
        }
        startForeground(1, createNotification());
        requestLocationUpdates();
    }
    private void requestLocationUpdates() {
        if (hasLocationPermissions()) {
            requestPermissionsManually();
            return;
        }
        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                5000).setMinUpdateDistanceMeters(10).build();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }
    private void sendLocationUpdate(Location location, int index) {
        Intent intent = new Intent("BackgroundLocationUpdate");
        intent.putExtra("reference", reference);
        intent.putExtra("index", index);
        intent.putExtra("latitude", location.getLatitude());
        intent.putExtra("longitude", location.getLongitude());
        intent.putExtra("altitude", location.getAltitude());
        intent.putExtra("speed", location.getSpeed());
        intent.putExtra("heading", location.getBearing());
        intent.putExtra("accuracy", location.getAccuracy());
        intent.putExtra("altitudeAccuracy", location.getVerticalAccuracyMeters());
        intent.putExtra("timestamp", location.getTime());
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }
    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Location Tracking Active")
                .setContentText("Your location is being tracked in the background")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID, "Location Tracking", NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(serviceChannel);
        }
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (hasLocationPermissions()) {
            requestPermissionsManually();
            stopSelf(); // Stop service if permissions are missing
            return START_NOT_STICKY;
        }
        if (intent != null && intent.hasExtra("reference")) {
            reference = intent.getStringExtra("reference");
            if (reference == null || reference.trim().isEmpty()) {
                reference = "default_reference"; // Set a fallback reference to prevent null issues
            }
        } else {
            reference = "default_reference"; // Another fallback to avoid null reference
        }
        lastIndex = db.getNextIndexForReference(reference); // Resume index tracking safely
        return START_STICKY;
    }
    private void requestPermissionsManually() {
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
    private boolean hasLocationPermissions() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED;
    }
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }
}