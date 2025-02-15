package com.elsapiens.backgroundlocation;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.google.android.gms.location.*;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;

@CapacitorPlugin(name = "BackgroundLocation", permissions = {
        @Permission(alias = "foregroundLocation", strings = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        }),
        @Permission(alias = "foregroundLocationNew", strings = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.FOREGROUND_SERVICE_LOCATION
        }),
        @Permission(alias = "backgroundLocation", strings = {
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
        }),
        @Permission(alias = "callPhone", strings = {
                Manifest.permission.CALL_PHONE
        })
})
public class BackgroundLocationPlugin extends Plugin implements SensorEventListener {
    private static final int FOREGROUND_LOCATION_REQUEST_CODE = 1001;
    private static final int BACKGROUND_LOCATION_REQUEST_CODE = 1002;
    private static final String TAG = "BackgroundLocation";
    private  FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private  SQLiteDatabaseHelper db;
    private String currentReference;
    private Location lastLocation;
    private boolean isMoving = false;
    private boolean isTrackingActive = false; // 🚀 NEW: Track if location tracking is active
    private float lastAcceleration = 0;
    private long lastMovementTime = 0;
    private LocationBroadcastReceiver locationReceiver;
    private static BackgroundLocationPlugin instance;

    public BackgroundLocationPlugin() {
        instance = this; // ✅ Save instance for use in static methods
    }

    public static BackgroundLocationPlugin getInstance() {
        return instance;
    }

    @Override
    public void load() {
        super.load();
        Context context = getContext();
        db = new SQLiteDatabaseHelper(getContext());
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);

        // 🔹 Register Broadcast Receiver
        locationReceiver = new LocationBroadcastReceiver();
        IntentFilter filter = new IntentFilter("BackgroundLocationUpdate");
        ContextCompat.registerReceiver(context, locationReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        Log.d(TAG, "📡 LocationBroadcastReceiver Registered");
    }

    @Override
    protected void handleOnDestroy() {
        super.handleOnDestroy();
        Context context = getContext();
        if (locationReceiver != null) {
            context.unregisterReceiver(locationReceiver);
            Log.d(TAG, "📡 LocationBroadcastReceiver Unregistered");
        }
    }


    @Override
    protected void handleOnResume() {
        super.handleOnResume();
        Log.d(TAG, "App Resumed - Pushing Latest Location " + currentReference);
        if (currentReference != null) {
            pushLatestLocationToAngular(currentReference); // Send latest location to Angular when the app resumes
        }
    }

    private void pushLatestLocationToAngular(String reference) {
        LocationItem location = db.getLastLocation(reference);
        if (location != null) {
            pushUpdateToCapacitor(location);
        }
    }

    @PluginMethod
    public void startTracking(PluginCall call) {

        //check if already tracking
        if (isTrackingActive) {
            stopLocationUpdates();
        }
        if (!call.getData().has("reference")) {
            call.reject("Missing 'reference' parameter.");
            return;
        }
        currentReference = call.getString("reference");
        if (!hasLocationPermissions()) {
            requestLocationPermissions(call);
        }else if (!hasBackgroundLocationPermission()) {
            requestBackgroundPermission(call);
        }else{
            executeStartTracking(call);
        }

    }

    @PermissionCallback
    private void executeStartTracking(PluginCall call) {
        Context context = getContext();
        Intent serviceIntent = new Intent(context, BackgroundLocationService.class);
        serviceIntent.putExtra("reference", currentReference);
        context.startForegroundService(serviceIntent);
        isTrackingActive = true;
        call.resolve();
    }

    @PluginMethod
    public void clearStoredLocations(PluginCall call) {
        try {
            db.clearStoredLocations();
            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to clear stored locations", e);
        }
    }

    @PluginMethod
    public void getLastLocation(PluginCall call) {
        if (!call.getData().has("reference")) {
            call.reject("Missing 'reference' parameter.");
            return;
        }
        String reference = call.getString("reference");
        pushLatestLocationToAngular(reference);
    }

