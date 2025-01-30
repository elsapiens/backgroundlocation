package com.elsapiens.backgroundlocation;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.android.gms.location.*;
import com.google.android.gms.location.LocationCallback;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

@CapacitorPlugin(name = "BackgroundLocation", permissions = {
    @com.getcapacitor.annotation.Permission(
        alias = "location",
        strings = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION}
    )
})
public class BackgroundLocationPlugin extends Plugin implements SensorEventListener {
    private static final String TAG = "BackgroundLocation";
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private SQLiteDatabaseHelper db;
    private String currentReference;
    private Location lastLocation;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private boolean isMoving = false;
    private float lastAcceleration = 0;
    private long lastGpsUpdateTime = 0;

    @PluginMethod
    public void startTracking(PluginCall call) {
        if (!call.hasOption("reference")) {
            call.reject("Missing 'reference' parameter.");
            return;
        }
        currentReference = call.getString("reference");

        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(call);
            return;
        }

        Context context = getContext();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        db = new SQLiteDatabaseHelper(context);

        // Initialize accelerometer to detect motion
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }

        requestLocationUpdate(); // Start tracking immediately
        call.resolve();
    }

    private void requestLocationUpdate() {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(3000); // Request location every 3 seconds
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult != null) {
                    for (Location location : locationResult.getLocations()) {
                        if (shouldSaveLocation(location)) {
                            saveToDatabase(location);
                            pushUpdateToAngular(location);  // 🔥 Send live update to Angular
                        }
                    }
                }
            }
        };

        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    private boolean shouldSaveLocation(Location newLocation) {
        if (lastLocation == null) return true;
        float distance = lastLocation.distanceTo(newLocation);

        // Save if moved at least 10 meters or turned significantly
        return distance >= 10 || hasSignificantTurn(lastLocation, newLocation);
    }

    private boolean hasSignificantTurn(Location oldLocation, Location newLocation) {
        float bearingChange = Math.abs(oldLocation.getBearing() - newLocation.getBearing());
        return bearingChange > 30; // Consider turns greater than 30 degrees
    }

    private void saveToDatabase(Location location) {
        int index = db.getNextIndexForReference(currentReference);
        db.insertLocation(currentReference, index, location.getLatitude(), location.getLongitude(), location.getTime(), location.getAccuracy());
        lastLocation = location;
        lastGpsUpdateTime = System.currentTimeMillis();
        Log.d(TAG, "Location Saved: " + location.getLatitude() + ", " + location.getLongitude() + " Accuracy: " + location.getAccuracy());
    }

    private void pushUpdateToAngular(Location location) {
        JSObject data = new JSObject();
        data.put("latitude", location.getLatitude());
        data.put("longitude", location.getLongitude());
        data.put("accuracy", location.getAccuracy()); // Include accuracy
        data.put("timestamp", location.getTime());
        notifyListeners("locationUpdate", data);  // 🔥 Push update to Angular
    }

    @PluginMethod
    public void stopTracking(PluginCall call) {
        if (fusedLocationClient != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        call.resolve();
    }

    @PluginMethod
    public void getStoredLocations(PluginCall call) {
        if (!call.hasOption("reference")) {
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
                lastGpsUpdateTime = currentTime;
            } else {
                // If no GPS update in the last 5 seconds, assume stopped
                if (currentTime - lastGpsUpdateTime > 5000) {
                    isMoving = false;
                }
            }
            lastAcceleration = acceleration;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}