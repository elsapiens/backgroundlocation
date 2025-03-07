package com.elsapiens.backgroundlocation;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.*;

public class BackgroundLocationService extends Service {
  private static final String CHANNEL_ID = "location_service_channel";
  private FusedLocationProviderClient fusedLocationClient;
  private LocationCallback locationCallback;
  private SQLiteDatabaseHelper db;
  private String reference; // Store reference passed from the plugin
  private int lastIndex = 0; // Track the last index
  private Location lastLocation = null; // Keep track of the last location
  private float totalDistance = 0; // Distance traveled
  private long interval = 3000; // Default to 3000ms
  private float minDistance = 10; // Default to 10 meters
  private boolean highAccuracy = true; // Default to high accuracy

  @Override
  public void onCreate() {

    super.onCreate();
    startForeground(1, createNotification());
    createNotificationChannel();
    db = new SQLiteDatabaseHelper(this);
    fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

    // 🛑 STOP SERVICE IMMEDIATELY IF LOCATION IS DISABLED
    if (!isLocationEnabled()) {
      Log.e("BackgroundLocation", "Location is disabled. Stopping service.");
      sendLocationDisabledBroadcast();
      stopSelf();
      return; // ✅ Prevent further execution
    }

    // 🛑 STOP SERVICE IF PERMISSIONS ARE MISSING
    if (!hasLocationPermissions()) {
      requestPermissionsManually();
      stopSelf();
      return;
    }
    locationCallback = new LocationCallback() {
      @Override
      public void onLocationResult(@NonNull LocationResult locationResult) {
        if (!isLocationEnabled()) {
          sendLocationDisabledBroadcast();
          stopSelf(); // Stop the service if location is disabled
          return;
        }

        for (Location location : locationResult.getLocations()) {
          lastIndex = db.getNextIndexForReference(reference);
          if (lastLocation != null) {
            totalDistance += lastLocation.distanceTo(location);
          }
          if (location.getAccuracy() > 30) {
            continue;
          }
          lastLocation = location;
          db.insertLocation(reference, lastIndex, location.getLatitude(), location.getLongitude(),
              location.getAltitude(), location.getAccuracy(), location.getSpeed(), location.getBearing(),
              location.getVerticalAccuracyMeters(), location.getTime());
          Log.d("BackgroundLocation", "Location update: " + location.getLatitude() + ", " + location.getLongitude());
          sendLocationUpdate(location, lastIndex, totalDistance);
        }
      }
    };
    if (!hasLocationPermissions()) {
      requestPermissionsManually();
      stopSelf(); // Stop service if permissions are missing
      return;
    }
    requestLocationUpdates(interval, minDistance, highAccuracy);
  }

  private void requestLocationUpdates(long interval, float minDistance, boolean highAccuracy) {
    if (!isLocationEnabled()) {
        sendLocationDisabledBroadcast(); // Notify the app about the issue
        return;
    }
    if (!hasLocationPermissions()) {
        requestPermissionsManually();
        return;
    }
    LocationRequest locationRequest = new LocationRequest.Builder(
            highAccuracy ? Priority.PRIORITY_HIGH_ACCURACY : Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            interval)
            .setMinUpdateDistanceMeters(minDistance)
            .build();
    if (ActivityCompat.checkSelfPermission(this,
            Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this,
            Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        return;
    }
    fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
}

  private void checkLocationSettings() {
    LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build();
    LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest);

    SettingsClient settingsClient = LocationServices.getSettingsClient(this);
    settingsClient.checkLocationSettings(builder.build())
        .addOnFailureListener(exception -> {
          if (exception instanceof ResolvableApiException) {
            Log.e("BackgroundLocation", "Location settings are not satisfied.");
            sendLocationDisabledBroadcast();
          }
        });
  }

  private void sendLocationDisabledBroadcast() {
    Intent intent = new Intent("BackgroundLocationDisabled");
    intent.setPackage(getPackageName());
    sendBroadcast(intent);
  }

  private boolean isLocationEnabled() {
    android.location.LocationManager locationManager = (android.location.LocationManager) getSystemService(
        LOCATION_SERVICE);
    return locationManager != null
        && (locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER));
  }

  private void sendLocationUpdate(Location location, int index, float totalDistance) {
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
    intent.putExtra("totalDistance", totalDistance);
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
      if (!hasLocationPermissions()) {
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

      // Get additional parameters for update interval and minimum distance
      interval = intent.getLongExtra("interval", 3000); // Default to 3000ms
      minDistance = intent.getFloatExtra("minDistance", 10); // Default to 10 meters
      highAccuracy = intent.getBooleanExtra("highAccuracy", true); // Default to high accuracy

      requestLocationUpdates(interval, minDistance, highAccuracy);
      return START_STICKY;
  }

  private void requestPermissionsManually() {
    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
    intent.setData(Uri.parse("package:" + getPackageName()));
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(intent);
  }

  private boolean hasLocationPermissions() {
    return ActivityCompat.checkSelfPermission(this,
        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
        ActivityCompat.checkSelfPermission(this,
            Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        &&
        ActivityCompat.checkSelfPermission(this,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    if (fusedLocationClient != null && locationCallback != null) {
      fusedLocationClient.removeLocationUpdates(locationCallback);
    }
  }
}