    private void requestLocationUpdate() {
        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                3000).setMinUpdateDistanceMeters(10)
                .setWaitForAccurateLocation(true)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (shouldSaveLocation(location)) {
                        int index = db.getNextIndexForReference(currentReference);
                        saveToDatabase(location, currentReference, index);
                        pushUpdateToCapacitor(location, index);
                    }
                }
            }
        };

        if (ActivityCompat.checkSelfPermission(getContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    private boolean shouldSaveLocation(Location newLocation) {
        if (lastLocation == null)
            return true;
        float distance = lastLocation.distanceTo(newLocation);

        // Save if moved at least 10 meters or turned significantly
        return distance >= 10 || hasSignificantTurn(lastLocation, newLocation);
    }

    private boolean hasSignificantTurn(Location oldLocation, Location newLocation) {
        float bearingChange = Math.abs(oldLocation.getBearing() - newLocation.getBearing());
        return bearingChange > 30;
    }

    public void saveToDatabase(Location location, String currentReference, int index) {
        db.insertLocation(currentReference, index, location.getLatitude(), location.getLongitude(), (float) location.getAltitude(),
                location.getAccuracy(), location.getSpeed(), location.getBearing(), location.getVerticalAccuracyMeters(), location.getTime());
        lastLocation = location;
        Log.d(TAG, "Location Saved: " + location.getLatitude() + ", " + location.getLongitude() + " Accuracy: "
                + location.getAccuracy());
    }

    private void pushUpdateToCapacitor(Location location, int index) {
        JSObject data = new JSObject();
        lastLocation = location;
        data.put("reference", currentReference);
        data.put("index", index);
        data.put("latitude", location.getLatitude());
        data.put("longitude", location.getLongitude());
        data.put("altitude", location.getAltitude());
        data.put("accuracy", location.getAccuracy());
        data.put("speed", location.getSpeed());
        data.put("heading", location.getBearing());
        data.put("altitudeAccuracy", location.getVerticalAccuracyMeters());
        data.put("totalDistance", db.getTotalDistanceForReference(currentReference));
        data.put("timestamp", location.getTime());
        notifyListeners("locationUpdate", data);
    }

    public void pushUpdateToCapacitor(LocationItem location) {
        JSObject data = new JSObject();
        data.put("reference", location.reference);
        data.put("index", location.index);
        data.put("latitude", location.latitude);
        data.put("longitude", location.longitude);
        data.put("altitude", location.altitude);
        data.put("accuracy", location.accuracy);
        data.put("speed", location.speed);
        data.put("heading", location.heading);
        data.put("altitudeAccuracy", location.altitudeAccuracy);
        data.put("totalDistance", db.getTotalDistanceForReference(location.reference));
        data.put("timestamp", location.timestamp);
        notifyListeners("locationUpdate", data);
    }

    @PluginMethod
    public void stopTracking(PluginCall call) {
        stopLocationUpdates();
        call.resolve();
    }

    private void stopLocationUpdates() {

        Context context = getContext();
        Intent serviceIntent = new Intent(context, BackgroundLocationService.class);
        context.stopService(serviceIntent);

        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        isTrackingActive = false; // 🚀 Mark tracking as inactive
        Log.d(TAG, "Location tracking stopped.");
    }

    @PluginMethod
    public void getStoredLocations(PluginCall call) {
        if (!call.getData().has("reference")) {
            call.reject("Missing 'reference' parameter.");
            return;
        }
        String reference = call.getString("reference");
        JSObject result = new JSObject();
        result.put("locations", db.getLocationsForReference(reference));
        call.resolve(result);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            float acceleration = (float) Math.sqrt(x * x + y * y + z * z);
            long currentTime = System.currentTimeMillis();

            if (Math.abs(acceleration - lastAcceleration) > 0.5) {
                isMoving = true;
                lastMovementTime = currentTime;

                // 🚀 If movement is detected and tracking is inactive, restart location updates
                if (!isTrackingActive) {
                    Log.d(TAG, "User started moving. Restarting location tracking.");
                    requestLocationUpdate();
                    isTrackingActive = true;
                }
            } else {
                // 🚀 If no movement for 5+ seconds, stop location updates
                if (isMoving && (currentTime - lastMovementTime > 5000)) {
                    Log.d(TAG, "User stopped moving. Stopping location tracking.");
                    stopLocationUpdates();
                    isMoving = false;
                }
            }

            lastAcceleration = acceleration;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private boolean hasLocationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(getContext(), Manifest.permission.FOREGROUND_SERVICE_LOCATION) == PackageManager.PERMISSION_GRANTED ;
        }else{
            return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }
    private boolean hasBackgroundLocationPermission() {
        return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    @PermissionCallback
    public void requestLocationPermissions(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            requestPermissionForAlias("foregroundLocationNew", call, "requestBackgroundPermission");
        }else {
            requestPermissionForAlias("foregroundLocation", call, "requestBackgroundPermission");
        }
    }
    @PermissionCallback
    private void requestBackgroundPermission(PluginCall call) {
        requestPermissionForAlias("backgroundLocation", call, "executeStartTracking");
    }

    // Show instructions if background location is denied
    private void showManualBackgroundLocationInstructions() {
        new AlertDialog.Builder(getActivity())
                .setTitle("Background Location Required")
                .setMessage("To enable background location, go to:\nSettings > Apps > Your App > Permissions > Location > Allow All the Time.")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + getActivity().getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getActivity().startActivity(intent);
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }
}
